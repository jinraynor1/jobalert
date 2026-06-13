package com.jobalert.data.repository

import com.jobalert.data.local.db.AlertDao
import com.jobalert.data.mapper.toDomain
import com.jobalert.data.mapper.toEntity
import com.jobalert.domain.model.Alert
import com.jobalert.domain.repository.AlertRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AlertRepositoryImpl(private val dao: AlertDao) : AlertRepository {

    override val allAlerts: Flow<List<Alert>> =
        dao.getAllAlerts().map { list -> list.map { it.toDomain() } }

    override val unacknowledgedCount: Flow<Int> = dao.getUnacknowledgedCount()

    override suspend fun insert(alert: Alert): Long = dao.insert(alert.toEntity())

    override suspend fun acknowledge(id: Long) = dao.acknowledge(id)

    override suspend fun deleteById(id: Long) = dao.deleteById(id)

    override suspend fun deleteAll() = dao.deleteAll()
}
