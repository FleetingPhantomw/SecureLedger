package com.secureledger.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.secureledger.data.entity.ProjectEntity
import com.secureledger.data.entity.ProjectMemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY createdAt ASC")
    suspend fun getAllProjects(): List<ProjectEntity>

    @Query("SELECT * FROM project_members ORDER BY projectId, joinedAt")
    suspend fun getAllMembers(): List<ProjectMemberEntity>

    @Query("SELECT * FROM projects ORDER BY createdAt ASC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE projectId = :projectId LIMIT 1")
    suspend fun getProject(projectId: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProject(project: ProjectEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProjects(projects: List<ProjectEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMember(member: ProjectMemberEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMembers(members: List<ProjectMemberEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMember(member: ProjectMemberEntity)

    @Query("SELECT * FROM project_members WHERE projectId = :projectId ORDER BY joinedAt ASC")
    fun observeMembers(projectId: String): Flow<List<ProjectMemberEntity>>

    @Query("SELECT * FROM project_members WHERE projectId = :projectId ORDER BY joinedAt ASC")
    suspend fun getMembers(projectId: String): List<ProjectMemberEntity>

    @Query("SELECT * FROM project_members WHERE projectId = :projectId AND userId = :userId LIMIT 1")
    suspend fun getMember(projectId: String, userId: String): ProjectMemberEntity?
}
