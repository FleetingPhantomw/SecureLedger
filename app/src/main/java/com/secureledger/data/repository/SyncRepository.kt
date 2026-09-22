package com.secureledger.data.repository

import androidx.room.withTransaction
import com.secureledger.core.crypto.AesGcmManager
import com.secureledger.core.crypto.HashManager
import com.secureledger.core.crypto.KeyManager
import com.secureledger.core.crypto.decodeBase64
import com.secureledger.core.crypto.encodeBase64
import com.secureledger.core.ledger.LedgerHashChain
import com.secureledger.core.ledger.SecureRecord
import com.secureledger.core.merkle.MerkleTree
import com.secureledger.core.protocol.SecureSyncCodec
import com.secureledger.core.protocol.SecureSyncPackage
import com.secureledger.core.protocol.SyncPackageValidator
import com.secureledger.data.database.AppDatabase
import com.secureledger.data.entity.LedgerRecordEntity
import com.secureledger.data.entity.ProcessedSyncMessageEntity
import java.security.SecureRandom
import java.util.UUID

enum class SyncImportStatus { ACCEPTED, REPLAY_REJECTED, VALIDATION_REJECTED, PROJECT_UNKNOWN, KEY_INCOMPATIBLE, MALFORMED }

data class SyncImportResult(
    val status: SyncImportStatus,
    val importedRecords: Int = 0,
    val duplicateRecords: Int = 0,
    val details: List<String> = emptyList()
) {
    val accepted: Boolean get() = status == SyncImportStatus.ACCEPTED
}

/** Secure package persistence boundary shared by QR, clipboard, LAN, and relay adapters. */
class SyncRepository(
    private val database: AppDatabase,
    private val projectKeys: ProjectKeyRepository? = null
) {
    private val recordDao = database.ledgerRecordDao()
    private val projectDao = database.projectDao()
    private val replayDao = database.syncMessageDao()
    private val validator = SyncPackageValidator()
    private val random = SecureRandom()

    suspend fun export(identity: KeyManager.UserIdentity, projectId: String): String {
        require(projectDao.getProject(projectId) != null) { "项目不存在" }
        val member = projectDao.getMember(projectId, identity.userId)
        require(member?.publicKey == identity.publicKey.encodeBase64()) { "当前用户不是项目成员" }
        val records = recordDao.getAllByProject(projectId).map(LedgerRecordEntity::toDomain)
        val syncPackage = SecureSyncPackage.sign(
            protocolVersion = SyncPackageValidator.SUPPORTED_PROTOCOL,
            packageId = "pkg_${UUID.randomUUID()}",
            projectId = projectId,
            senderId = identity.userId,
            senderPublicKey = identity.publicKey,
            timestamp = System.currentTimeMillis(),
            nonce = ByteArray(16).also(random::nextBytes),
            records = records,
            merkleRoot = MerkleTree(records).calculateRoot(),
            privateSeed = identity.privateSeed
        )
        check(syncPackage.isPayloadHashValid() && syncPackage.isSignatureValid()) {
            "Generated sync package failed self-verification"
        }
        return SecureSyncCodec.encode(syncPackage)
    }

    suspend fun import(identity: KeyManager.UserIdentity, encoded: String): SyncImportResult {
        val syncPackage = runCatching { SecureSyncCodec.decode(encoded) }.getOrElse {
            return SyncImportResult(SyncImportStatus.MALFORMED, details = listOf(it.message ?: "Malformed package"))
        }
        if (projectDao.getProject(syncPackage.projectId) == null) {
            return SyncImportResult(SyncImportStatus.PROJECT_UNKNOWN, details = listOf("未知项目：${syncPackage.projectId}"))
        }
        val sender = projectDao.getMember(syncPackage.projectId, syncPackage.senderId)
        if (sender == null || sender.publicKey != syncPackage.senderPublicKey) {
            return SyncImportResult(
                SyncImportStatus.VALIDATION_REJECTED,
                details = listOf("发送者不是项目成员，或成员公钥不匹配")
            )
        }
        val validation = validator.validate(syncPackage, expectedProjectId = syncPackage.projectId)
        if (!validation.isValid) {
            return SyncImportResult(
                SyncImportStatus.VALIDATION_REJECTED,
                details = validation.issues.map { "${it.failure}: ${it.detail}" }
            )
        }
        val messageKey = HashManager.sha256Hex("${syncPackage.packageId}|${syncPackage.nonce}".toByteArray(Charsets.UTF_8))
        if (replayDao.contains(messageKey)) {
            return SyncImportResult(SyncImportStatus.REPLAY_REJECTED, details = listOf("该同步包已处理"))
        }

        val existing = recordDao.getAllByProject(syncPackage.projectId).associateBy { it.recordId }
        val conflicts = syncPackage.records.filter { incoming ->
            existing[incoming.recordId]?.toDomain()?.let { stored -> stored != incoming } == true
        }
        if (conflicts.isNotEmpty()) {
            return SyncImportResult(
                SyncImportStatus.VALIDATION_REJECTED,
                details = conflicts.map { "RECORD_CONFLICT: ${it.recordId}" }
            )
        }
        val newRecords = syncPackage.records.filterNot { existing.containsKey(it.recordId) }
        val projectKey = projectKeys?.keyFor(identity, syncPackage.projectId)
            ?: identity.masterKey.takeIf { syncPackage.projectId == ProjectRepository.PERSONAL_PROJECT_ID }
        val incompatible = newRecords.filterNot { record -> projectKey != null && canDecrypt(projectKey, record) }
        if (incompatible.isNotEmpty()) {
            return SyncImportResult(
                SyncImportStatus.KEY_INCOMPATIBLE,
                details = listOf("${incompatible.size} 条记录无法使用当前项目密钥解密，已拒绝写入")
            )
        }
        val combined = (existing.values.map(LedgerRecordEntity::toDomain) + newRecords)
        val chainIssues = combined.groupBy { it.authorId }.values.flatMap(LedgerHashChain::validate)
        if (chainIssues.isNotEmpty()) {
            return SyncImportResult(
                SyncImportStatus.VALIDATION_REJECTED,
                details = chainIssues.map { "${it.recordId}: ${it.failure}" }
            )
        }

        database.withTransaction {
            if (replayDao.contains(messageKey)) error("Replay detected during transaction")
            if (newRecords.isNotEmpty()) recordDao.insertAll(newRecords.map(LedgerRecordEntity::fromDomain))
            replayDao.insert(
                ProcessedSyncMessageEntity(
                    messageKey = messageKey,
                    packageId = syncPackage.packageId,
                    nonce = syncPackage.nonce,
                    senderId = syncPackage.senderId,
                    processedAt = System.currentTimeMillis()
                )
            )
        }
        return SyncImportResult(
            status = SyncImportStatus.ACCEPTED,
            importedRecords = newRecords.size,
            duplicateRecords = syncPackage.records.size - newRecords.size,
            details = listOf("签名、哈希链、Merkle Root 与防重放检查均通过")
        )
    }

    private fun canDecrypt(encryptionKey: ByteArray, record: SecureRecord): Boolean = runCatching {
        AesGcmManager.decrypt(
            encryptionKey,
            AesGcmManager.Ciphertext(record.nonce.decodeBase64(), record.encryptedPayload.decodeBase64()),
            listOf(record.recordId, record.authorId, record.projectId, record.recordType, record.schemaVersion)
                .joinToString("|").toByteArray(Charsets.UTF_8)
        )
    }.isSuccess
}
