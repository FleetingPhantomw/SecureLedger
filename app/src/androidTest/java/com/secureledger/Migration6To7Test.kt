package com.secureledger

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secureledger.data.database.AppDatabase
import com.secureledger.data.repository.BankCardRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

/** Verifies the additive v6→v7 schema change without deleting any existing table. */
@RunWith(AndroidJUnit4::class)
class Migration6To7Test {
    @Test fun bankCardCrudStoresOnlySafeReferenceData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val room = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repository = BankCardRepository(room.bankCardDao())
            repository.save(null, "招商银行", "工资卡", "4821")
            val created = room.bankCardDao().getAll().single()
            assertEquals("招商银行 · 工资卡 · 4821", created.displaySnapshot)

            repository.save(created.id, "中国银行", "日常", "1023")
            val updated = room.bankCardDao().get(created.id)!!
            assertEquals("中国银行 · 日常 · 1023", updated.displaySnapshot)
            repository.setEnabled(updated, false)
            assertFalse(room.bankCardDao().get(created.id)!!.enabled)
            repository.delete(updated.copy(enabled = false))
            assertTrue(room.bankCardDao().getAll().isEmpty())
        } finally {
            room.close()
        }
    }

    @Test fun migrationCreatesValidatedBankCardTableAndIndexes() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        val database = helper.writableDatabase
        try {
            database.execSQL("CREATE TABLE sentinel (id INTEGER NOT NULL PRIMARY KEY)")
            database.execSQL("INSERT INTO sentinel(id) VALUES (7)")
            AppDatabase.MIGRATION_6_7.migrate(database)

            database.query("PRAGMA table_info(bank_cards)").use { cursor ->
                val names = buildSet {
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) add(cursor.getString(nameIndex))
                }
                assertEquals(setOf("id", "bankName", "cardAlias", "lastFourDigits", "enabled", "createdAt"), names)
            }
            database.query("SELECT id FROM sentinel").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(7, cursor.getInt(0))
            }
            database.query("PRAGMA index_list(bank_cards)").use { cursor ->
                val indexes = buildSet {
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) add(cursor.getString(nameIndex))
                }
                assertTrue("index_bank_cards_enabled" in indexes)
                assertTrue("index_bank_cards_createdAt" in indexes)
            }
        } finally {
            helper.close()
        }
    }
}
