package com.jobalert.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val senders: List<String>,
    val subjectKeywords: List<String>,
    val bodyKeywords: List<String>,
    val isEnabled: Boolean = true,
    val alertColor: Int? = null,
    val position: Int = 0
)
