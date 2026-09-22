package com.secureledger.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import kotlinx.serialization.Serializable

/** Project membership binds a participant to the Ed25519 public key used for approvals. */
@Entity(
    tableName = "project_members",
    primaryKeys = ["projectId", "userId"],
    foreignKeys = [ForeignKey(
        entity = ProjectEntity::class,
        parentColumns = ["projectId"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )]
)
@Serializable
data class ProjectMemberEntity(
    val projectId: String,
    val userId: String,
    val displayName: String,
    val publicKey: String,
    val agreementPublicKey: String = "",
    val role: String,
    val joinedAt: Long
)
