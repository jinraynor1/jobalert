package com.jobalert.data.local.db

import androidx.room.*
import com.jobalert.data.local.entity.RuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules ORDER BY position ASC")
    fun getAllRules(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules ORDER BY position ASC")
    suspend fun getAllRulesOnce(): List<RuleEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM rules")
    suspend fun maxPosition(): Int

    @Insert
    suspend fun insert(rule: RuleEntity): Long

    @Update
    suspend fun update(rule: RuleEntity)

    @Update
    suspend fun updateAll(rules: List<RuleEntity>)

    @Query("UPDATE rules SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Delete
    suspend fun delete(rule: RuleEntity)
}
