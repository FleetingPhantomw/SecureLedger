package com.secureledger.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.secureledger.data.entity.BankCardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BankCardDao {
    @Query("SELECT * FROM bank_cards ORDER BY enabled DESC, createdAt ASC")
    fun observeAll(): Flow<List<BankCardEntity>>

    @Query("SELECT * FROM bank_cards ORDER BY createdAt ASC")
    suspend fun getAll(): List<BankCardEntity>

    @Query("SELECT * FROM bank_cards WHERE id = :id LIMIT 1")
    suspend fun get(id: String): BankCardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(card: BankCardEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(cards: List<BankCardEntity>)

    @Delete
    suspend fun delete(card: BankCardEntity)
}
