package com.secureledger.core.importer

import com.secureledger.core.crypto.HashManager
import com.secureledger.core.ledger.CanonicalRecord
import com.secureledger.core.ledger.PlainRecordPayload
import java.text.Normalizer
import kotlin.math.abs

/** Deterministic business fingerprint plus conservative, explainable duplicate rules. */
object BusinessFingerprint {
    fun calculate(transaction: ImportedTransaction): String = HashManager.sha256Hex(
        listOf(
            transaction.sourceType,
            normalize(transaction.externalOrderId.orEmpty()),
            transaction.amountCents.toString(),
            transaction.direction,
            normalize(transaction.merchant),
            CanonicalRecord.timestamp(transaction.transactionTime),
            normalize(transaction.paymentChannel),
            transaction.accountLast4.orEmpty()
        ).joinToString("\u001f").toByteArray(Charsets.UTF_8)
    )

    fun classify(candidate: ImportedTransaction, existing: ImportedTransaction): DuplicateLevel {
        val sameSource = candidate.sourceType == existing.sourceType
        val sameOrder = candidate.externalOrderId?.takeIf(String::isNotBlank)?.let {
            sameSource && normalize(it) == normalize(existing.externalOrderId.orEmpty())
        } == true
        if (sameOrder) return DuplicateLevel.CONFIRMED_DUPLICATE

        val sameAmount = candidate.amountCents == existing.amountCents && candidate.direction == existing.direction
        val timeDistance = abs(candidate.transactionTime - existing.transactionTime)
        val merchantSimilarity = similarity(candidate.merchant, existing.merchant)
        val accountCompatible = candidate.accountLast4 == null || existing.accountLast4 == null ||
            candidate.accountLast4 == existing.accountLast4
        return when {
            sameSource && sameAmount && timeDistance <= 2 * 60_000 && merchantSimilarity >= 0.85 && accountCompatible ->
                DuplicateLevel.HIGHLY_SUSPECTED
            sameAmount && timeDistance <= 24 * 60 * 60_000 && merchantSimilarity >= 0.45 ->
                DuplicateLevel.POSSIBLE_DUPLICATE
            else -> DuplicateLevel.NOT_DUPLICATE
        }
    }

    internal fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase().filter { it.isLetterOrDigit() }

    internal fun similarity(left: String, right: String): Double {
        val a = normalize(left)
        val b = normalize(right)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        if (a.contains(b) || b.contains(a)) return minOf(a.length, b.length).toDouble() / maxOf(a.length, b.length)
        val common = a.toSet().intersect(b.toSet()).size
        return (2.0 * common) / (a.toSet().size + b.toSet().size)
    }
}

/** Matches an external anchor to ledger records without claiming cryptographic proof of purchase. */
object ReconciliationMatcher {
    fun match(transaction: ImportedTransaction, records: List<Pair<String, PlainRecordPayload>>): ReconciliationMatch {
        val expectedType = if (transaction.direction == TransactionDirection.CREDIT.name) "INCOME" else "EXPENSE"
        val candidates = records.map { (recordId, payload) ->
            Candidate(
                recordId,
                payload,
                abs(transaction.transactionTime - payload.transactionTime),
                BusinessFingerprint.similarity(transaction.merchant, payload.merchant)
            )
        }.filter { it.payload.recordType == expectedType }

        candidates.filter { it.payload.amountCents == transaction.amountCents }
            .minWithOrNull(compareBy<Candidate> { it.timeDistance }.thenByDescending { it.merchantSimilarity })
            ?.let { best ->
                if (best.timeDistance <= 5 * 60_000 && best.merchantSimilarity >= 0.55) {
                    return ReconciliationMatch(ReconciliationStatus.MATCHED, best.recordId, "金额、时间与商户一致")
                }
                if (best.timeDistance <= 24 * 60 * 60_000) {
                    return ReconciliationMatch(ReconciliationStatus.POSSIBLE, best.recordId, "金额一致，需确认时间或商户")
                }
            }

        candidates.filter { it.timeDistance <= 10 * 60_000 && it.merchantSimilarity >= 0.55 }
            .minByOrNull { abs(it.payload.amountCents - transaction.amountCents) }
            ?.let { return ReconciliationMatch(ReconciliationStatus.CONFLICT, it.recordId, "时间与商户接近，但金额冲突") }

        return ReconciliationMatch(ReconciliationStatus.MISSING, explanation = "未找到可信账目，可能漏记")
    }

    private data class Candidate(
        val recordId: String,
        val payload: PlainRecordPayload,
        val timeDistance: Long,
        val merchantSimilarity: Double
    )
}
