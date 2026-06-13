package com.jobalert.data.remote

import android.content.Context
import android.content.Intent
import com.jobalert.data.local.credential.CredentialStore
import com.jobalert.data.remote.imap.ImapClient
import com.jobalert.data.remote.oauth.OAuthTokenManager
import com.jobalert.domain.model.OAuthConfig
import com.jobalert.domain.repository.MailAuthGateway

class MailAuthGatewayImpl(
    private val credentialStore: CredentialStore
) : MailAuthGateway {

    override suspend fun testConnection(
        host: String,
        port: Int,
        useSsl: Boolean,
        email: String,
        credential: String,
        authType: String
    ): Result<Unit> = ImapClient.testConnection(host, port, useSsl, email, credential, authType)

    override suspend fun buildAuthorizationIntent(
        authType: String,
        oauthConfig: OAuthConfig?,
        context: Context
    ): Intent? = OAuthTokenManager.buildAuthorizationIntent(authType, oauthConfig, context)

    override fun storeTokensFromResponse(
        email: String,
        accessToken: String,
        refreshToken: String,
        expirationTime: Long?
    ) = OAuthTokenManager.storeTokensFromResponse(email, accessToken, refreshToken, expirationTime, credentialStore)
}
