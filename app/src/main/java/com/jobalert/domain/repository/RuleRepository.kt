package com.jobalert.domain.repository

import com.jobalert.domain.model.Rule
import kotlinx.coroutines.flow.Flow

interface RuleRepository {
    val allRules: Flow<List<Rule>>
    suspend fun getAllRulesOnce(): List<Rule>
    suspend fun maxPosition(): Int
    suspend fun insert(rule: Rule): Long
    suspend fun update(rule: Rule)
    suspend fun updateAll(rules: List<Rule>)
    suspend fun setEnabled(id: Long, enabled: Boolean)
    suspend fun delete(rule: Rule)
}
