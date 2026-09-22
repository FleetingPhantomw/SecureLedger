package com.secureledger.data.entity

import androidx.room.Entity
import androidx.room.Index
import kotlinx.serialization.Serializable

/** Encrypted, immutable storage envelope for a signed AA settlement proof. */
@Entity(
    tableName = "settlements",
    indices = [Index("projectId"), Index("createdAt")]
)
@Serializable
data class SettlementEntity(
    @androidx.room.PrimaryKey val settlementId: String,
    val projectId: String,
    val createdAt: Long,
    val proofHash: String,
    val nonce: String,
    val encryptedProof: String
)
