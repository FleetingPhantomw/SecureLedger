package com.secureledger.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.secureledger.data.entity.LedgerRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerRecordDao {
    @Query("SELECT * FROM ledger_records ORDER BY transactionTime DESC, createdAt DESC")
    fun observeAll(): Flow<List<LedgerRecordEntity>>

    @Query("SELECT * FROM ledger_records ORDER BY authorId ASC, authorSequence ASC")
    suspend fun getAll(): List<LedgerRecordEntity>

    @Query("SELECT * FROM ledger_records WHERE projectId = :projectId ORDER BY authorId ASC, authorSequence ASC")
    suspend fun getAllByProject(projectId: String): List<LedgerRecordEntity>

    @Query("SELECT * FROM ledger_records WHERE authorId = :authorId ORDER BY authorSequence DESC LIMIT 1")
    suspend fun getLastByAuthor(authorId: String): LedgerRecordEntity?

    @Query("SELECT * FROM ledger_records WHERE authorId = :authorId AND projectId = :projectId ORDER BY authorSequence DESC LIMIT 1")
    suspend fun getLastByAuthorAndProject(authorId: String, projectId: String): LedgerRecordEntity?

    @Query("SELECT * FROM ledger_records WHERE recordId = :recordId LIMIT 1")
    suspend fun getById(recordId: String): LedgerRecordEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: LedgerRecordEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(records: List<LedgerRecordEntity>)

    @Query("SELECT COUNT(*) FROM ledger_records")
    suspend fun count(): Int
}
