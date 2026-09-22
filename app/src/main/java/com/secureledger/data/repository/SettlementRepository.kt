package com.secureledger.data.repository

import com.secureledger.core.crypto.AesGcmManager
import com.secureledger.core.crypto.Ed25519Manager
import com.secureledger.core.crypto.KeyManager
import com.secureledger.core.crypto.encodeBase64
import com.secureledger.core.crypto.decodeBase64
import com.secureledger.core.merkle.MerkleTree
import com.secureledger.core.protocol.SettlementExchangeCodec
import com.secureledger.core.settlement.Balance
import com.secureledger.core.settlement.SettlementCalculator
import com.secureledger.core.settlement.SettlementProof
import com.secureledger.core.settlement.SettlementSignature
import com.secureledger.data.dao.SettlementDao
import com.secureledger.data.database.AppDatabase
import com.secureledger.data.entity.SettlementEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.util.UUID

/** Creates, project-key encrypts, and independently co-signs durable AA proofs. */
class SettlementRepository(
    private val database: AppDatabase,
    private val projectKeys: ProjectKeyRepository
) {
    private val dao: SettlementDao = database.settlementDao()
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }

    /** Calculates the minimum-transfer plan and signs it with the current user's Ed25519 key. */
    suspend fun createAndSave(
        identity: KeyManager.UserIdentity,
        projectId: String,
        ledgerMerkleRoot: String,
        balances: List<Balance>
    ): SettlementProof {
        val settlementId = "s_${UUID.randomUUID()}"
        val unsigned = SettlementCalculator.createProof(
            projectId = projectId,
            settlementId = settlementId,
            merkleRoot = ledgerMerkleRoot,
            balances = balances,
            createdAt = System.currentTimeMillis()
        )
        val signature = Ed25519Manager.sign(identity.privateSeed, unsigned.hash.toByteArray(Charsets.UTF_8))
        val proof = unsigned.copy(
            signatures = listOf(
                SettlementSignature(
                    signerId = identity.userId,
                    publicKey = identity.publicKey.encodeBase64(),
                    signature = signature.encodeBase64()
                )
            )
        )
        require(proof.verifyHash() && proof.verifySignatures()) { "Generated settlement proof failed verification" }
        validateMemberSignatures(proof)
        val plaintext = json.encodeToString(SettlementProof.serializer(), proof).toByteArray(Charsets.UTF_8)
        val key = projectKeys.keyFor(identity, projectId) ?: error("项目密钥不可用")
        val encrypted = AesGcmManager.encrypt(key, plaintext, aad(proof.projectId, proof.settlementId))
        dao.insert(
            SettlementEntity(
                settlementId = proof.settlementId,
                projectId = proof.projectId,
                createdAt = proof.createdAt,
                proofHash = proof.hash,
                nonce = encrypted.nonce.encodeBase64(),
                encryptedProof = encrypted.bytes.encodeBase64()
            )
        )
        return proof
    }

    fun observe(projectId: String, identity: KeyManager.UserIdentity): Flow<List<SettlementProof>> =
        dao.observeByProject(projectId).combine(projectKeys.observeKeys(identity)) { entities, keys ->
            entities.mapNotNull { decrypt(it, identity, keys[it.projectId] ?: identity.masterKey) }
        }

    suspend fun latest(projectId: String, identity: KeyManager.UserIdentity): SettlementProof? =
        dao.latestByProject(projectId)?.let { entity ->
            decrypt(entity, identity, projectKeys.keyFor(identity, entity.projectId) ?: identity.masterKey)
        }

    /** Validates local project state, then adds this member's independent signature. */
    suspend fun coSign(identity: KeyManager.UserIdentity, encoded: String): String {
        val proof = SettlementExchangeCodec.decode(encoded)
        val member = database.projectDao().getMember(proof.projectId, identity.userId)
            ?: error("当前用户不是该项目成员")
        require(member.publicKey == identity.publicKey.encodeBase64()) { "成员签名公钥不匹配" }
        require(identity.userId in proof.participants) { "当前用户不是该结算的相关方" }
        validateMemberSignatures(proof)
        val localRecords = database.ledgerRecordDao().getAll().map { it.toDomain() }.filter { it.projectId == proof.projectId }
        require(MerkleTree(localRecords).calculateRoot() == proof.ledgerMerkleRoot) { "本地项目账本与结算凭证不一致" }
        val signature = SettlementSignature(
            identity.userId,
            identity.publicKey.encodeBase64(),
            Ed25519Manager.sign(identity.privateSeed, proof.hash.toByteArray(Charsets.UTF_8)).encodeBase64()
        )
        return SettlementExchangeCodec.encode(
            proof.copy(signatures = (proof.signatures.filterNot { it.signerId == identity.userId } + signature)
                .sortedBy(SettlementSignature::signerId))
        )
    }

    /** Merges only valid project-member signatures into the locally stored proof. */
    suspend fun mergeSignatures(identity: KeyManager.UserIdentity, encoded: String): SettlementProof {
        val incoming = SettlementExchangeCodec.decode(encoded)
        validateMemberSignatures(incoming)
        val entity = dao.getById(incoming.settlementId) ?: error("本机没有对应的结算凭证")
        val projectKey = projectKeys.keyFor(identity, entity.projectId) ?: identity.masterKey
        val local = decrypt(entity, identity, projectKey) ?: error("本机结算凭证无法解密或验证")
        require(local.hash == incoming.hash && local.canonicalBytes().contentEquals(incoming.canonicalBytes())) {
            "会签包与本机结算内容不一致"
        }
        val merged = local.copy(signatures = (local.signatures + incoming.signatures)
            .associateBy(SettlementSignature::signerId).values.sortedBy(SettlementSignature::signerId))
        validateMemberSignatures(merged)
        val key = projectKeys.keyFor(identity, merged.projectId) ?: error("项目密钥不可用")
        val encrypted = AesGcmManager.encrypt(
            key,
            json.encodeToString(SettlementProof.serializer(), merged).toByteArray(Charsets.UTF_8),
            aad(merged.projectId, merged.settlementId)
        )
        check(dao.updateSignatures(merged.settlementId, merged.hash, encrypted.nonce.encodeBase64(), encrypted.bytes.encodeBase64()) == 1) {
            "结算凭证更新冲突"
        }
        return merged
    }

    fun encodeForApproval(proof: SettlementProof): String = SettlementExchangeCodec.encode(proof)

    private suspend fun validateMemberSignatures(proof: SettlementProof) {
        require(proof.signatures.map(SettlementSignature::signerId).distinct().size == proof.signatures.size) { "存在重复签名者" }
        require(proof.verifyHash() && proof.verifySignatures()) { "结算凭证签名无效" }
        proof.signatures.forEach { signature ->
            val member = database.projectDao().getMember(proof.projectId, signature.signerId)
                ?: error("签名者不是项目成员：${signature.signerId}")
            require(member.publicKey == signature.publicKey) { "签名者公钥与成员登记不一致" }
        }
    }

    private fun decrypt(entity: SettlementEntity, identity: KeyManager.UserIdentity, projectKey: ByteArray): SettlementProof? = runCatching {
        val ciphertext = AesGcmManager.Ciphertext(entity.nonce.decodeBase64(), entity.encryptedProof.decodeBase64())
        val bytes = runCatching { AesGcmManager.decrypt(projectKey, ciphertext, aad(entity.projectId, entity.settlementId)) }
            .getOrElse {
                // Pre-v6 settlements were encrypted with the vault master key.
                AesGcmManager.decrypt(identity.masterKey, ciphertext, aad(entity.projectId, entity.settlementId))
            }
        json.decodeFromString(SettlementProof.serializer(), bytes.toString(Charsets.UTF_8))
            .also { proof ->
                check(proof.settlementId == entity.settlementId)
                check(proof.projectId == entity.projectId)
                check(proof.hash == entity.proofHash)
                check(proof.verifyHash())
                check(proof.verifySignatures())
            }
    }.getOrNull()

    private fun aad(projectId: String, settlementId: String): ByteArray =
        "settlement|$projectId|$settlementId|1".toByteArray(Charsets.UTF_8)
}
