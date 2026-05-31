package com.jobalert.data.db

import androidx.room.*
import com.jobalert.domain.Rule
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules ORDER BY name ASC")
    fun getAllRules(): Flow<List<Rule>>

    @Query("SELECT * FROM rules ORDER BY name ASC")
    suspend fun getAllRulesOnce(): List<Rule>

    @Insert
    suspend fun insert(rule: Rule): Long

    @Update
    suspend fun update(rule: Rule)

    @Query("UPDATE rules SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Delete
    suspend fun delete(rule: Rule)
}
