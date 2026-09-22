package com.secureledger.data.repository

import com.secureledger.core.crypto.AesGcmManager
import com.secureledger.core.crypto.HashManager
import com.secureledger.core.crypto.KeyManager
import com.secureledger.core.crypto.decodeBase64
import com.secureledger.core.crypto.encodeBase64
import com.secureledger.core.ledger.CanonicalRecord
import com.secureledger.core.ledger.CorrectionProtocol
import com.secureledger.core.ledger.CorrectionRequest
import com.secureledger.core.ledger.IntegrityReport
import com.secureledger.core.ledger.IntegrityVerifier
import com.secureledger.core.ledger.LedgerHashChain
import com.secureledger.core.ledger.PlainRecordPayload
import com.secureledger.core.ledger.SecureRecord
import com.secureledger.core.merkle.MerkleTree
import com.secureledger.data.dao.LedgerRecordDao
import com.secureledger.data.entity.LedgerRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/** Application boundary that atomically creates signed immutable records. */
class LedgerRepository(
    private val dao: LedgerRecordDao,
    private val keyManager: KeyManager,
    private val projectKeys: ProjectKeyRepository? = null
) {
    @Serializable
    private data class PayloadEnvelope(val payload: PlainRecordPayload)

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }
    private val verifier = IntegrityVerifier()

    fun observeRecords(identity: KeyManager.UserIdentity): Flow<List<DecryptedRecord>> {
        val keyedEntities = projectKeys?.let { keys ->
            dao.observeAll().combine(keys.observeKeys(identity)) { entities, keyMap -> entities to keyMap }
        } ?: dao.observeAll().map { it to mapOf(ProjectRepository.PERSONAL_PROJECT_ID to identity.masterKey) }
        return keyedEntities.map { (entities, keyMap) ->
        val all = entities.mapNotNull { entity ->
            // Pre-v6 multi-user records used the vault key. They remain readable/auditable,
            // but no new invitation can export that key as a project key.
            decrypt(entity.toDomain(), keyMap[entity.projectId] ?: identity.masterKey)
        }
        val corrections = all.filter { it.payload.recordType == "CORRECTION" }
            .groupBy { it.payload.correctionTargetRecordId }
        all.filterNot { it.payload.recordType == "CORRECTION" }.map { record ->
            val history = corrections[record.secure.recordId].orEmpty().sortedBy { it.secure.createdAt }
            val latest = history.lastOrNull()
            val correctedCents = latest?.payload?.correctionNewValue?.let(::parseAmountCents)
            if (latest != null && correctedCents != null) {
                record.copy(payload = record.payload.copy(
                    amountCents = correctedCents,
                    note = "${record.payload.note}（已更正：${latest.payload.correctionReason ?: "未说明"}）"
                ), correctionHistory = history.map { it.toCorrectionAudit() })
            } else record.copy(correctionHistory = history.map { it.toCorrectionAudit() })
        }
        }
    }

    suspend fun addRecord(
        identity: KeyManager.UserIdentity,
        payload: PlainRecordPayload,
        projectId: String = DEFAULT_PROJECT
    ): SecureRecord {
        require(payload.amountCents >= 0) { "金额不能为负数" }
        require(payload.payments.all { it.amountCents >= 0 }) { "支付明细金额不能为负数" }
        require(payload.payments.isEmpty() || payload.payments.sumOf { it.amountCents } == payload.amountCents) {
            "支付明细合计必须等于交易金额"
        }
        val encryptionKey = projectKeys?.keyFor(identity, projectId)
            ?: identity.masterKey.takeIf { projectId == DEFAULT_PROJECT }
            ?: error("当前项目暂时无法打开")
        val previous = dao.getLastByAuthorAndProject(identity.userId, projectId)?.toDomain()
        val sequence = (previous?.authorSequence ?: 0) + 1
        val recordId = "r_" + UUID.randomUUID().toString()
        val createdAt = System.currentTimeMillis()
        val payloadBytes = json.encodeToString(PayloadEnvelope.serializer(), PayloadEnvelope(payload)).toByteArray(Charsets.UTF_8)
        val provisional = SecureRecord(
            recordId = recordId,
            projectId = projectId,
            authorId = identity.userId,
            authorSequence = sequence,
            recordType = payload.recordType,
            transactionTime = payload.transactionTime,
            createdAt = createdAt,
            schemaVersion = payload.schemaVersion,
            encryptedPayload = "",
            nonce = "",
            payloadHash = "",
            previousHash = previous?.recordHash ?: LedgerHashChain.GENESIS_HASH,
            recordHash = "",
            signature = "",
            authorPublicKey = identity.publicKey.encodeBase64()
        )
        val aad = aad(provisional)
        val encrypted = AesGcmManager.encrypt(encryptionKey, payloadBytes, aad)
        val recordWithCipher = provisional.copy(
            encryptedPayload = encrypted.bytes.encodeBase64(),
            nonce = encrypted.nonce.encodeBase64(),
            payloadHash = HashManager.sha256Hex(encrypted.bytes)
        )
        val recordWithHash = recordWithCipher.copy(recordHash = SecureRecord.calculateHash(recordWithCipher))
        val signed = recordWithHash.copy(
            signature = identity.privateSeed.let { seed ->
                com.secureledger.core.crypto.Ed25519Manager.sign(seed, CanonicalRecord.signedRecord(recordWithHash)).encodeBase64()
            }
        )
        dao.insert(LedgerRecordEntity.fromDomain(signed))
        return signed
    }

    suspend fun verify(identity: KeyManager.UserIdentity): IntegrityReport {
        val records = dao.getAll().map(LedgerRecordEntity::toDomain)
        val keys = records.map { it.projectId }.distinct().associateWith { projectId ->
            projectKeys?.keyFor(identity, projectId) ?: identity.masterKey
        }
        return verifier.verifyWithKeys(records) { keys[it] }
    }

    /** Calculates a deterministic root for exactly one project after normal record ordering. */
    suspend fun projectMerkleRoot(projectId: String): String = MerkleTree(
        dao.getAll().map(LedgerRecordEntity::toDomain).filter { it.projectId == projectId }
    ).calculateRoot()

    /** Returns a point-in-time decrypted view for local matching and export workflows. */
    suspend fun getDecryptedRecords(identity: KeyManager.UserIdentity): List<DecryptedRecord> =
        dao.getAll().mapNotNull { entity ->
            val key = projectKeys?.keyFor(identity, entity.projectId) ?: identity.masterKey
            decrypt(entity.toDomain(), key)
        }

    /** Appends a signed correction record while leaving the target immutable. */
    suspend fun addCorrection(identity: KeyManager.UserIdentity, request: CorrectionRequest): SecureRecord {
        val target = dao.getById(request.targetRecordId) ?: error("原始记录不存在")
        require(target.recordType != "CORRECTION") { "不能更正另一条更正记录" }
        require(target.authorId == identity.userId) { "多人账本中只有原记录作者可以提交更正" }
        val key = projectKeys?.keyFor(identity, target.projectId) ?: identity.masterKey
        val projectRecords = dao.getAll().mapNotNull { entity ->
            entity.takeIf { it.projectId == target.projectId }?.toDomain()?.let { decrypt(it, key) }
        }
        val original = projectRecords.firstOrNull { it.secure.recordId == target.recordId }
            ?: error("目标记录无法解密")
        val latest = projectRecords.filter { it.payload.correctionTargetRecordId == target.recordId }
            .maxByOrNull { it.secure.createdAt }
        val currentCents = latest?.payload?.correctionNewValue?.let(::parseAmountCents) ?: original.payload.amountCents
        require(parseAmountCents(request.oldValue) == currentCents) { "更正前金额与当前有效值不一致" }
        require((parseAmountCents(request.newValue) ?: -1) >= 0) { "更正后金额无效" }
        return addRecord(identity, CorrectionProtocol.toPayload(request), target.projectId)
    }

    private fun decrypt(record: SecureRecord, masterKey: ByteArray): DecryptedRecord? = runCatching {
        val jsonText = AesGcmManager.decrypt(
            masterKey,
            AesGcmManager.Ciphertext(record.nonce.decodeBase64(), record.encryptedPayload.decodeBase64()),
            aad(record)
        ).toString(Charsets.UTF_8)
        val payload = json.decodeFromString(PayloadEnvelope.serializer(), jsonText).payload
        DecryptedRecord(record, payload)
    }.getOrNull()

    private fun aad(record: SecureRecord): ByteArray =
        listOf(record.recordId, record.authorId, record.projectId, record.recordType, record.schemaVersion)
            .joinToString("|").toByteArray(Charsets.UTF_8)

    private fun parseAmountCents(value: String): Long? = runCatching {
        (value.toBigDecimal() * java.math.BigDecimal(100)).longValueExact()
    }.getOrNull()

    companion object { const val DEFAULT_PROJECT = "personal" }
}

data class CorrectionAudit(
    val recordId: String,
    val oldValue: String,
    val newValue: String,
    val reason: String,
    val createdAt: Long
)

data class DecryptedRecord(
    val secure: SecureRecord,
    val payload: PlainRecordPayload,
    val correctionHistory: List<CorrectionAudit> = emptyList()
)

private fun DecryptedRecord.toCorrectionAudit() = CorrectionAudit(
    secure.recordId,
    payload.correctionOldValue.orEmpty(),
    payload.correctionNewValue.orEmpty(),
    payload.correctionReason.orEmpty(),
    secure.createdAt
)
