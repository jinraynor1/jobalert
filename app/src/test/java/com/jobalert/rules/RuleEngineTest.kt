package com.jobalert.rules

import com.jobalert.domain.NotificationData
import com.jobalert.domain.Rule
import org.junit.Assert.*
import org.junit.Test

class RuleEngineTest {

    private val data = NotificationData(
        sender = "alertas@sistema.com",
        subject = "CRITICAL: Server DOWN",
        snippet = "El servidor production-01 no responde desde las 03:00"
    )

    @Test
    fun `matches when sender substring and keyword both match`() {
        val rules = listOf(Rule(name = "Prod", senders = listOf("sistema.com"), keywords = listOf("CRITICAL")))
        assertTrue(RuleEngine.match(data, rules))
    }

    @Test
    fun `does not match when sender does not match any in list`() {
        val rules = listOf(Rule(name = "Prod", senders = listOf("otro.com"), keywords = listOf("CRITICAL")))
        assertFalse(RuleEngine.match(data, rules))
    }

    @Test
    fun `does not match when no keyword found in subject or snippet`() {
        val rules = listOf(Rule(name = "Prod", senders = listOf("sistema.com"), keywords = listOf("WARNING")))
        assertFalse(RuleEngine.match(data, rules))
    }

    @Test
    fun `match is case insensitive for sender and keyword`() {
        val rules = listOf(Rule(name = "Prod", senders = listOf("SISTEMA.COM"), keywords = listOf("critical")))
        assertTrue(RuleEngine.match(data, rules))
    }

    @Test
    fun `matches keyword found in snippet even if not in subject`() {
        val rules = listOf(Rule(name = "Prod", senders = listOf("sistema.com"), keywords = listOf("production-01")))
        assertTrue(RuleEngine.match(data, rules))
    }

    @Test
    fun `empty rules list never matches`() {
        assertFalse(RuleEngine.match(data, emptyList()))
    }

    @Test
    fun `empty sender list matches any sender`() {
        val rules = listOf(Rule(name = "Any", senders = emptyList(), keywords = listOf("CRITICAL")))
        assertTrue(RuleEngine.match(data, rules))
    }

    @Test
    fun `empty keyword list matches any content`() {
        val rules = listOf(Rule(name = "Any", senders = listOf("sistema.com"), keywords = emptyList()))
        assertTrue(RuleEngine.match(data, rules))
    }

    @Test
    fun `second rule matches when first rule does not`() {
        val rules = listOf(
            Rule(name = "Rule1", senders = listOf("otro.com"), keywords = listOf("CRITICAL")),
            Rule(name = "Rule2", senders = listOf("sistema.com"), keywords = listOf("CRITICAL"))
        )
        assertTrue(RuleEngine.match(data, rules))
    }

    @Test
    fun `does not match sender with empty keywords if rule has keywords`() {
        val rules = listOf(Rule(name = "Prod", senders = listOf("sistema.com"), keywords = listOf("NEVER_HERE")))
        assertFalse(RuleEngine.match(data, rules))
    }
}
