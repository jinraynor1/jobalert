package com.jobalert.domain.rule

import com.jobalert.domain.model.NotificationData
import com.jobalert.domain.model.Rule
import org.junit.Assert.*
import org.junit.Test

class RuleEngineTest {

    private val data = NotificationData(
        sender = "alertas@sistema.com",
        subject = "Error aplicativos",
        snippet = "CRITICAL: El servidor production-01 no responde desde las 03:00"
    )

    @Test
    fun `matches when sender, subject keyword and body keyword all match`() {
        val rule = Rule(name = "Prod", senders = listOf("sistema.com"), subjectKeywords = listOf("Error aplicativos"), bodyKeywords = listOf("CRITICAL"))
        assertNotNull(RuleEngine.match(data, listOf(rule)))
    }

    @Test
    fun `does not match when sender does not match`() {
        val rule = Rule(name = "Prod", senders = listOf("otro.com"), subjectKeywords = listOf("Error aplicativos"), bodyKeywords = listOf("CRITICAL"))
        assertNull(RuleEngine.match(data, listOf(rule)))
    }

    @Test
    fun `does not match when subject keyword not found`() {
        val rule = Rule(name = "Prod", senders = listOf("sistema.com"), subjectKeywords = listOf("Backup report"), bodyKeywords = listOf("CRITICAL"))
        assertNull(RuleEngine.match(data, listOf(rule)))
    }

    @Test
    fun `does not match when body keyword not found`() {
        val rule = Rule(name = "Prod", senders = listOf("sistema.com"), subjectKeywords = listOf("Error aplicativos"), bodyKeywords = listOf("EMERGENCY"))
        assertNull(RuleEngine.match(data, listOf(rule)))
    }

    @Test
    fun `differentiates two rules with same sender and subject but different body keywords`() {
        val rule1 = Rule(name = "Critical", senders = listOf("sistema.com"), subjectKeywords = listOf("Error aplicativos"), bodyKeywords = listOf("CRITICAL"))
        val rule2 = Rule(name = "Emergency", senders = listOf("sistema.com"), subjectKeywords = listOf("Error aplicativos"), bodyKeywords = listOf("EMERGENCY"))
        assertEquals("Critical", RuleEngine.match(data, listOf(rule1, rule2))?.name)
        assertNull(RuleEngine.match(data, listOf(rule2)))
    }

    @Test
    fun `match is case insensitive`() {
        val rule = Rule(name = "Prod", senders = listOf("SISTEMA.COM"), subjectKeywords = listOf("error aplicativos"), bodyKeywords = listOf("critical"))
        assertNotNull(RuleEngine.match(data, listOf(rule)))
    }

    @Test
    fun `empty rules list never matches`() {
        assertNull(RuleEngine.match(data, emptyList()))
    }

    @Test
    fun `empty sender list matches any sender`() {
        val rule = Rule(name = "Any", senders = emptyList(), subjectKeywords = listOf("Error aplicativos"), bodyKeywords = listOf("CRITICAL"))
        assertNotNull(RuleEngine.match(data, listOf(rule)))
    }

    @Test
    fun `empty subjectKeywords matches any subject`() {
        val rule = Rule(name = "Any", senders = listOf("sistema.com"), subjectKeywords = emptyList(), bodyKeywords = listOf("CRITICAL"))
        assertNotNull(RuleEngine.match(data, listOf(rule)))
    }

    @Test
    fun `empty bodyKeywords matches any body`() {
        val rule = Rule(name = "Any", senders = listOf("sistema.com"), subjectKeywords = listOf("Error aplicativos"), bodyKeywords = emptyList())
        assertNotNull(RuleEngine.match(data, listOf(rule)))
    }

    @Test
    fun `all empty filters matches any message`() {
        val rule = Rule(name = "Any", senders = emptyList(), subjectKeywords = emptyList(), bodyKeywords = emptyList())
        assertNotNull(RuleEngine.match(data, listOf(rule)))
    }

    @Test
    fun `second rule matches when first does not`() {
        val rule1 = Rule(name = "Rule1", senders = listOf("otro.com"), subjectKeywords = emptyList(), bodyKeywords = listOf("CRITICAL"))
        val rule2 = Rule(name = "Rule2", senders = listOf("sistema.com"), subjectKeywords = emptyList(), bodyKeywords = listOf("CRITICAL"))
        assertEquals("Rule2", RuleEngine.match(data, listOf(rule1, rule2))?.name)
    }

    @Test
    fun `disabled rule does not match`() {
        val rule = Rule(name = "Prod", senders = listOf("sistema.com"), subjectKeywords = emptyList(), bodyKeywords = emptyList(), isEnabled = false)
        assertNull(RuleEngine.match(data, listOf(rule)))
    }
}
