package com.jobalert.data.db

import androidx.room.*
import com.jobalert.data.model.AlertEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts ORDER BY timestamp DESC")
    fun getAllAlerts(): Flow<List<AlertEntity>>

    @Insert
    suspend fun insert(alert: AlertEntity): Long

    @Query("UPDATE alerts SET acknowledged = 1 WHERE id = :id")
    suspend fun acknowledge(id: Long)

    @Query("DELETE FROM alerts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM alerts")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM alerts WHERE acknowledged = 0")
    fun getUnacknowledgedCount(): Flow<Int>
}
