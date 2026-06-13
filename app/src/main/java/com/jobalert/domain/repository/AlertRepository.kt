package com.jobalert.domain.repository

import com.jobalert.domain.model.Alert
import kotlinx.coroutines.flow.Flow

interface AlertRepository {
    val allAlerts: Flow<List<Alert>>
    val unacknowledgedCount: Flow<Int>
    suspend fun insert(alert: Alert): Long
    suspend fun acknowledge(id: Long)
    suspend fun deleteById(id: Long)
    suspend fun deleteAll()
}
