package com.secureledger.core.ledger

import com.secureledger.core.crypto.HashManager
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable

/** Deterministic field writer used for hashes and signatures across devices. */
object CanonicalRecord {
    private val timeFormatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC)

    fun text(value: String?): String = value?.let {
        Normalizer.normalize(it, Normalizer.Form.NFC).replace("\r\n", "\n").replace('\r', '\n')
    } ?: "<null>"

    fun timestamp(epochMillis: Long): String = timeFormatter.format(Instant.ofEpochMilli(epochMillis))

    fun amount(cents: Long): String = BigDecimal(cents).divide(BigDecimal(100))
        .setScale(2, RoundingMode.UNNECESSARY).toPlainString()

    fun payload(payload: PlainRecordPayload): ByteArray = lines(
        "schemaVersion" to payload.schemaVersion.toString(),
        "recordType" to payload.recordType,
        "amountCents" to payload.amountCents.toString(),
        "category" to payload.category,
        "merchant" to payload.merchant,
        "note" to payload.note,
        "account" to payload.account,
        "paymentChannel" to payload.paymentChannel,
        "paymentChannelCode" to payload.paymentChannelCode,
        "bankCardId" to payload.bankCardId,
        "bankCardDisplaySnapshot" to payload.bankCardDisplaySnapshot,
        "primaryCategoryCode" to payload.primaryCategoryCode,
        "legacyCategory" to payload.legacyCategory,
        "orderId" to payload.orderId,
        "payments" to payload.payments.sortedWith(compareBy<PaymentDetail> { it.channel }.thenBy { it.amountCents })
            .joinToString(";") { "${text(it.channel)},${it.amountCents},${it.isBankDirect}" },
        "voucherRefs" to payload.voucherRefs.sorted().joinToString(","),
        "tags" to payload.tags.sorted().joinToString(","),
        "transactionTime" to timestamp(payload.transactionTime),
        "correctionTargetRecordId" to payload.correctionTargetRecordId,
        "correctionOldValue" to payload.correctionOldValue,
        "correctionNewValue" to payload.correctionNewValue,
        "correctionReason" to payload.correctionReason
    )

    /** Fields are intentionally ordered and length-delimited by line boundaries. */
    fun unsignedRecord(record: SecureRecord): ByteArray = lines(
        "recordId" to record.recordId,
        "projectId" to record.projectId,
        "authorId" to record.authorId,
        "authorSequence" to record.authorSequence.toString(),
        "recordType" to record.recordType,
        "transactionTime" to timestamp(record.transactionTime),
        "createdAt" to timestamp(record.createdAt),
        "schemaVersion" to record.schemaVersion.toString(),
        "encryptedPayload" to record.encryptedPayload,
        "nonce" to record.nonce,
        "payloadHash" to record.payloadHash,
        "previousHash" to record.previousHash
    )

    fun signedRecord(record: SecureRecord): ByteArray = lines(
        "unsigned" to unsignedRecord(record).toString(Charsets.UTF_8),
        "recordHash" to record.recordHash
    )

    private fun lines(vararg fields: Pair<String, String?>): ByteArray = buildString {
        fields.forEach { (name, value) ->
            val normalized = text(value)
            val utf8 = normalized.toByteArray(Charsets.UTF_8)
            append(name).append(':').append(utf8.size).append(':').append(normalized).append('\n')
        }
    }.toByteArray(Charsets.UTF_8)
}

/** Cleartext transaction fields. This object is encrypted before persistence. */
@Serializable
data class PlainRecordPayload(
    val schemaVersion: Int = 2,
    val recordType: String,
    val amountCents: Long,
    val category: String,
    val merchant: String,
    val note: String,
    val account: String,
    val paymentChannel: String,
    val paymentChannelCode: String = PaymentChannelCode.fromLegacy(paymentChannel).name,
    val bankCardId: String? = null,
    val bankCardDisplaySnapshot: String? = null,
    val primaryCategoryCode: String = ExpenseCategoryCode.fromLegacy(category).name,
    val legacyCategory: String? = category.takeIf { it != ExpenseCategoryCode.fromLegacy(it).displayName },
    val orderId: String? = null,
    val payments: List<PaymentDetail> = emptyList(),
    val voucherRefs: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val transactionTime: Long,
    val correctionTargetRecordId: String? = null,
    val correctionOldValue: String? = null,
    val correctionNewValue: String? = null,
    val correctionReason: String? = null
)

/** One component of a combined payment, retained inside the encrypted payload. */
@Serializable
data class PaymentDetail(
    val amountCents: Long,
    val channel: String,
    val isBankDirect: Boolean = false
)

/** Immutable signed envelope persisted by the ledger and transported by sync packages. */
@Serializable
data class SecureRecord(
    val recordId: String,
    val projectId: String,
    val authorId: String,
    val authorSequence: Long,
    val recordType: String,
    val transactionTime: Long,
    val createdAt: Long,
    val schemaVersion: Int,
    val encryptedPayload: String,
    val nonce: String,
    val payloadHash: String,
    val previousHash: String,
    val recordHash: String,
    val signature: String,
    val authorPublicKey: String
) {
    companion object {
        fun calculateHash(record: SecureRecord): String = HashManager.sha256Hex(CanonicalRecord.unsignedRecord(record))
    }
}
