package com.jobalert.domain

data class NotificationData(
    val sender: String,
    val subject: String,
    val snippet: String,
    val ruleName: String = "",
    val alertColor: Int? = null
)
