package com.secureledger.data.repository

import com.secureledger.core.crypto.AesGcmManager
import com.secureledger.core.crypto.HashManager
import com.secureledger.core.crypto.KeyManager
import com.secureledger.core.crypto.decodeBase64
import com.secureledger.core.crypto.encodeBase64
import com.secureledger.data.dao.ProjectKeyDao
import com.secureledger.data.entity.ProjectKeyEntity
import java.security.SecureRandom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Stores an independent random AES-256 key per multi-user project. */
class ProjectKeyRepository(private val dao: ProjectKeyDao) {
    private val random = SecureRandom()

    suspend fun create(identity: KeyManager.UserIdentity, projectId: String): ByteArray {
        require(projectId != ProjectRepository.PERSONAL_PROJECT_ID) { "Personal ledger uses the vault master key" }
        check(dao.get(projectId) == null) { "Project key already exists" }
        return ByteArray(32).also { random.nextBytes(it); import(identity, projectId, it) }
    }

    suspend fun import(identity: KeyManager.UserIdentity, projectId: String, projectKey: ByteArray) {
        require(projectKey.size == 32) { "Project key must be 256 bits" }
        val encrypted = AesGcmManager.encrypt(identity.masterKey, projectKey, aad(projectId))
        dao.insert(ProjectKeyEntity(
            projectId, encrypted.bytes.encodeBase64(), encrypted.nonce.encodeBase64(),
            HashManager.sha256Hex(projectKey), System.currentTimeMillis()
        ))
    }

    suspend fun keyFor(identity: KeyManager.UserIdentity, projectId: String): ByteArray? =
        if (projectId == ProjectRepository.PERSONAL_PROJECT_ID) identity.masterKey else dao.get(projectId)?.let { decrypt(identity, it) }

    fun observeKeys(identity: KeyManager.UserIdentity): Flow<Map<String, ByteArray>> = dao.observeAll().map { keys ->
        keys.associate { it.projectId to decrypt(identity, it) } +
            (ProjectRepository.PERSONAL_PROJECT_ID to identity.masterKey)
    }

    private fun decrypt(identity: KeyManager.UserIdentity, entity: ProjectKeyEntity): ByteArray {
        val key = AesGcmManager.decrypt(
            identity.masterKey,
            AesGcmManager.Ciphertext(entity.nonce.decodeBase64(), entity.encryptedKey.decodeBase64()),
            aad(entity.projectId)
        )
        check(HashManager.sha256Hex(key) == entity.keyFingerprint) { "Project key fingerprint mismatch" }
        return key
    }

    private fun aad(projectId: String) = "SecureLedger/project-key/v1/$projectId".toByteArray(Charsets.UTF_8)
}
