package com.secureledger

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secureledger.core.crypto.Ed25519Manager
import com.secureledger.core.crypto.KeyManager
import com.secureledger.core.ledger.PlainRecordPayload
import com.secureledger.data.database.AppDatabase
import com.secureledger.data.repository.LedgerRepository
import java.security.SecureRandom
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShoppingBatchDeviceTest {
    @Test
    fun sameMerchantOrdersAreIndependentlySignedAndBatchRollsBackOnFailure() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<SecureLedgerApplication>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val pair = Ed25519Manager.generateKeyPair()
            val identity = KeyManager.UserIdentity("shopping_test", pair.publicKey,
                ByteArray(32).also(SecureRandom()::nextBytes), pair.privateSeed, ByteArray(32), ByteArray(32))
            val ledger = LedgerRepository(database.ledgerRecordDao(), KeyManager(context, "shopping-batch-test"), database = database)
            fun order(amount: Long) = PlainRecordPayload(
                recordType = "EXPENSE", amountCents = amount, category = "购物", merchant = "同名店铺",
                note = "模拟订单", account = "default", paymentChannel = "淘宝",
                tags = listOf("shopping-batch", "time-estimated"), transactionTime = 1_790_000_000_000L
            )
            val saved = ledger.addRecords(identity, listOf(order(6378), order(2990)))
            assertEquals(2, saved.size)
            assertEquals(2, saved.map { it.recordId }.distinct().size)
            assertTrue(ledger.verify(identity).isValid)
            val failed = runCatching { ledger.addRecords(identity, listOf(order(100), order(-1))) }
            assertTrue(failed.isFailure)
            assertEquals(2, database.ledgerRecordDao().count())
        } finally {
            database.close()
        }
    }
}
