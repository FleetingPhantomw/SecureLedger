package com.secureledger.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.secureledger.data.dao.LedgerRecordDao
import com.secureledger.data.dao.ProjectDao
import com.secureledger.data.entity.LedgerRecordEntity
import com.secureledger.data.entity.ProjectEntity
import com.secureledger.data.entity.ProjectMemberEntity
import com.secureledger.data.entity.SettlementEntity
import com.secureledger.data.entity.ProcessedSyncMessageEntity
import com.secureledger.data.entity.ImportBatchEntity
import com.secureledger.data.entity.ImportedTransactionEntity
import com.secureledger.data.entity.ProjectKeyEntity
import com.secureledger.data.entity.BankCardEntity

@Database(
    entities = [
        LedgerRecordEntity::class,
        ProjectEntity::class,
        ProjectMemberEntity::class,
        SettlementEntity::class,
        ProcessedSyncMessageEntity::class,
        ImportBatchEntity::class,
        ImportedTransactionEntity::class,
        ProjectKeyEntity::class,
        BankCardEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ledgerRecordDao(): LedgerRecordDao
    abstract fun projectDao(): ProjectDao
    abstract fun settlementDao(): com.secureledger.data.dao.SettlementDao
    abstract fun syncMessageDao(): com.secureledger.data.dao.SyncMessageDao
    abstract fun importDao(): com.secureledger.data.dao.ImportDao
    abstract fun projectKeyDao(): com.secureledger.data.dao.ProjectKeyDao
    abstract fun bankCardDao(): com.secureledger.data.dao.BankCardDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "secureledger.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
                .also { instance = it }
        }

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS projects (projectId TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, createdBy TEXT NOT NULL, createdAt INTEGER NOT NULL, status TEXT NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS project_members (projectId TEXT NOT NULL, userId TEXT NOT NULL, displayName TEXT NOT NULL, publicKey TEXT NOT NULL, role TEXT NOT NULL, joinedAt INTEGER NOT NULL, PRIMARY KEY(projectId, userId), FOREIGN KEY(projectId) REFERENCES projects(projectId) ON UPDATE NO ACTION ON DELETE CASCADE)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_project_members_projectId ON project_members(projectId)")
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS settlements (settlementId TEXT NOT NULL PRIMARY KEY, projectId TEXT NOT NULL, createdAt INTEGER NOT NULL, proofHash TEXT NOT NULL, nonce TEXT NOT NULL, encryptedProof TEXT NOT NULL)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_settlements_projectId ON settlements(projectId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_settlements_createdAt ON settlements(createdAt)")
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS processed_sync_messages (messageKey TEXT NOT NULL PRIMARY KEY, packageId TEXT NOT NULL, nonce TEXT NOT NULL, senderId TEXT NOT NULL, processedAt INTEGER NOT NULL)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_processed_sync_messages_packageId ON processed_sync_messages(packageId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_processed_sync_messages_processedAt ON processed_sync_messages(processedAt)")
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS import_batches (batchId TEXT NOT NULL PRIMARY KEY, sourceType TEXT NOT NULL, displayName TEXT NOT NULL, importedAt INTEGER NOT NULL, acceptedRows INTEGER NOT NULL, rejectedRows INTEGER NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS imported_transactions (importedId TEXT NOT NULL PRIMARY KEY, batchId TEXT NOT NULL, sourceType TEXT NOT NULL, encryptedPayload TEXT NOT NULL, nonce TEXT NOT NULL, payloadHash TEXT NOT NULL, businessFingerprint TEXT NOT NULL, duplicateLevel TEXT NOT NULL, status TEXT NOT NULL, matchedRecordId TEXT, explanation TEXT NOT NULL, importedAt INTEGER NOT NULL, FOREIGN KEY(batchId) REFERENCES import_batches(batchId) ON UPDATE NO ACTION ON DELETE CASCADE)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_imported_transactions_batchId ON imported_transactions(batchId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_imported_transactions_businessFingerprint ON imported_transactions(businessFingerprint)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_imported_transactions_status ON imported_transactions(status)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_imported_transactions_importedAt ON imported_transactions(importedAt)")
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE project_members ADD COLUMN agreementPublicKey TEXT NOT NULL DEFAULT ''")
                database.execSQL("CREATE TABLE IF NOT EXISTS project_keys (projectId TEXT NOT NULL PRIMARY KEY, encryptedKey TEXT NOT NULL, nonce TEXT NOT NULL, keyFingerprint TEXT NOT NULL, addedAt INTEGER NOT NULL, FOREIGN KEY(projectId) REFERENCES projects(projectId) ON UPDATE NO ACTION ON DELETE CASCADE)")
            }
        }

        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS bank_cards (id TEXT NOT NULL PRIMARY KEY, bankName TEXT NOT NULL, cardAlias TEXT NOT NULL, lastFourDigits TEXT NOT NULL, enabled INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_bank_cards_enabled ON bank_cards(enabled)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_bank_cards_createdAt ON bank_cards(createdAt)")
            }
        }
    }
}
