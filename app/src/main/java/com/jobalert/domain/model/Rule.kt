package com.jobalert.domain.model

data class Rule(
    val id: Long = 0,
    val name: String,
    val senders: List<String>,
    val subjectKeywords: List<String>,
    val bodyKeywords: List<String>,
    val isEnabled: Boolean = true,
    val alertColor: Int? = null,  // ARGB; null = color del tema
    val position: Int = 0
)
