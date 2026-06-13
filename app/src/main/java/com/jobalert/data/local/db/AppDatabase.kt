package com.jobalert.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jobalert.data.local.entity.AlertEntity
import com.jobalert.data.local.entity.EmailAccountEntity
import com.jobalert.data.local.entity.RuleEntity

@Database(entities = [AlertEntity::class, RuleEntity::class, EmailAccountEntity::class], version = 8, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alertDao(): AlertDao
    abstract fun ruleDao(): RuleDao
    abstract fun emailAccountDao(): EmailAccountDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rules ADD COLUMN isEnabled INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS email_accounts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        email TEXT NOT NULL,
                        host TEXT NOT NULL,
                        port INTEGER NOT NULL DEFAULT 993,
                        useSsl INTEGER NOT NULL DEFAULT 1,
                        isEnabled INTEGER NOT NULL DEFAULT 1,
                        lastSeenUid INTEGER NOT NULL DEFAULT 0,
                        uidValidity INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_email_accounts_email ON email_accounts (email)")
            }
        }

        // Public so MigrationTestHelper can reference it directly
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE email_accounts ADD COLUMN authType TEXT NOT NULL DEFAULT 'PASSWORD'")
                db.execSQL("ALTER TABLE email_accounts ADD COLUMN oauthConfig TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE email_accounts ADD COLUMN needsReauth INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rules ADD COLUMN alertColor INTEGER")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS rules")
                db.execSQL("""
                    CREATE TABLE rules (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        senders TEXT NOT NULL,
                        subjectKeywords TEXT NOT NULL,
                        bodyKeywords TEXT NOT NULL,
                        isEnabled INTEGER NOT NULL DEFAULT 1,
                        alertColor INTEGER
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alerts ADD COLUMN ruleName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE rules ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE rules SET position = id")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "jobalert.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
