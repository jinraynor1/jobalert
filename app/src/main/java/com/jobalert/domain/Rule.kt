package com.jobalert.domain

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rules")
data class Rule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val senders: List<String>,
    val subjectKeywords: List<String>,
    val bodyKeywords: List<String>,
    val isEnabled: Boolean = true,
    val alertColor: Int? = null,  // ARGB; null = color del tema
    val position: Int = 0
)
