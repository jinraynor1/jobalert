package com.jobalert.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jobalert.data.local.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration3To4Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate3to4_preservesExistingRows_andAddsDefaultAuthType() {
        helper.createDatabase("test_migration", 3).use { db ->
            db.execSQL(
                """INSERT INTO email_accounts (id, email, host, port, useSsl, isEnabled, lastSeenUid, uidValidity)
                   VALUES (1, 'test@example.com', 'imap.example.com', 993, 1, 1, 0, 0)"""
            )
        }

        helper.runMigrationsAndValidate(
            "test_migration", 4, true,
            AppDatabase.MIGRATION_3_4
        ).use { db ->
            val cursor = db.query("SELECT authType, oauthConfig FROM email_accounts WHERE id = 1")
            cursor.moveToFirst()
            assertEquals("PASSWORD", cursor.getString(0))
            assertNull(cursor.getString(1))
            cursor.close()
        }
    }
}
