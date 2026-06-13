package com.jobalert.data.repository

import com.jobalert.data.db.RuleDao
import com.jobalert.domain.Rule
import kotlinx.coroutines.flow.Flow

class RuleRepository(private val dao: RuleDao) {
    val allRules: Flow<List<Rule>> = dao.getAllRules()

    suspend fun getAllRulesOnce(): List<Rule> = dao.getAllRulesOnce()

    suspend fun maxPosition(): Int = dao.maxPosition()

    suspend fun insert(rule: Rule): Long = dao.insert(rule)

    suspend fun update(rule: Rule) = dao.update(rule)

    suspend fun updateAll(rules: List<Rule>) = dao.updateAll(rules)

    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)

    suspend fun delete(rule: Rule) = dao.delete(rule)
}
