package com.secureledger.data.repository

import androidx.room.withTransaction
import com.secureledger.core.crypto.AesGcmManager
import com.secureledger.core.crypto.HashManager
import com.secureledger.core.crypto.KeyManager
import com.secureledger.core.crypto.decodeBase64
import com.secureledger.core.crypto.encodeBase64
import com.secureledger.core.importer.BusinessFingerprint
import com.secureledger.core.importer.CsvImportIssue
import com.secureledger.core.importer.DataSourceType
import com.secureledger.core.importer.DuplicateLevel
import com.secureledger.core.importer.ImportedTransaction
import com.secureledger.core.importer.ReconciliationMatcher
import com.secureledger.core.importer.ReconciliationStatus
import com.secureledger.core.importer.StatementCsvParser
import com.secureledger.core.ledger.PlainRecordPayload
import com.secureledger.data.database.AppDatabase
import com.secureledger.data.entity.ImportBatchEntity
import com.secureledger.data.entity.ImportedTransactionEntity
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.UUID
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

data class ImportSummary(
    val batchId: String,
    val acceptedRows: Int,
    val rejectedRows: Int,
    val issues: List<CsvImportIssue>
)

/** Coordinates bounded file decoding, conservative deduplication, matching and encrypted persistence. */
class ImportRepository(
    private val database: AppDatabase,
    private val ledgerRepository: LedgerRepository,
    private val parser: StatementCsvParser = StatementCsvParser()
) {
    @Serializable private data class TransactionEnvelope(val transaction: ImportedTransaction)

    private val dao = database.importDao()
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }

    fun observeTransactions(identity: KeyManager.UserIdentity): Flow<List<ImportedTransactionView>> =
        dao.observeTransactions().map { entities -> entities.mapNotNull { decrypt(it, identity) } }

    suspend fun importCsv(
        identity: KeyManager.UserIdentity,
        sourceType: DataSourceType,
        displayName: String,
        bytes: ByteArray
    ): ImportSummary {
        require(bytes.size <= MAX_FILE_BYTES) { "文件超过 5 MiB 限制" }
        val parsed = parser.parse(decodeText(bytes), sourceType)
        require(parsed.transactions.isNotEmpty()) { parsed.issues.firstOrNull()?.message ?: "CSV 中没有可导入交易" }
        val existing = dao.getTransactions().mapNotNull { decrypt(it, identity)?.transaction }
        val ledger = ledgerRepository.getDecryptedRecords(identity)
            .map { it.secure.recordId to it.payload }
        val batchId = "batch_${UUID.randomUUID()}"
        val importedAt = System.currentTimeMillis()
        val acceptedSoFar = mutableListOf<ImportedTransaction>()
        val entities = parsed.transactions.map { transaction ->
            val duplicate = (existing + acceptedSoFar)
                .map { BusinessFingerprint.classify(transaction, it) }
                .minByOrNull { it.ordinal } ?: DuplicateLevel.NOT_DUPLICATE
            val match = if (duplicate == DuplicateLevel.CONFIRMED_DUPLICATE ||
                duplicate == DuplicateLevel.HIGHLY_SUSPECTED) {
                com.secureledger.core.importer.ReconciliationMatch(
                    ReconciliationStatus.DUPLICATE_REVIEW,
                    explanation = if (duplicate == DuplicateLevel.CONFIRMED_DUPLICATE) "订单号与已导入流水相同" else "金额、时间与商户高度相似"
                )
            } else ReconciliationMatcher.match(transaction, ledger)
            acceptedSoFar += transaction
            encrypt(identity, batchId, transaction, duplicate, match.status, match.matchedRecordId, match.explanation, importedAt)
        }
        database.withTransaction {
            dao.insertBatch(ImportBatchEntity(batchId, sourceType.name, displayName.take(120), importedAt, entities.size, parsed.issues.size))
            dao.insertTransactions(entities)
        }
        return ImportSummary(batchId, entities.size, parsed.issues.size, parsed.issues.take(20))
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
        dao.updateResolution(importedId, ReconciliationStatus.IGNORED.name, null, "用户确认忽略")
    }

    private fun encrypt(
        identity: KeyManager.UserIdentity,
        batchId: String,
        transaction: ImportedTransaction,
        duplicate: DuplicateLevel,
        status: ReconciliationStatus,
        matchedRecordId: String?,
        explanation: String,
        importedAt: Long
    ): ImportedTransactionEntity {
        val importedId = "import_${UUID.randomUUID()}"
        val bytes = json.encodeToString(TransactionEnvelope.serializer(), TransactionEnvelope(transaction)).toByteArray(Charsets.UTF_8)
        val ciphertext = AesGcmManager.encrypt(identity.masterKey, bytes, aad(importedId, batchId, transaction.sourceType))
        return ImportedTransactionEntity(
            importedId, batchId, transaction.sourceType,
            ciphertext.bytes.encodeBase64(), ciphertext.nonce.encodeBase64(),
            HashManager.sha256Hex(ciphertext.bytes), BusinessFingerprint.calculate(transaction),
            duplicate.name, status.name, matchedRecordId, explanation, importedAt
        )
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

    private fun decodeText(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return bytes.copyOfRange(3, bytes.size).toString(Charsets.UTF_8)
        }
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            bytes.toString(charset("GB18030"))
        }
    }

    companion object {
        const val MAX_FILE_BYTES = 5 * 1024 * 1024
    }
}
