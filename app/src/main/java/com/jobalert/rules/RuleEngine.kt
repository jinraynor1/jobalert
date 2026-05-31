package com.jobalert.rules

import com.jobalert.domain.NotificationData
import com.jobalert.domain.Rule

object RuleEngine {

    fun match(data: NotificationData, rules: List<Rule>): Boolean {
        if (rules.isEmpty()) return false
        return rules.filter { it.isEnabled }.any { rule -> matchesRule(data, rule) }
    }

    private fun matchesRule(data: NotificationData, rule: Rule): Boolean {
        val senderMatches = rule.senders.isEmpty() ||
            rule.senders.any { data.sender.contains(it, ignoreCase = true) }

        val keywordMatches = rule.keywords.isEmpty() ||
            rule.keywords.any { kw ->
                data.subject.contains(kw, ignoreCase = true) ||
                    data.snippet.contains(kw, ignoreCase = true)
            }

        return senderMatches && keywordMatches
    }
}
