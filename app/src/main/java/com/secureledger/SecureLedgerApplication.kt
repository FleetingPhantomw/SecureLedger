package com.secureledger

import android.app.Application
import com.secureledger.core.crypto.KeyManager
import com.secureledger.data.database.AppDatabase
import com.secureledger.data.repository.LedgerRepository
import com.secureledger.data.repository.ProjectRepository
import com.secureledger.data.repository.SettlementRepository
import com.secureledger.data.repository.SyncRepository
import com.secureledger.data.repository.ImportRepository
import com.secureledger.integration.ocr.OfflineTextRecognizer
import com.secureledger.core.ocr.OrderParser
import com.secureledger.data.repository.ProjectKeyRepository
import com.secureledger.data.repository.InvitationRepository
import com.secureledger.integration.network.LanSyncController
import com.secureledger.integration.network.RelaySyncClient
import com.secureledger.data.repository.BackupRepository

class SecureLedgerApplication : Application() {
    val keyManager by lazy { KeyManager(this) }
    val database by lazy { AppDatabase.getInstance(this) }
    val projectKeyRepository by lazy { ProjectKeyRepository(database.projectKeyDao()) }
    val ledgerRepository by lazy { LedgerRepository(database.ledgerRecordDao(), keyManager, projectKeyRepository) }
    val projectRepository by lazy { ProjectRepository(database.projectDao(), projectKeyRepository) }
    val settlementRepository by lazy { SettlementRepository(database, projectKeyRepository) }
    val syncRepository by lazy { SyncRepository(database, projectKeyRepository) }
    val importRepository by lazy { ImportRepository(database, ledgerRepository) }
    val offlineTextRecognizer by lazy { OfflineTextRecognizer(this) }
    val orderParser by lazy { OrderParser() }
    val invitationRepository by lazy { InvitationRepository(database, projectKeyRepository) }
    val lanSyncController by lazy { LanSyncController(this) }
    val relaySyncClient by lazy { RelaySyncClient() }
    val backupRepository by lazy { BackupRepository(database, keyManager) }
}
