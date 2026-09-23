package com.secureledger

import android.app.Application
import com.secureledger.core.crypto.KeyManager
import com.secureledger.data.database.AppDatabase
import com.secureledger.data.repository.LedgerRepository
import com.secureledger.data.repository.ProjectRepository
import com.secureledger.data.repository.SettlementRepository
import com.secureledger.data.repository.SyncRepository
import com.secureledger.data.repository.ImportRepository
import com.secureledger.integration.ocr.OcrEngine
import com.secureledger.integration.ocr.PaddleOcrEngine
import com.secureledger.integration.accessibility.AccessibilityPreferences
import com.secureledger.core.ocr.OrderParser
import com.secureledger.data.repository.ProjectKeyRepository
import com.secureledger.data.repository.InvitationRepository
import com.secureledger.data.repository.BackupRepository
import com.secureledger.data.repository.BankCardRepository
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SecureLedgerApplication : Application() {
    data class HomeStartupPerformance(
        val roomOpenMs: Long,
        val repositoryInitializationMs: Long,
        val personalProjectMs: Long,
        val totalMs: Long
    )

    val keyManager by lazy { KeyManager(this) }
    val database by lazy { AppDatabase.getInstance(this) }
    val projectKeyRepository by lazy { ProjectKeyRepository(database.projectKeyDao()) }
    val ledgerRepository by lazy { LedgerRepository(database.ledgerRecordDao(), keyManager, projectKeyRepository) }
    val projectRepository by lazy { ProjectRepository(database.projectDao(), projectKeyRepository) }
    val settlementRepository by lazy { SettlementRepository(database, projectKeyRepository) }
    val syncRepository by lazy { SyncRepository(database, projectKeyRepository) }
    val importRepository by lazy { ImportRepository(database, ledgerRepository) }
    val ocrEngine: OcrEngine by lazy { PaddleOcrEngine(this) }
    val accessibilityPreferences by lazy { AccessibilityPreferences(this) }
    val orderParser by lazy { OrderParser() }
    val invitationRepository by lazy { InvitationRepository(database, projectKeyRepository) }
    val backupRepository by lazy { BackupRepository(database, keyManager) }
    val bankCardRepository by lazy { BankCardRepository(database.bankCardDao()) }

    /** Opens only the storage required by the home screen, never on the main thread. */
    suspend fun prepareHome(identity: KeyManager.UserIdentity): HomeStartupPerformance = withContext(Dispatchers.IO) {
        val totalStarted = SystemClock.elapsedRealtime()
        val roomStarted = SystemClock.elapsedRealtime()
        database.openHelper.writableDatabase
        val roomMs = SystemClock.elapsedRealtime() - roomStarted

        val repositoryStarted = SystemClock.elapsedRealtime()
        ledgerRepository
        projectRepository
        val repositoryMs = SystemClock.elapsedRealtime() - repositoryStarted

        val projectStarted = SystemClock.elapsedRealtime()
        projectRepository.ensurePersonalProject(identity)
        val projectMs = SystemClock.elapsedRealtime() - projectStarted
        HomeStartupPerformance(roomMs, repositoryMs, projectMs, SystemClock.elapsedRealtime() - totalStarted).also {
            Log.d("SecureLedgerPerf", "home room=${it.roomOpenMs}ms repositories=${it.repositoryInitializationMs}ms personalProject=${it.personalProjectMs}ms total=${it.totalMs}ms")
        }
    }
}
