package com.jobalert.domain

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rules")
data class Rule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val senders: List<String>,
    val keywords: List<String>,
    val isEnabled: Boolean = true
)
