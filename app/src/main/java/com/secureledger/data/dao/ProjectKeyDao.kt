package com.secureledger.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.secureledger.data.entity.ProjectKeyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectKeyDao {
    @Query("SELECT * FROM project_keys ORDER BY projectId")
    fun observeAll(): Flow<List<ProjectKeyEntity>>

    @Query("SELECT * FROM project_keys WHERE projectId = :projectId LIMIT 1")
    suspend fun get(projectId: String): ProjectKeyEntity?

    @Query("SELECT * FROM project_keys ORDER BY projectId")
    suspend fun getAll(): List<ProjectKeyEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ProjectKeyEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entities: List<ProjectKeyEntity>)
}
