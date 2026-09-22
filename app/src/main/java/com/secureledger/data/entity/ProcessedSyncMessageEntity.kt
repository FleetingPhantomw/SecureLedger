package com.secureledger.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Durable replay marker. It is inserted only after the package has fully validated. */
@Entity(
    tableName = "processed_sync_messages",
    indices = [Index("packageId"), Index("processedAt")]
)
@Serializable
data class ProcessedSyncMessageEntity(
    @PrimaryKey val messageKey: String,
    val packageId: String,
    val nonce: String,
    val senderId: String,
    val processedAt: Long
)
