package com.jobalert.domain.model

data class Alert(
    val id: Long = 0,
    val timestamp: Long,
    val sender: String,
    val subject: String,
    val snippet: String,
    val acknowledged: Boolean = false,
    val ruleName: String = ""
)
