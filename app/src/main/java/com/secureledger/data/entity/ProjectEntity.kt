package com.secureledger.data.entity

import androidx.room.Entity
import kotlinx.serialization.Serializable

/** Stable project container for personal or multi-party ledgers. */
@Entity(tableName = "projects")
@Serializable
data class ProjectEntity(
    @androidx.room.PrimaryKey val projectId: String,
    val name: String,
    val createdBy: String,
    val createdAt: Long,
    val status: String = "ACTIVE"
)
