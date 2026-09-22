package com.secureledger.data.repository

import androidx.room.withTransaction
import com.secureledger.core.crypto.KeyManager
import com.secureledger.core.crypto.decodeBase64
import com.secureledger.core.crypto.encodeBase64
import com.secureledger.core.protocol.ProjectInvitationCodec
import com.secureledger.core.protocol.ProjectInvitationCrypto
import com.secureledger.core.protocol.ProjectMemberDescriptor
import com.secureledger.data.database.AppDatabase
import com.secureledger.data.entity.ProjectEntity
import com.secureledger.data.entity.ProjectMemberEntity

/** Persists only fully verified membership invitations and their decrypted project key. */
class InvitationRepository(
    private val database: AppDatabase,
    private val projectKeys: ProjectKeyRepository
) {
    private val projectDao = database.projectDao()

    fun createJoinRequest(identity: KeyManager.UserIdentity, displayName: String): String =
        ProjectInvitationCodec.encodeJoin(ProjectInvitationCrypto.createJoinRequest(
            identity.userId, displayName, identity.publicKey, identity.agreementPublicKey, identity.privateSeed
        ))

    suspend fun issueInvitation(identity: KeyManager.UserIdentity, projectId: String, encodedRequest: String): String {
        val request = ProjectInvitationCodec.decodeJoin(encodedRequest)
        request.validate()?.let { error(it) }
        val project = projectDao.getProject(projectId) ?: error("项目不存在")
        val inviter = projectDao.getMember(projectId, identity.userId) ?: error("当前用户不是项目成员")
        require(inviter.role == "OWNER") { "只有项目创建者可以邀请成员" }
        require(inviter.publicKey == identity.publicKey.encodeBase64()) { "项目成员公钥不匹配" }
        val key = projectKeys.keyFor(identity, projectId) ?: error("项目密钥不可用")
        val now = System.currentTimeMillis()
        val newMember = ProjectMemberEntity(
            projectId, request.userId, request.displayName, request.signingPublicKey,
            request.agreementPublicKey, "MEMBER", now
        )
        val members = (projectDao.getMembers(projectId) + newMember)
            .associateBy(ProjectMemberEntity::userId)
            .values
            .sortedBy(ProjectMemberEntity::joinedAt)
            .map { it.toDescriptor() }
        val encoded = ProjectInvitationCodec.encodeInvitation(ProjectInvitationCrypto.seal(
            projectId, project.name, identity.userId, identity.publicKey, identity.privateSeed,
            request, key, members, now
        ))
        // Persist membership only after the complete invitation has been sealed.
        // A crypto/serialization failure therefore cannot leave a phantom member.
        projectDao.upsertMember(newMember)
        return encoded
    }

    suspend fun acceptInvitation(identity: KeyManager.UserIdentity, encodedInvitation: String): ProjectEntity {
        val invitation = ProjectInvitationCodec.decodeInvitation(encodedInvitation)
        val secret = ProjectInvitationCrypto.open(
            invitation, identity.userId, identity.agreementPrivateKey
        )
        val inviter = secret.members.firstOrNull { it.userId == invitation.inviterId }
            ?: error("邀请者不在成员清单中")
        require(inviter.role == "OWNER" && inviter.signingPublicKey == invitation.inviterSigningPublicKey) {
            "邀请者不是项目 OWNER 或公钥不匹配"
        }
        val self = secret.members.firstOrNull { it.userId == identity.userId }
            ?: error("当前用户不在邀请成员清单中")
        require(self.signingPublicKey == identity.publicKey.encodeBase64() &&
            self.agreementPublicKey == identity.agreementPublicKey.encodeBase64()) { "接收者身份密钥不匹配" }
        require(projectDao.getProject(invitation.projectId) == null) { "项目已存在，请使用同步功能" }
        val project = ProjectEntity(
            invitation.projectId, invitation.projectName, invitation.inviterId, invitation.createdAt
        )
        database.withTransaction {
            projectDao.insertProject(project)
            secret.members.forEach { projectDao.insertMember(it.toEntity(invitation.projectId)) }
            projectKeys.import(identity, invitation.projectId, secret.projectKey.decodeBase64())
        }
        return project
    }

    private fun ProjectMemberEntity.toDescriptor() = ProjectMemberDescriptor(
        userId, displayName, publicKey, agreementPublicKey, role, joinedAt
    )

    private fun ProjectMemberDescriptor.toEntity(projectId: String) = ProjectMemberEntity(
        projectId, userId, displayName, signingPublicKey, agreementPublicKey, role, joinedAt
    )
}
