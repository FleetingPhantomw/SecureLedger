package com.secureledger.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.secureledger.core.ledger.SecureRecord

/** Room representation of the immutable signed envelope; cleartext payload is never stored here. */
@Entity(tableName = "ledger_records")
data class LedgerRecordEntity(
    @PrimaryKey val recordId: String,
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
    fun toDomain() = SecureRecord(recordId, projectId, authorId, authorSequence, recordType, transactionTime,
        createdAt, schemaVersion, encryptedPayload, nonce, payloadHash, previousHash, recordHash, signature, authorPublicKey)

    companion object {
        fun fromDomain(record: SecureRecord) = LedgerRecordEntity(record.recordId, record.projectId, record.authorId,
            record.authorSequence, record.recordType, record.transactionTime, record.createdAt, record.schemaVersion,
            record.encryptedPayload, record.nonce, record.payloadHash, record.previousHash, record.recordHash,
            record.signature, record.authorPublicKey)
    }
}
