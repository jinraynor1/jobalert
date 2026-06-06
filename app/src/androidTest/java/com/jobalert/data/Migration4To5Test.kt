package com.jobalert.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jobalert.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration4To5Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate4to5_preservesExistingRows_andAddsNeedsReauthDefaultFalse() {
        helper.createDatabase("test_migration_4_5", 4).use { db ->
            db.execSQL(
                """INSERT INTO email_accounts (id, email, host, port, useSsl, isEnabled, lastSeenUid, uidValidity, authType, oauthConfig)
                   VALUES (1, 'test@gmail.com', 'imap.gmail.com', 993, 1, 1, 0, 0, 'OAUTH2_GOOGLE', NULL)"""
            )
        }

        helper.runMigrationsAndValidate(
            "test_migration_4_5", 5, true,
            AppDatabase.MIGRATION_4_5
        ).use { db ->
            val cursor = db.query("SELECT email, authType, needsReauth FROM email_accounts WHERE id = 1")
            cursor.moveToFirst()
            assertEquals("test@gmail.com", cursor.getString(0))
            assertEquals("OAUTH2_GOOGLE", cursor.getString(1))
            assertEquals(0, cursor.getInt(2))  // default false
            cursor.close()
        }
    }
}
