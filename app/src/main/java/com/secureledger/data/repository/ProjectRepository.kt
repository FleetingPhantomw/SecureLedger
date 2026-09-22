package com.secureledger.data.repository

import com.secureledger.core.crypto.KeyManager
import com.secureledger.core.crypto.encodeBase64
import com.secureledger.data.dao.ProjectDao
import com.secureledger.data.entity.ProjectEntity
import com.secureledger.data.entity.ProjectMemberEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Project/member persistence boundary with independent project-key creation. */
class ProjectRepository(
    private val dao: ProjectDao,
    private val projectKeys: ProjectKeyRepository? = null
) {
    fun observeProjects(): Flow<List<ProjectEntity>> = dao.observeProjects()

    fun observeMembers(projectId: String): Flow<List<ProjectMemberEntity>> = dao.observeMembers(projectId)

    suspend fun ensurePersonalProject(identity: KeyManager.UserIdentity) {
        if (dao.getProject(PERSONAL_PROJECT_ID) == null) {
            createProject(identity, PERSONAL_PROJECT_ID, "个人账本")
        } else {
            dao.upsertMember(member(identity, PERSONAL_PROJECT_ID, "OWNER", "我", System.currentTimeMillis()))
        }
    }

    suspend fun createProject(identity: KeyManager.UserIdentity, projectId: String = "p_${UUID.randomUUID()}", name: String): ProjectEntity {
        require(name.trim().isNotEmpty()) { "项目名称不能为空" }
        val now = System.currentTimeMillis()
        val project = ProjectEntity(projectId, name.trim(), identity.userId, now)
        dao.insertProject(project)
        dao.insertMember(member(identity, projectId, "OWNER", "我", now))
        if (projectId != PERSONAL_PROJECT_ID) {
            checkNotNull(projectKeys) { "Project key repository is required" }.create(identity, projectId)
        }
        return project
    }

    suspend fun addMember(member: ProjectMemberEntity) = dao.upsertMember(member)

    suspend fun project(projectId: String): ProjectEntity? = dao.getProject(projectId)

    suspend fun members(projectId: String): List<ProjectMemberEntity> = dao.getMembers(projectId)

    private fun member(
        identity: KeyManager.UserIdentity,
        projectId: String,
        role: String,
        displayName: String,
        joinedAt: Long
    ) = ProjectMemberEntity(
        projectId = projectId,
        userId = identity.userId,
        displayName = displayName,
        publicKey = identity.publicKey.encodeBase64(),
        agreementPublicKey = identity.agreementPublicKey.encodeBase64(),
        role = role,
        joinedAt = joinedAt
    )

    companion object { const val PERSONAL_PROJECT_ID = "personal" }
}
