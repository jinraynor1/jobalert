package com.jobalert.domain.repository

import android.content.Context
import android.content.Intent
import com.jobalert.domain.model.OAuthConfig

interface MailAuthGateway {

    suspend fun testConnection(
        host: String,
        port: Int,
        useSsl: Boolean,
        email: String,
        credential: String,
        authType: String = "PASSWORD"
    ): Result<Unit>

    suspend fun buildAuthorizationIntent(
        authType: String,
        oauthConfig: OAuthConfig?,
        context: Context
    ): Intent?

    fun storeTokensFromResponse(
        email: String,
        accessToken: String,
        refreshToken: String,
        expirationTime: Long?
    )
}
