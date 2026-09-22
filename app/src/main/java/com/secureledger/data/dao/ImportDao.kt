package com.secureledger.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.secureledger.data.entity.ImportBatchEntity
import com.secureledger.data.entity.ImportedTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportDao {
    @Query("SELECT * FROM imported_transactions ORDER BY importedAt DESC")
    fun observeTransactions(): Flow<List<ImportedTransactionEntity>>

    @Query("SELECT * FROM imported_transactions ORDER BY importedAt DESC")
    suspend fun getTransactions(): List<ImportedTransactionEntity>

    @Query("SELECT * FROM import_batches ORDER BY importedAt")
    suspend fun getBatches(): List<ImportBatchEntity>

    @Query("SELECT COUNT(*) FROM import_batches")
    suspend fun batchCount(): Int

    @Query("SELECT * FROM import_batches ORDER BY importedAt DESC")
    fun observeBatches(): Flow<List<ImportBatchEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBatch(batch: ImportBatchEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBatches(batches: List<ImportBatchEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransactions(transactions: List<ImportedTransactionEntity>)

    @Query("UPDATE imported_transactions SET status = :status, matchedRecordId = :recordId, explanation = :explanation WHERE importedId = :importedId")
    suspend fun updateResolution(importedId: String, status: String, recordId: String?, explanation: String)
}
