package com.jobalert.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "email_accounts", indices = [Index(value = ["email"], unique = true)])
data class EmailAccount(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val email: String,
    val host: String,
    val port: Int = 993,
    val useSsl: Boolean = true,
    val isEnabled: Boolean = true,
    val lastSeenUid: Long = 0,
    val uidValidity: Long = 0,
    val authType: String = "PASSWORD",  // PASSWORD | OAUTH2_GOOGLE | OAUTH2_MICROSOFT | OAUTH2_CUSTOM
    val oauthConfig: String? = null     // JSON-encoded OAuthConfig, only for OAUTH2_CUSTOM
)
