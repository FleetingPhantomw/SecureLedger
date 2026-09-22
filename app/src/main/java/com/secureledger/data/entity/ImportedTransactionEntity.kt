package com.secureledger.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Encrypted external transaction. Searchable fields are limited to hashes and
 * workflow state; amount, merchant and raw statement text never appear in clear.
 */
@Entity(
    tableName = "imported_transactions",
    foreignKeys = [ForeignKey(
        entity = ImportBatchEntity::class,
        parentColumns = ["batchId"],
        childColumns = ["batchId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("batchId"), Index("businessFingerprint"), Index("status"), Index("importedAt")]
)
@Serializable
data class ImportedTransactionEntity(
    @PrimaryKey val importedId: String,
    val batchId: String,
    val sourceType: String,
    val encryptedPayload: String,
    val nonce: String,
    val payloadHash: String,
    val businessFingerprint: String,
    val duplicateLevel: String,
    val status: String,
    val matchedRecordId: String?,
    val explanation: String,
    val importedAt: Long
)
