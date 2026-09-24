package com.secureledger.data.repository

import androidx.room.withTransaction
import com.secureledger.core.crypto.AesGcmManager
import com.secureledger.core.crypto.HashManager
import com.secureledger.core.crypto.KeyManager
import com.secureledger.core.crypto.decodeBase64
import com.secureledger.core.crypto.encodeBase64
import com.secureledger.core.importer.BusinessFingerprint
import com.secureledger.core.importer.DuplicateLevel
import com.secureledger.core.importer.ImportedTransaction
import com.secureledger.core.importer.ReconciliationMatcher
import com.secureledger.core.importer.ReconciliationStatus
import com.secureledger.core.ledger.PlainRecordPayload
import com.secureledger.data.database.AppDatabase
import com.secureledger.data.entity.ImportedTransactionEntity
import com.secureledger.data.entity.ImportBatchEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

data class ImportedTransactionView(
    val importedId: String,
    val batchId: String,
    val transaction: ImportedTransaction,
    val duplicateLevel: DuplicateLevel,
    val status: ReconciliationStatus,
    val matchedRecordId: String?,
    val explanation: String,
    val importedAt: Long
)

/** Retains access to encrypted historical imports and their reconciliation actions. */
class ImportRepository(
    private val database: AppDatabase,
    private val ledgerRepository: LedgerRepository
) {
    @Serializable private data class TransactionEnvelope(val transaction: ImportedTransaction)

    private val dao = database.importDao()
    private val json = Json { ignoreUnknownKeys = false }

    fun observeTransactions(identity: KeyManager.UserIdentity): Flow<List<ImportedTransactionView>> =
        dao.observeTransactions().map { entities -> entities.mapNotNull { decrypt(it, identity) } }

    /** Persists every user-selected row as an encrypted external anchor, never as an automatic ledger edit. */
    suspend fun importBatch(
        identity: KeyManager.UserIdentity,
        displayName: String,
        transactions: List<ImportedTransaction>
    ): Int {
        require(transactions.isNotEmpty()) { "没有可导入的流水" }
        require(transactions.size <= 500) { "单次最多导入 500 笔" }
        val previous = dao.getTransactions().mapNotNull { decrypt(it, identity)?.transaction }
        val records = ledgerRepository.getDecryptedRecords(identity)
            .filter { it.payload.recordType != "CORRECTION" }
            .map { it.secure.recordId to it.payload }
        val batchId = "b_${UUID.randomUUID()}"
        val now = System.currentTimeMillis()
        val staged = mutableListOf<ImportedTransaction>()
        val entities = transactions.map { transaction ->
            val importedId = "i_${UUID.randomUUID()}"
            val duplicate = (previous + staged).map { BusinessFingerprint.classify(transaction, it) }
                .minByOrNull { it.ordinal } ?: DuplicateLevel.NOT_DUPLICATE
            val match = ReconciliationMatcher.match(transaction, records)
            val status = if (duplicate != DuplicateLevel.NOT_DUPLICATE) ReconciliationStatus.DUPLICATE_REVIEW else match.status
            val explanation = if (status == ReconciliationStatus.DUPLICATE_REVIEW) "与已导入流水相似，请决定保留或忽略" else match.explanation
            val clear = json.encodeToString(TransactionEnvelope.serializer(), TransactionEnvelope(transaction)).toByteArray(Charsets.UTF_8)
            val encrypted = AesGcmManager.encrypt(identity.masterKey, clear, aad(importedId, batchId, transaction.sourceType))
            staged += transaction
            ImportedTransactionEntity(
                importedId, batchId, transaction.sourceType, encrypted.bytes.encodeBase64(),
                encrypted.nonce.encodeBase64(), HashManager.sha256Hex(encrypted.bytes),
                BusinessFingerprint.calculate(transaction), duplicate.name, status.name,
                if (status == ReconciliationStatus.DUPLICATE_REVIEW) null else match.matchedRecordId,
                explanation, now
            )
        }
        database.withTransaction {
            dao.insertBatch(ImportBatchEntity(batchId, "BANK", displayName.take(100), now, entities.size, 0))
            dao.insertTransactions(entities)
        }
        return entities.size
    }

    suspend fun supplement(identity: KeyManager.UserIdentity, importedId: String): String {
        val entity = dao.getTransactions().firstOrNull { it.importedId == importedId }
            ?: error("导入流水不存在")
        val view = decrypt(entity, identity) ?: error("导入流水认证失败")
        require(view.status != ReconciliationStatus.MATCHED && view.status != ReconciliationStatus.SUPPLEMENTED) {
            "该流水已经关联到账目"
        }
        val transaction = view.transaction
        val record = ledgerRepository.addRecord(identity, PlainRecordPayload(
            recordType = if (transaction.direction == "CREDIT") "INCOME" else "EXPENSE",
            amountCents = transaction.amountCents,
            category = transaction.category,
            merchant = transaction.merchant,
            note = transaction.rawDescription,
            account = transaction.accountLast4?.let { "尾号$it" } ?: "外部流水",
            paymentChannel = transaction.paymentChannel,
            tags = listOf("imported", "source:${transaction.sourceType.lowercase()}", "fingerprint:${entity.businessFingerprint}"),
            transactionTime = transaction.transactionTime
        ))
        dao.updateResolution(importedId, ReconciliationStatus.SUPPLEMENTED.name, record.recordId, "已由用户确认补记")
        return record.recordId
    }

    suspend fun ignore(importedId: String) {
        val entity = dao.getTransactions().firstOrNull { it.importedId == importedId }
            ?: error("导入流水不存在")
        require(entity.status != ReconciliationStatus.SUPPLEMENTED.name && entity.status != ReconciliationStatus.MATCHED.name) {
            "该流水已关联账目，不能直接忽略"
        }
        dao.updateResolution(importedId, ReconciliationStatus.IGNORED.name, null, "用户确认忽略")
    }

    suspend fun retain(importedId: String) {
        val entity = dao.getTransactions().firstOrNull { it.importedId == importedId }
            ?: error("导入流水不存在")
        require(entity.status == ReconciliationStatus.DUPLICATE_REVIEW.name) { "该流水不在重复核对状态" }
        dao.updateResolution(importedId, ReconciliationStatus.RETAINED.name, null, "用户确认保留此重复流水（不自动补记）")
    }

    private fun decrypt(entity: ImportedTransactionEntity, identity: KeyManager.UserIdentity): ImportedTransactionView? = runCatching {
        val ciphertext = entity.encryptedPayload.decodeBase64()
        require(HashManager.sha256Hex(ciphertext) == entity.payloadHash) { "Imported payload hash mismatch" }
        val clear = AesGcmManager.decrypt(
            identity.masterKey,
            AesGcmManager.Ciphertext(entity.nonce.decodeBase64(), ciphertext),
            aad(entity.importedId, entity.batchId, entity.sourceType)
        )
        val transaction = json.decodeFromString(TransactionEnvelope.serializer(), clear.toString(Charsets.UTF_8)).transaction
        ImportedTransactionView(
            entity.importedId, entity.batchId, transaction,
            DuplicateLevel.valueOf(entity.duplicateLevel), ReconciliationStatus.valueOf(entity.status),
            entity.matchedRecordId, entity.explanation, entity.importedAt
        )
    }.getOrNull()

    private fun aad(importedId: String, batchId: String, sourceType: String): ByteArray =
        "$importedId|$batchId|$sourceType|1".toByteArray(Charsets.UTF_8)

}
