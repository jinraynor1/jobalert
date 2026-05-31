package com.jobalert.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jobalert.data.model.AlertEntity
import com.jobalert.data.model.EmailAccount
import com.jobalert.domain.Rule

@Database(entities = [AlertEntity::class, Rule::class, EmailAccount::class], version = 4, exportSchema = false)
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

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "jobalert.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
