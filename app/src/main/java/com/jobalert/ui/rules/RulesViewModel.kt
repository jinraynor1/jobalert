package com.jobalert.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jobalert.data.repository.RuleRepository
import com.jobalert.domain.Rule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RulesViewModel(private val repository: RuleRepository) : ViewModel() {
    val rules: StateFlow<List<Rule>> = repository.allRules
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun addRule(name: String, senders: List<String>, subjectKeywords: List<String>, bodyKeywords: List<String>, alertColor: Int? = null) {
        viewModelScope.launch {
            val position = repository.maxPosition() + 1
            repository.insert(Rule(name = name, senders = senders, subjectKeywords = subjectKeywords, bodyKeywords = bodyKeywords, alertColor = alertColor, position = position))
        }
    }

    fun updateRule(rule: Rule, name: String, senders: List<String>, subjectKeywords: List<String>, bodyKeywords: List<String>, alertColor: Int? = null) {
        viewModelScope.launch {
            repository.update(rule.copy(name = name, senders = senders, subjectKeywords = subjectKeywords, bodyKeywords = bodyKeywords, alertColor = alertColor))
        }
    }

    fun setRuleEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(id, enabled) }
    }

    fun deleteRule(rule: Rule) {
        viewModelScope.launch { repository.delete(rule) }
    }

    fun persistOrder(reordered: List<Rule>) {
        viewModelScope.launch {
            repository.updateAll(reordered.mapIndexed { index, rule -> rule.copy(position = index) })
        }
    }
}
