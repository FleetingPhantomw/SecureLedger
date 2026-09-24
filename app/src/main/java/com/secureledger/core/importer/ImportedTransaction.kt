package com.secureledger.core.importer

import kotlinx.serialization.Serializable

/** Historical source codes retained so existing encrypted imports still decode. */
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
        require(sourceType.matches(Regex("[A-Z][A-Z0-9_]{1,63}"))) { "Unsupported source type" }
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
    SUPPLEMENTED,
    RETAINED
}

/** Explainable result of comparing one external anchor with local records. */
data class ReconciliationMatch(
    val status: ReconciliationStatus,
    val matchedRecordId: String? = null,
    val explanation: String
)
