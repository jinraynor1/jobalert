package com.jobalert.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val sender: String,
    val subject: String,
    val snippet: String,
    val acknowledged: Boolean = false,
    val ruleName: String = ""
)
