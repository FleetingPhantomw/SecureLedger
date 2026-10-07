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
import com.secureledger.core.settlement.SplitPlan
import com.secureledger.core.settlement.SelectedBillSplit
import com.secureledger.core.settlement.SplitBill
import com.secureledger.data.dao.SettlementDao
import com.secureledger.data.database.AppDatabase
import com.secureledger.data.entity.SettlementEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.util.UUID
import androidx.room.withTransaction

/** Creates, project-key encrypts, and independently co-signs durable AA proofs. */
class SettlementRepository(
    private val database: AppDatabase,
    private val projectKeys: ProjectKeyRepository
) {
    private val dao: SettlementDao = database.settlementDao()
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }

    /** Calculates a deterministic transfer plan and signs it with the current user's Ed25519 key. */
    suspend fun createAndSave(
        identity: KeyManager.UserIdentity,
        projectId: String,
        ledgerMerkleRoot: String,
        balances: List<Balance>,
        splitPlan: SplitPlan? = null
    ): SettlementProof {
        val settlementId = "s_${UUID.randomUUID()}"
        val base = SettlementCalculator.createProof(
            projectId = projectId,
            settlementId = settlementId,
            merkleRoot = ledgerMerkleRoot,
            balances = balances,
            createdAt = System.currentTimeMillis()
        )
        val extended = base.copy(selectedRecordIds = splitPlan?.recordIds.orEmpty(), allocations = splitPlan?.allocations.orEmpty())
        val unsigned = extended.copy(hash = com.secureledger.core.crypto.HashManager.sha256Hex(extended.canonicalBytes()))
        validateSelection(identity, unsigned)
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
        validateSelection(identity, proof)
        val signature = SettlementSignature(
            identity.userId,
            identity.publicKey.encodeBase64(),
            Ed25519Manager.sign(identity.privateSeed, proof.hash.toByteArray(Charsets.UTF_8)).encodeBase64()
        )
        val signed = proof.copy(signatures = (proof.signatures.filterNot { it.signerId == identity.userId } + signature)
            .sortedBy(SettlementSignature::signerId))
        storeReceivedProof(identity, signed)
        return SettlementExchangeCodec.encode(signed)
    }

    /** Recipients retain their independently verified approval as well as returning it to the creator. */
    private suspend fun storeReceivedProof(identity: KeyManager.UserIdentity, proof: SettlementProof) = database.withTransaction {
        val key = projectKeys.keyFor(identity, proof.projectId) ?: error("项目密钥不可用")
        val existing = dao.getById(proof.settlementId)
        val local = existing?.let { decrypt(it, identity, key) ?: error("现有结算凭证无法验证") }
        require(local == null || local.canonicalBytes().contentEquals(proof.canonicalBytes())) { "结算内容冲突" }
        val merged = proof.copy(signatures = (local?.signatures.orEmpty() + proof.signatures)
            .associateBy { it.signerId }.values.sortedBy { it.signerId })
        val encrypted = AesGcmManager.encrypt(key, json.encodeToString(SettlementProof.serializer(), merged).toByteArray(), aad(proof.projectId, proof.settlementId))
        if (existing == null) dao.insert(SettlementEntity(proof.settlementId, proof.projectId, proof.createdAt, proof.hash,
            encrypted.nonce.encodeBase64(), encrypted.bytes.encodeBase64()))
        else check(dao.updateSignatures(proof.settlementId, proof.hash, encrypted.nonce.encodeBase64(), encrypted.bytes.encodeBase64()) == 1)
    }

    /** Merges only valid project-member signatures into the locally stored proof. */
    suspend fun mergeSignatures(identity: KeyManager.UserIdentity, encoded: String): SettlementProof {
        val incoming = SettlementExchangeCodec.decode(encoded)
        validateMemberSignatures(incoming)
        validateSelection(identity, incoming)
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

    /** Recompute selected expenses before signing; caller-supplied allocations cannot be trusted. */
    private suspend fun validateSelection(identity: KeyManager.UserIdentity, proof: SettlementProof) {
        if (proof.selectedRecordIds.isEmpty() && proof.allocations.isEmpty()) return
        require(proof.participants.distinct().size == proof.participants.size) { "结算成员重复" }
        proof.participants.forEach { require(database.projectDao().getMember(proof.projectId, it) != null) { "结算包含未登记成员" } }
        val records = LedgerRepository(database.ledgerRecordDao(), projectKeys = projectKeys).observeRecords(identity).first()
            .filter { it.secure.projectId == proof.projectId && it.secure.recordId in proof.selectedRecordIds }
        require(records.size == proof.selectedRecordIds.size && records.all { it.payload.recordType == "EXPENSE" }) { "选中支出不存在或重复" }
        val calculated = SelectedBillSplit.calculate(records.map { SplitBill(it.secure.recordId, it.secure.authorId, it.payload.amountCents) }, proof.participants)
        require(calculated.allocations == proof.allocations.sortedBy { it.participantId } &&
            calculated.balances == proof.balances.sortedBy { it.participantId } &&
            SettlementCalculator.calculate(calculated.balances) == proof.transfers) { "分摊金额与所选账单不一致" }
    }

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
