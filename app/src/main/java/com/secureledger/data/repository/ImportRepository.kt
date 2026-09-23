package com.secureledger.data.repository

import com.secureledger.core.crypto.AesGcmManager
import com.secureledger.core.crypto.HashManager
import com.secureledger.core.crypto.KeyManager
import com.secureledger.core.crypto.decodeBase64
import com.secureledger.core.importer.DuplicateLevel
import com.secureledger.core.importer.ImportedTransaction
import com.secureledger.core.importer.ReconciliationStatus
import com.secureledger.core.ledger.PlainRecordPayload
import com.secureledger.data.database.AppDatabase
import com.secureledger.data.entity.ImportedTransactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
        dao.updateResolution(importedId, ReconciliationStatus.IGNORED.name, null, "用户确认忽略")
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
