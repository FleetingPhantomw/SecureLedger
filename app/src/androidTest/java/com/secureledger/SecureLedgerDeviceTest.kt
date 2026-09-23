package com.secureledger

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.Room
import com.secureledger.core.crypto.KeyManager
import com.secureledger.core.crypto.AesGcmManager
import com.secureledger.core.crypto.encodeBase64
import com.secureledger.core.crypto.Ed25519Manager
import com.secureledger.core.crypto.HashManager
import com.secureledger.core.ledger.CorrectionRequest
import com.secureledger.core.ledger.IntegrityFailure
import com.secureledger.core.ledger.PlainRecordPayload
import com.secureledger.data.database.AppDatabase
import com.secureledger.data.repository.LedgerRepository
import com.secureledger.data.repository.ImportRepository
import com.secureledger.data.repository.ProjectRepository
import com.secureledger.data.repository.SyncImportStatus
import com.secureledger.data.repository.SyncRepository
import com.secureledger.data.repository.BackupRepository
import com.secureledger.data.repository.ProjectKeyRepository
import com.secureledger.data.repository.InvitationRepository
import com.secureledger.data.repository.SettlementRepository
import com.secureledger.core.merkle.MerkleTree
import com.secureledger.core.importer.DataSourceType
import com.secureledger.core.importer.DuplicateLevel
import com.secureledger.core.importer.ImportedTransaction
import com.secureledger.core.importer.ReconciliationStatus
import com.secureledger.core.importer.TransactionDirection
import com.secureledger.data.entity.ImportBatchEntity
import com.secureledger.data.entity.ImportedTransactionEntity
import com.secureledger.core.settlement.Balance
import com.secureledger.core.protocol.SecureSyncCodec
import com.secureledger.core.protocol.SecureSyncPackage
import androidx.camera.lifecycle.ProcessCameraProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/** Runs the complete first milestone against the real Android Keystore and Room database. */
@RunWith(AndroidJUnit4::class)
class SecureLedgerDeviceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AppDatabase

    @Test
    fun vaultUnlockIsProfiledOffMainThread() = runBlocking {
        context.getSharedPreferences(PERFORMANCE_VAULT, Context.MODE_PRIVATE).edit().clear().commit()
        val keyManager = KeyManager(context, PERFORMANCE_NAMESPACE)
        val createStarted = SystemClock.elapsedRealtime()
        withContext(Dispatchers.Default) {
            assertTrue(Looper.myLooper() != Looper.getMainLooper())
            keyManager.createUser("PerformanceTestPassword123".toCharArray())
        }
        val createMs = SystemClock.elapsedRealtime() - createStarted
        val result = withContext(Dispatchers.Default) {
            assertTrue(Looper.myLooper() != Looper.getMainLooper())
            keyManager.unlockProfiled("PerformanceTestPassword123".toCharArray())
        }
        assertTrue(result.performance.argon2Ms > 0)
        assertTrue(result.performance.totalMs >= result.performance.argon2Ms)
        Log.i(
            "SecureLedgerPerfTest",
            "create=${createMs}ms unlock=${result.performance.totalMs}ms " +
                "keystore=${result.performance.keystoreUnwrapMs}ms argon2=${result.performance.argon2Ms}ms " +
                "masterDecrypt=${result.performance.masterKeyDecryptMs}ms " +
                "identity=${result.performance.identityDecryptMs}ms agreement=${result.performance.agreementIdentityMs}ms"
        )
        Unit
    }

    @Test
    fun encryptPersistReloadAndDetectDatabaseTamper() = runBlocking {
        context.getSharedPreferences(TEST_VAULT, Context.MODE_PRIVATE).edit().clear().commit()
        val keyManager = KeyManager(context, TEST_NAMESPACE)
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val projectKeys = ProjectKeyRepository(database.projectKeyDao())
        val repository = LedgerRepository(database.ledgerRecordDao(), keyManager, projectKeys)
        val identity = keyManager.createUser("DeviceTestPassword123".toCharArray())
        val projectRepository = ProjectRepository(database.projectDao(), projectKeys)
        projectRepository.ensurePersonalProject(identity)
        val transactionTime = System.currentTimeMillis()
        val record = repository.addRecord(identity, PlainRecordPayload(
            recordType = "EXPENSE",
            amountCents = 2350,
            category = "餐饮",
            merchant = "真机测试",
            note = "Android Keystore + Room",
            account = "default",
            paymentChannel = "instrumented",
            transactionTime = transactionTime
        ))

        assertTrue(repository.verify(identity).isValid)

        // A pre-existing encrypted statement row remains readable after CSV ingestion is removed.
        val legacy = ImportedTransaction(
            sourceType = DataSourceType.BANK.name, amountCents = 2350,
            direction = TransactionDirection.DEBIT.name, merchant = "真机测试",
            transactionTime = transactionTime, paymentChannel = "银行卡", category = "餐饮",
            rawDescription = "历史银行流水", sourceRow = 2
        )
        val importedId = "legacy_import_1"
        val batchId = "legacy_batch_1"
        val clear = "{\"transaction\":${Json.encodeToString(legacy)}}".toByteArray()
        val encrypted = AesGcmManager.encrypt(
            identity.masterKey, clear,
            "$importedId|$batchId|${legacy.sourceType}|1".toByteArray()
        )
        database.importDao().insertBatch(ImportBatchEntity(batchId, legacy.sourceType, "历史记录", transactionTime, 1, 0))
        database.importDao().insertTransactions(listOf(ImportedTransactionEntity(
            importedId, batchId, legacy.sourceType, encrypted.bytes.encodeBase64(),
            encrypted.nonce.encodeBase64(), HashManager.sha256Hex(encrypted.bytes),
            "legacy_fingerprint", DuplicateLevel.NOT_DUPLICATE.name,
            ReconciliationStatus.MATCHED.name, record.recordId, "已匹配", transactionTime
        )))
        val imported = ImportRepository(database, repository).observeTransactions(identity).first().single()
        assertTrue(imported.transaction.merchant == "真机测试")
        assertTrue(imported.matchedRecordId == record.recordId)
        assertTrue(!database.importDao().getTransactions().single().encryptedPayload.contains("真机测试"))

        val groupProject = projectRepository.createProject(identity, name = "真机项目密钥测试")
        repository.addRecord(identity, PlainRecordPayload(
            recordType = "EXPENSE", amountCents = 1200, category = "测试", merchant = "项目账目",
            note = "独立项目 AES 密钥", account = "default", paymentChannel = "instrumented",
            transactionTime = transactionTime
        ), groupProject.projectId)
        assertTrue(repository.verify(identity).isValid)

        repository.addCorrection(identity, CorrectionRequest(
            correctionId = "c_device_test",
            targetRecordId = record.recordId,
            oldValue = "23.50",
            newValue = "23.00",
            reason = "真机更正测试",
            createdAt = System.currentTimeMillis()
        ))
        val visible = repository.observeRecords(identity).first()
        assertTrue(visible.single { it.secure.recordId == record.recordId }.payload.amountCents == 2300L)
        assertTrue(visible.single { it.secure.recordId == record.recordId }.correctionHistory.size == 1)
        assertTrue(runCatching {
            repository.addCorrection(identity, CorrectionRequest(
                correctionId = "c_stale_test", targetRecordId = record.recordId,
                oldValue = "23.50", newValue = "22.00", reason = "过期旧值", createdAt = System.currentTimeMillis()
            ))
        }.isFailure)
        assertTrue(repository.verify(identity).isValid)

        val syncRepository = SyncRepository(database, projectKeys)
        val encoded = syncRepository.export(identity, "personal")
        val accepted = syncRepository.import(identity, encoded)
        assertTrue(accepted.status == SyncImportStatus.ACCEPTED)
        assertTrue(accepted.duplicateRecords == 2)
        val replay = syncRepository.import(identity, encoded)
        assertTrue(replay.status == SyncImportStatus.REPLAY_REJECTED)

        val backupBytes = BackupRepository(database, keyManager)
            .export(identity, "PortableBackupPassword123".toCharArray())
        context.getSharedPreferences(RESTORE_VAULT, Context.MODE_PRIVATE).edit().clear().commit()
        val restoreKeyManager = KeyManager(context, RESTORE_NAMESPACE)
        val restoredDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val restoredIdentity = BackupRepository(restoredDatabase, restoreKeyManager).restore(
            backupBytes, "PortableBackupPassword123".toCharArray(), "RestoredVaultPassword123".toCharArray()
        )
        val restoredProjectKeys = ProjectKeyRepository(restoredDatabase.projectKeyDao())
        val restoredLedger = LedgerRepository(restoredDatabase.ledgerRecordDao(), restoreKeyManager, restoredProjectKeys)
        assertTrue(restoredIdentity.userId == identity.userId)
        assertTrue(restoredLedger.verify(restoredIdentity).isValid)
        assertTrue(restoredDatabase.importDao().getTransactions().size == 1)
        restoredDatabase.close()

        // Simulate the course-defense attack: modify the immutable row outside the repository.
        database.openHelper.writableDatabase.execSQL(
            "UPDATE ledger_records SET encryptedPayload = ? WHERE recordId = ?",
            arrayOf("tampered-ciphertext", record.recordId)
        )
        val report = repository.verify(identity)
        assertTrue(report.issues.any { it.failure == IntegrityFailure.HASH_MISMATCH })
        assertTrue(report.issues.any { it.failure == IntegrityFailure.SIGNATURE_INVALID })
        assertTrue(report.issues.any { it.failure == IntegrityFailure.DECRYPTION_FAILED })
    }

    @Test
    fun projectInvitationSyncAndIndependentSettlementCosignWorkEndToEnd() = runBlocking {
        context.getSharedPreferences(TEST_VAULT, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(MEMBER_VAULT, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(PERFORMANCE_VAULT, Context.MODE_PRIVATE).edit().clear().commit()
        val ownerKeys = KeyManager(context, TEST_NAMESPACE)
        val memberKeys = KeyManager(context, MEMBER_NAMESPACE)
        val owner = ownerKeys.createUser("OwnerDevicePassword123".toCharArray())
        val member = memberKeys.createUser("MemberDevicePassword123".toCharArray())

        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val memberDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val ownerProjectKeys = ProjectKeyRepository(database.projectKeyDao())
            val ownerProjects = ProjectRepository(database.projectDao(), ownerProjectKeys)
            val project = ownerProjects.createProject(owner, name = "双设备会签测试")
            val ownerLedger = LedgerRepository(database.ledgerRecordDao(), ownerKeys, ownerProjectKeys)
            ownerLedger.addRecord(owner, PlainRecordPayload(
                recordType = "EXPENSE", amountCents = 8800, category = "测试", merchant = "会签项目账目",
                note = "", account = "default", paymentChannel = "instrumented",
                transactionTime = System.currentTimeMillis()
            ), project.projectId)

            val ownerInvitations = InvitationRepository(database, ownerProjectKeys)
            val memberProjectKeys = ProjectKeyRepository(memberDatabase.projectKeyDao())
            val memberInvitations = InvitationRepository(memberDatabase, memberProjectKeys)
            val join = memberInvitations.createJoinRequest(member, "成员二")
            val invitation = ownerInvitations.issueInvitation(owner, project.projectId, join)
            memberInvitations.acceptInvitation(member, invitation)

            val ownerSync = SyncRepository(database, ownerProjectKeys)
            val memberSync = SyncRepository(memberDatabase, memberProjectKeys)
            assertTrue(memberSync.import(member, ownerSync.export(owner, project.projectId)).accepted)

            val ownerSettlements = SettlementRepository(database, ownerProjectKeys)
            val memberSettlements = SettlementRepository(memberDatabase, memberProjectKeys)
            val proof = ownerSettlements.createAndSave(
                owner, project.projectId, ownerLedger.projectMerkleRoot(project.projectId),
                listOf(Balance(owner.userId, 4400), Balance(member.userId, -4400))
            )
            val coSigned = memberSettlements.coSign(member, ownerSettlements.encodeForApproval(proof))
            val merged = ownerSettlements.mergeSignatures(owner, coSigned)
            assertTrue(merged.verifyHash() && merged.verifySignatures())
            assertTrue(merged.signatures.map { it.signerId }.toSet() == setOf(owner.userId, member.userId))
        } finally {
            memberDatabase.close()
        }
    }

    @Test
    fun qrTransportAndScannerAreAvailableOnDevice() {
        val pair = Ed25519Manager.generateKeyPair()
        val senderId = "u_" + HashManager.sha256Hex(pair.publicKey).take(16)
        val syncPackage = SecureSyncPackage.sign(
            1,
            "pkg_qr_device",
            "personal",
            senderId,
            pair.publicKey,
            System.currentTimeMillis(),
            ByteArray(16) { it.toByte() },
            emptyList(),
            MerkleTree(emptyList()).calculateRoot(),
            pair.privateSeed
        )
        val encoded = SecureSyncCodec.encode(syncPackage)
        val matrix = QRCodeWriter().encode(encoded, BarcodeFormat.QR_CODE, 400, 400)
        assertTrue(matrix.width == 400 && matrix.height == 400)
        assertTrue(SecureSyncCodec.decode(encoded).isSignatureValid())

        val scanIntent = ScanContract().createIntent(
            context,
            ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        )
        assertNotNull(context.packageManager.resolveActivity(scanIntent, 0))
    }

    @Test
    fun cameraXIsAvailableOnDevice() {
        assertNotNull(ProcessCameraProvider.getInstance(context).get(10, TimeUnit.SECONDS))
    }

    @After
    fun clearTestVault() {
        if (::database.isInitialized) database.close()
        context.getSharedPreferences(TEST_VAULT, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(RESTORE_VAULT, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(MEMBER_VAULT, Context.MODE_PRIVATE).edit().clear().commit()
    }

    companion object {
        private const val TEST_NAMESPACE = "device_test"
        private const val TEST_VAULT = "secure_vault.$TEST_NAMESPACE"
        private const val RESTORE_NAMESPACE = "restore_test"
        private const val RESTORE_VAULT = "secure_vault.$RESTORE_NAMESPACE"
        private const val MEMBER_NAMESPACE = "member_test"
        private const val MEMBER_VAULT = "secure_vault.$MEMBER_NAMESPACE"
        private const val PERFORMANCE_NAMESPACE = "performance_test"
        private const val PERFORMANCE_VAULT = "secure_vault.$PERFORMANCE_NAMESPACE"
    }
}
