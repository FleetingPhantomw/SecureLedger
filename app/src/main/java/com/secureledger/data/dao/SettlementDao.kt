package com.secureledger.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.secureledger.data.entity.SettlementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettlementDao {
    @Query("SELECT * FROM settlements ORDER BY createdAt")
    suspend fun getAll(): List<SettlementEntity>

    @Query("SELECT * FROM settlements WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun observeByProject(projectId: String): Flow<List<SettlementEntity>>

    @Query("SELECT * FROM settlements WHERE projectId = :projectId ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestByProject(projectId: String): SettlementEntity?

    @Query("SELECT * FROM settlements WHERE settlementId = :settlementId LIMIT 1")
    suspend fun getById(settlementId: String): SettlementEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(settlement: SettlementEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(settlements: List<SettlementEntity>)

    @Query("UPDATE settlements SET nonce = :nonce, encryptedProof = :encryptedProof WHERE settlementId = :settlementId AND proofHash = :proofHash")
    suspend fun updateSignatures(settlementId: String, proofHash: String, nonce: String, encryptedProof: String): Int
}
