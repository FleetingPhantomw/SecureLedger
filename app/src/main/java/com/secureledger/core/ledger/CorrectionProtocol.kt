package com.secureledger.core.ledger

/** Corrections are new signed records; an accepted record is never updated in place. */
/** User intent for an append-only amount correction. */
data class CorrectionRequest(
    val correctionId: String,
    val targetRecordId: String,
    val oldValue: String,
    val newValue: String,
    val reason: String,
    val createdAt: Long
)

object CorrectionProtocol {
    fun toPayload(request: CorrectionRequest): PlainRecordPayload = PlainRecordPayload(
        recordType = "CORRECTION",
        amountCents = 0,
        category = "correction",
        merchant = request.targetRecordId,
        note = "${request.oldValue} -> ${request.newValue}; ${request.reason}",
        account = "system",
        paymentChannel = "internal",
        transactionTime = request.createdAt,
        correctionTargetRecordId = request.targetRecordId,
        correctionOldValue = request.oldValue,
        correctionNewValue = request.newValue,
        correctionReason = request.reason
    )
}
