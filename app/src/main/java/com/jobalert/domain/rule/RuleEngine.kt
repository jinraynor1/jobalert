package com.jobalert.domain.rule

import com.jobalert.domain.model.NotificationData
import com.jobalert.domain.model.Rule

object RuleEngine {

    fun match(data: NotificationData, rules: List<Rule>): Rule? =
        rules.filter { it.isEnabled }.firstOrNull { rule -> matchesRule(data, rule) }

    private fun matchesRule(data: NotificationData, rule: Rule): Boolean {
        val senderMatches = rule.senders.isEmpty() ||
            rule.senders.any { data.sender.contains(it, ignoreCase = true) }

        val subjectMatches = rule.subjectKeywords.isEmpty() ||
            rule.subjectKeywords.any { data.subject.contains(it, ignoreCase = true) }

        val bodyMatches = rule.bodyKeywords.isEmpty() ||
            rule.bodyKeywords.any { data.snippet.contains(it, ignoreCase = true) }

        return senderMatches && subjectMatches && bodyMatches
    }
}
