package com.secureledger.data.repository

import androidx.room.withTransaction
import com.secureledger.core.backup.PortableBackupCodec
import com.secureledger.core.backup.PortableBackupPayload
import com.secureledger.core.backup.PortableVaultIdentity
import com.secureledger.core.crypto.AesGcmManager
import com.secureledger.core.crypto.HashManager
import com.secureledger.core.crypto.KeyManager
import com.secureledger.core.crypto.decodeBase64
import com.secureledger.core.crypto.encodeBase64
import com.secureledger.core.ledger.IntegrityVerifier
import com.secureledger.data.database.AppDatabase
import com.secureledger.data.entity.LedgerRecordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Exports and restores complete vault state without ever writing plaintext key material to disk. */
class BackupRepository(
    private val database: AppDatabase,
    private val keyManager: KeyManager
) {
    suspend fun export(identity: KeyManager.UserIdentity, backupPassword: CharArray): ByteArray {
        val storedProjectKeys = database.projectKeyDao().getAll()
        val clearProjectKeys = storedProjectKeys.associate { it.projectId to decryptProjectKey(identity.masterKey, it) }
        val report = IntegrityVerifier().verifyWithKeys(
            database.ledgerRecordDao().getAll().map(LedgerRecordEntity::toDomain)
        ) { projectId ->
            if (projectId == ProjectRepository.PERSONAL_PROJECT_ID) identity.masterKey
            else clearProjectKeys[projectId]
        }
        require(report.isValid) { "账本完整性验证失败，已拒绝备份" }
        val payload = PortableBackupPayload(
            identity = PortableVaultIdentity(
                identity.userId, identity.publicKey.encodeBase64(), identity.privateSeed.encodeBase64(),
                identity.masterKey.encodeBase64(), identity.agreementPublicKey.encodeBase64(),
                identity.agreementPrivateKey.encodeBase64()
            ),
            records = database.ledgerRecordDao().getAll().map(LedgerRecordEntity::toDomain),
            projects = database.projectDao().getAllProjects(),
            members = database.projectDao().getAllMembers(),
            projectKeys = storedProjectKeys,
            settlements = database.settlementDao().getAll(),
            importBatches = database.importDao().getBatches(),
            importedTransactions = database.importDao().getTransactions(),
            replayMarkers = database.syncMessageDao().getAll(),
            bankCards = database.bankCardDao().getAll()
        )
        return PortableBackupCodec.encode(identity, payload, backupPassword)
    }

    suspend fun restore(
        bytes: ByteArray,
        backupPassword: CharArray,
        newVaultPassword: CharArray
    ): KeyManager.UserIdentity {
        require(!keyManager.isInitialized()) { "恢复仅允许在尚未创建的保险库执行" }
        require(database.projectDao().getAllProjects().isEmpty() && database.ledgerRecordDao().count() == 0 &&
            database.importDao().batchCount() == 0) { "本地数据库不是空的，已拒绝覆盖" }
        val payload = PortableBackupCodec.decode(bytes, backupPassword)
        validatePayload(payload)
        val material = payload.identity
        database.withTransaction {
            if (payload.projects.isNotEmpty()) database.projectDao().insertProjects(payload.projects)
            if (payload.members.isNotEmpty()) database.projectDao().insertMembers(payload.members)
            if (payload.projectKeys.isNotEmpty()) database.projectKeyDao().insertAll(payload.projectKeys)
            if (payload.records.isNotEmpty()) database.ledgerRecordDao().insertAll(payload.records.map(LedgerRecordEntity::fromDomain))
            if (payload.settlements.isNotEmpty()) database.settlementDao().insertAll(payload.settlements)
            if (payload.importBatches.isNotEmpty()) database.importDao().insertBatches(payload.importBatches)
            if (payload.importedTransactions.isNotEmpty()) database.importDao().insertTransactions(payload.importedTransactions)
            if (payload.replayMarkers.isNotEmpty()) database.syncMessageDao().insertAll(payload.replayMarkers)
            if (payload.bankCards.isNotEmpty()) database.bankCardDao().insertAll(payload.bankCards)
        }
        return try {
            // Commit the database first. If device-key wrapping fails, restore the
            // precondition (an empty vault) so the user can safely retry.
            keyManager.restoreUser(
                newVaultPassword, material.userId, material.signingPublicKey.decodeBase64(),
                material.masterKey.decodeBase64(), material.signingPrivateSeed.decodeBase64(),
                material.agreementPublicKey.decodeBase64(), material.agreementPrivateKey.decodeBase64()
            )
        } catch (failure: Throwable) {
            withContext(Dispatchers.IO) { database.clearAllTables() }
            throw failure
        }
    }

    private fun validatePayload(payload: PortableBackupPayload) {
        val identity = payload.identity
        val masterKey = identity.masterKey.decodeBase64()
        val signingPublic = identity.signingPublicKey.decodeBase64()
        require("u_" + HashManager.sha256Hex(signingPublic).take(16) == identity.userId) { "备份用户 ID 无效" }
        val projectIds = payload.projects.map { it.projectId }.toSet()
        require(projectIds.size == payload.projects.size) { "备份包含重复项目" }
        require(payload.members.all { it.projectId in projectIds } && payload.records.all { it.projectId in projectIds }) {
            "备份包含悬空项目引用"
        }
        val projectKeys = payload.projectKeys.associate { entity -> entity.projectId to decryptProjectKey(masterKey, entity) }
        val report = IntegrityVerifier().verifyWithKeys(payload.records) { projectId ->
            if (projectId == ProjectRepository.PERSONAL_PROJECT_ID) masterKey else projectKeys[projectId] ?: masterKey
        }
        require(report.isValid) { "备份内账本完整性验证失败：${report.issues.firstOrNull()?.detail}" }
    }

    private fun decryptProjectKey(masterKey: ByteArray, entity: com.secureledger.data.entity.ProjectKeyEntity): ByteArray {
        val key = AesGcmManager.decrypt(
            masterKey,
            AesGcmManager.Ciphertext(entity.nonce.decodeBase64(), entity.encryptedKey.decodeBase64()),
            "SecureLedger/project-key/v1/${entity.projectId}".toByteArray(Charsets.UTF_8)
        )
        require(HashManager.sha256Hex(key) == entity.keyFingerprint) { "项目密钥指纹不匹配" }
        return key
    }
}
