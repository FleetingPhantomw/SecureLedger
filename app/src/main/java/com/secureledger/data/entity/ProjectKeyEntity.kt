package com.secureledger.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** A project AES key encrypted by this device's unlocked vault master key. */
@Entity(
    tableName = "project_keys",
    foreignKeys = [ForeignKey(
        entity = ProjectEntity::class,
        parentColumns = ["projectId"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )]
)
@Serializable
data class ProjectKeyEntity(
    @PrimaryKey val projectId: String,
    val encryptedKey: String,
    val nonce: String,
    val keyFingerprint: String,
    val addedAt: Long
)
