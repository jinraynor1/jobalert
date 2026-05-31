package com.jobalert.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jobalert.data.db.AppDatabase
import com.jobalert.data.db.AlertDao
import com.jobalert.data.model.AlertEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlertDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: AlertDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.alertDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAlert_thenRetrieveIt() = runBlocking {
        val alert = AlertEntity(
            timestamp = 1000L,
            sender = "test@ejemplo.com",
            subject = "CRITICAL: Servidor caído",
            snippet = "El servidor no responde"
        )
        val insertedId = dao.insert(alert)

        val alerts = dao.getAllAlerts().first()
        assertEquals(1, alerts.size)
        assertEquals(insertedId, alerts[0].id)
        assertEquals("CRITICAL: Servidor caído", alerts[0].subject)
        assertFalse(alerts[0].acknowledged)
    }

    @Test
    fun acknowledgeAlert_updatesFlag() = runBlocking {
        val id = dao.insert(
            AlertEntity(timestamp = 2000L, sender = "a@b.com", subject = "S", snippet = "N")
        )
        dao.acknowledge(id)

        val alerts = dao.getAllAlerts().first()
        assertTrue(alerts[0].acknowledged)
    }

    @Test
    fun alerts_orderedByTimestampDesc() = runBlocking {
        dao.insert(AlertEntity(timestamp = 1000L, sender = "a@b.com", subject = "Primero", snippet = ""))
        dao.insert(AlertEntity(timestamp = 3000L, sender = "a@b.com", subject = "Tercero", snippet = ""))
        dao.insert(AlertEntity(timestamp = 2000L, sender = "a@b.com", subject = "Segundo", snippet = ""))

        val alerts = dao.getAllAlerts().first()
        assertEquals("Tercero", alerts[0].subject)
        assertEquals("Segundo", alerts[1].subject)
        assertEquals("Primero", alerts[2].subject)
    }

    @Test
    fun getUnacknowledgedCount_reflectsAcknowledgedState() = runBlocking {
        dao.insert(AlertEntity(timestamp = 1000L, sender = "a@b.com", subject = "S1", snippet = ""))
        val id2 = dao.insert(AlertEntity(timestamp = 2000L, sender = "a@b.com", subject = "S2", snippet = ""))
        dao.insert(AlertEntity(timestamp = 3000L, sender = "a@b.com", subject = "S3", snippet = ""))

        assertEquals(3, dao.getUnacknowledgedCount().first())

        dao.acknowledge(id2)
        assertEquals(2, dao.getUnacknowledgedCount().first())
    }
}
