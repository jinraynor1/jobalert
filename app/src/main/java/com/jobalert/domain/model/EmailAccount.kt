package com.jobalert.domain.model

data class EmailAccount(
    val id: Long = 0,
    val email: String,
    val host: String,
    val port: Int = 993,
    val useSsl: Boolean = true,
    val isEnabled: Boolean = true,
    val lastSeenUid: Long = 0,
    val uidValidity: Long = 0,
    val authType: String = "PASSWORD",  // PASSWORD | OAUTH2_GOOGLE | OAUTH2_MICROSOFT | OAUTH2_CUSTOM
    val oauthConfig: String? = null,    // JSON-encoded OAuthConfig, only for OAUTH2_CUSTOM
    val needsReauth: Boolean = false    // true when the OAuth token could not be refreshed
)
