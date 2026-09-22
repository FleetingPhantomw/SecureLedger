package com.secureledger.core.ledger

/** Read-only chain rules for one author's append-only sequence. */
object LedgerHashChain {
    const val GENESIS_HASH = "GENESIS"

    fun validate(records: List<SecureRecord>): List<IntegrityIssue> {
        val sorted = records.sortedBy { it.authorSequence }
        val issues = mutableListOf<IntegrityIssue>()
        var expectedSequence = 1L
        var previousHash = GENESIS_HASH
        sorted.forEach { record ->
            if (record.authorSequence != expectedSequence) {
                issues += IntegrityIssue(record.recordId, IntegrityFailure.SEQUENCE_GAP,
                    "expected=$expectedSequence actual=${record.authorSequence}")
            }
            if (record.previousHash != previousHash) {
                issues += IntegrityIssue(record.recordId, IntegrityFailure.PREVIOUS_HASH_INVALID,
                    "expected=$previousHash actual=${record.previousHash}")
            }
            previousHash = record.recordHash
            expectedSequence = record.authorSequence + 1
        }
        return issues
    }
}

/** Concrete failure classes reported by a full ledger verification. */
enum class IntegrityFailure { HASH_MISMATCH, SIGNATURE_INVALID, PREVIOUS_HASH_INVALID, SEQUENCE_GAP, DECRYPTION_FAILED }

/** One localized integrity failure and its diagnostic detail. */
data class IntegrityIssue(val recordId: String, val failure: IntegrityFailure, val detail: String)

/** Point-in-time verification result displayed by the security centre. */
data class IntegrityReport(
    val checkedRecords: Int,
    val issues: List<IntegrityIssue>,
    val merkleRoot: String,
    val checkedAt: Long
) {
    val isValid: Boolean get() = issues.isEmpty()
}
