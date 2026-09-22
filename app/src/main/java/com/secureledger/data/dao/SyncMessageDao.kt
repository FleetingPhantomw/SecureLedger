package com.secureledger.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.secureledger.data.entity.ProcessedSyncMessageEntity

@Dao
interface SyncMessageDao {
    @Query("SELECT * FROM processed_sync_messages ORDER BY processedAt")
    suspend fun getAll(): List<ProcessedSyncMessageEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM processed_sync_messages WHERE messageKey = :messageKey)")
    suspend fun contains(messageKey: String): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(message: ProcessedSyncMessageEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(messages: List<ProcessedSyncMessageEntity>)
}
