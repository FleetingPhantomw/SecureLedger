package com.secureledger

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secureledger.core.crypto.Ed25519Manager
import com.secureledger.core.crypto.KeyManager
import com.secureledger.core.importer.ImportedTransaction
import com.secureledger.core.importer.ReconciliationStatus
import com.secureledger.data.database.AppDatabase
import com.secureledger.data.repository.ImportRepository
import com.secureledger.data.repository.LedgerRepository
import java.security.SecureRandom
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportBatchDeviceTest {
    @Test
    fun bulkImportKeepsDuplicatesForUserDecisionWithoutEditingLedger() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<SecureLedgerApplication>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val pair = Ed25519Manager.generateKeyPair()
            val identity = KeyManager.UserIdentity(
                "import_test", pair.publicKey, ByteArray(32).also(SecureRandom()::nextBytes),
                pair.privateSeed, ByteArray(32), ByteArray(32)
            )
            val ledger = LedgerRepository(database.ledgerRecordDao(), KeyManager(context, "import-batch-test"))
            val repository = ImportRepository(database, ledger)
            val transaction = ImportedTransaction(
                sourceType = "BANK", amountCents = 1575, direction = "DEBIT",
                merchant = "示例商店", transactionTime = 1_790_000_000_000L,
                paymentChannel = "银行卡", category = "购物", rawDescription = "本机模拟流水", sourceRow = 1
            )
            assertEquals(2, repository.importBatch(identity, "模拟银行截图", listOf(transaction, transaction.copy(sourceRow = 2))))
            val imported = repository.observeTransactions(identity).first()
            assertEquals(2, imported.size)
            assertEquals(1, imported.count { it.status == ReconciliationStatus.DUPLICATE_REVIEW })
            assertEquals(0, database.ledgerRecordDao().getAll().size)
            assertFalse(database.importDao().getTransactions().any { it.encryptedPayload.contains("示例商店") })
            val duplicateId = imported.first { it.status == ReconciliationStatus.DUPLICATE_REVIEW }.importedId
            repository.retain(duplicateId)
            assertTrue(repository.observeTransactions(identity).first().any {
                it.importedId == duplicateId && it.status == ReconciliationStatus.RETAINED
            })
            repository.ignore(duplicateId)
            assertTrue(repository.observeTransactions(identity).first().any {
                it.importedId == duplicateId && it.status == ReconciliationStatus.IGNORED
            })
            assertEquals(2, database.importDao().getTransactions().size)
        } finally {
            database.close()
        }
    }
}
