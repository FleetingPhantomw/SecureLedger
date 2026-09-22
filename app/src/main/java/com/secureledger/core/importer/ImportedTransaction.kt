package com.secureledger.core.importer

import kotlinx.serialization.Serializable

/** External statement kinds supported by the local import pipeline. */
enum class DataSourceType { BANK, CAMPUS_CARD }

/** Normalized direction. Amounts remain positive; direction carries the sign. */
enum class TransactionDirection { DEBIT, CREDIT }

/**
 * Canonical representation of one external statement row. The raw description is
 * retained inside encrypted storage so an import can always be audited later.
 */
@Serializable
data class ImportedTransaction(
    val schemaVersion: Int = 1,
    val sourceType: String,
    val externalOrderId: String? = null,
    val amountCents: Long,
    val direction: String,
    val merchant: String,
    val transactionTime: Long,
    val paymentChannel: String,
    val accountLast4: String? = null,
    val category: String,
    val rawDescription: String,
    val sourceRow: Long
) {
    init {
        require(amountCents >= 0) { "Amount must be non-negative" }
        require(sourceType in DataSourceType.entries.map(Enum<*>::name)) { "Unsupported source type" }
        require(direction in TransactionDirection.entries.map(Enum<*>::name)) { "Unsupported direction" }
    }
}

/** Conservative business-duplicate confidence; it is separate from replay defense. */
enum class DuplicateLevel {
    CONFIRMED_DUPLICATE,
    HIGHLY_SUSPECTED,
    POSSIBLE_DUPLICATE,
    NOT_DUPLICATE
}

/** User-visible lifecycle of an imported external transaction. */
enum class ReconciliationStatus {
    MATCHED,
    POSSIBLE,
    MISSING,
    CONFLICT,
    DUPLICATE_REVIEW,
    IGNORED,
    SUPPLEMENTED
}

/** Non-fatal row-level parsing rejection. */
data class CsvImportIssue(val row: Long, val message: String)

/** Parsed rows and explicit rejects from one bounded CSV input. */
data class CsvImportResult(
    val transactions: List<ImportedTransaction>,
    val issues: List<CsvImportIssue>
)

/** Explainable result of comparing one external anchor with local records. */
data class ReconciliationMatch(
    val status: ReconciliationStatus,
    val matchedRecordId: String? = null,
    val explanation: String
)
