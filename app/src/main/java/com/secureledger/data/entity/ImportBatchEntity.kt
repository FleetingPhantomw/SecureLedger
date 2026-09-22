package com.secureledger.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Summary of an explicitly selected local statement file. */
@Entity(tableName = "import_batches")
@Serializable
data class ImportBatchEntity(
    @PrimaryKey val batchId: String,
    val sourceType: String,
    val displayName: String,
    val importedAt: Long,
    val acceptedRows: Int,
    val rejectedRows: Int
)
