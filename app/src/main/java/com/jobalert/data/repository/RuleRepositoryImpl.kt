package com.jobalert.data.repository

import com.jobalert.data.local.db.RuleDao
import com.jobalert.data.mapper.toDomain
import com.jobalert.data.mapper.toEntity
import com.jobalert.domain.model.Rule
import com.jobalert.domain.repository.RuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RuleRepositoryImpl(private val dao: RuleDao) : RuleRepository {

    override val allRules: Flow<List<Rule>> =
        dao.getAllRules().map { list -> list.map { it.toDomain() } }

    override suspend fun getAllRulesOnce(): List<Rule> =
        dao.getAllRulesOnce().map { it.toDomain() }

    override suspend fun maxPosition(): Int = dao.maxPosition()

    override suspend fun insert(rule: Rule): Long = dao.insert(rule.toEntity())

    override suspend fun update(rule: Rule) = dao.update(rule.toEntity())

    override suspend fun updateAll(rules: List<Rule>) =
        dao.updateAll(rules.map { it.toEntity() })

    override suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)

    override suspend fun delete(rule: Rule) = dao.delete(rule.toEntity())
}
