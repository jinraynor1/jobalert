package com.jobalert.data.repository

import com.jobalert.data.db.AlertDao
import com.jobalert.data.model.AlertEntity
import kotlinx.coroutines.flow.Flow

class AlertRepository(private val dao: AlertDao) {
    val allAlerts: Flow<List<AlertEntity>> = dao.getAllAlerts()
    val unacknowledgedCount: Flow<Int> = dao.getUnacknowledgedCount()

    suspend fun insert(alert: AlertEntity): Long = dao.insert(alert)

    suspend fun acknowledge(id: Long) = dao.acknowledge(id)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun deleteAll() = dao.deleteAll()
}
