package com.jobalert.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jobalert.data.local.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration7To8Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate7to8_addsRuleNameToAlerts_andPositionToRules() {
        helper.createDatabase("test_migration_7_8", 7).use { db ->
            db.execSQL(
                """INSERT INTO alerts (id, timestamp, sender, subject, snippet, acknowledged)
                   VALUES (1, 1700000000000, 'alertas@sistema.com', 'CRITICAL: Server DOWN', 'El servidor no responde', 0)"""
            )
            db.execSQL(
                """INSERT INTO rules (id, name, senders, subjectKeywords, bodyKeywords, isEnabled, alertColor)
                   VALUES (1, 'Prod', '[]', '[]', '["CRITICAL"]', 1, NULL)"""
            )
        }

        helper.runMigrationsAndValidate(
            "test_migration_7_8", 8, true,
            AppDatabase.MIGRATION_7_8
        ).use { db ->
            // alerts.ruleName debe existir con default vacío
            val alertCursor = db.query("SELECT ruleName FROM alerts WHERE id = 1")
            alertCursor.moveToFirst()
            assertEquals("", alertCursor.getString(0))
            alertCursor.close()

            // rules.position debe existir; inicializado con id (=1)
            val ruleCursor = db.query("SELECT position FROM rules WHERE id = 1")
            ruleCursor.moveToFirst()
            assertEquals(1, ruleCursor.getInt(0))
            ruleCursor.close()
        }
    }
}
