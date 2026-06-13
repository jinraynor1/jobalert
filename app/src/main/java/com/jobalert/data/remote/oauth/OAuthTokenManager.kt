package com.jobalert.data.remote.oauth

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.jobalert.data.local.credential.CredentialStore
import com.jobalert.data.local.credential.OAuthTokens
import com.jobalert.domain.model.EmailAccount
import com.jobalert.domain.model.OAuthConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.GrantTypeValues
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import kotlin.coroutines.resume

private const val TAG = "JobAlert-OAuth"
private const val GOOGLE_DISCOVERY = "https://accounts.google.com/.well-known/openid-configuration"
private const val MICROSOFT_DISCOVERY = "https://login.microsoftonline.com/common/v2.0/.well-known/openid-configuration"
private const val REDIRECT_URI = "com.jobalert://oauth2redirect"
private const val GOOGLE_REDIRECT_URI = "com.googleusercontent.apps.1033326854666-7qkgreu5obcr5e1sdc7hqle6spejcabt:/oauth2redirect"
private const val GOOGLE_SCOPE = "https://mail.google.com/"
private const val MICROSOFT_SCOPE = "https://outlook.office.com/IMAP.AccessAsUser.All offline_access"

object OAuthClients {
    const val GOOGLE_CLIENT_ID = "1033326854666-7qkgreu5obcr5e1sdc7hqle6spejcabt.apps.googleusercontent.com"
    const val MICROSOFT_CLIENT_ID = "TODO_AZURE_CLIENT_ID"
}

object OAuthTokenManager {

    private val gson = Gson()

    // Exposed internal for unit testing
    internal fun isTokenFresh(tokens: OAuthTokens, now: Long = System.currentTimeMillis()): Boolean {
        if (tokens.accessTokenExpiry == -1L) return false
        return tokens.accessTokenExpiry > now + 60_000L
    }

    suspend fun getValidToken(
        account: EmailAccount,
        credentialStore: CredentialStore,
        context: Context
    ): String? {
        val tokens = credentialStore.getOAuthTokens(account.email) ?: run {
            Log.w(TAG, "[${account.email}] No OAuth tokens stored")
            return null
        }
        if (isTokenFresh(tokens)) {
            val expiresInSec = (tokens.accessTokenExpiry - System.currentTimeMillis()) / 1000
            Log.d(TAG, "[${account.email}] Token válido — expira en ${expiresInSec}s")
            logTokenInfo(account.email, tokens.accessToken)
            return tokens.accessToken
        }
        Log.i(TAG, "[${account.email}] Token expirado — refrescando")
        return refreshToken(account, tokens, credentialStore, context)
    }

    private suspend fun refreshToken(
        account: EmailAccount,
        tokens: OAuthTokens,
        credentialStore: CredentialStore,
        context: Context
    ): String? {
        val serviceConfig = serviceConfigFor(account) ?: run {
            Log.e(TAG, "[${account.email}] Cannot fetch service config for refresh")
            return null
        }
        val clientId = clientIdFor(account)
        val refreshRequest = TokenRequest.Builder(serviceConfig, clientId)
            .setGrantType(GrantTypeValues.REFRESH_TOKEN)
            .setRefreshToken(tokens.refreshToken)
            .build()

        val service = AuthorizationService(context)
        return try {
            val response = suspendCancellableCoroutine<net.openid.appauth.TokenResponse?> { cont ->
                service.performTokenRequest(refreshRequest) { resp, ex ->
                    if (resp != null) {
                        cont.resume(resp)
                    } else {
                        Log.e(TAG, "[${account.email}] Refresh falló: ${ex?.error} — ${ex?.message}")
                        cont.resume(null)
                    }
                }
            } ?: return null

            val expiry = response.accessTokenExpirationTime ?: (System.currentTimeMillis() + 3_600_000L)
            val newTokens = tokens.copy(
                accessToken = response.accessToken ?: return null,
                accessTokenExpiry = expiry
            )
            credentialStore.setOAuthTokens(account.email, newTokens)
            val expiresInSec = (expiry - System.currentTimeMillis()) / 1000
            Log.i(TAG, "[${account.email}] Token refrescado OK — nuevo expiry en ${expiresInSec}s")
            newTokens.accessToken
        } finally {
            service.dispose()
        }
    }

    suspend fun buildAuthorizationIntent(
        authType: String,
        oauthConfig: OAuthConfig?,
        context: Context
    ): android.content.Intent? {
        val serviceConfig = when (authType) {
            "OAUTH2_GOOGLE" -> fetchDiscovery(GOOGLE_DISCOVERY)
            "OAUTH2_MICROSOFT" -> fetchDiscovery(MICROSOFT_DISCOVERY)
            "OAUTH2_CUSTOM" -> {
                val cfg = oauthConfig ?: return null
                AuthorizationServiceConfiguration(Uri.parse(cfg.authEndpoint), Uri.parse(cfg.tokenEndpoint))
            }
            else -> return null
        } ?: return null

        val clientId = when (authType) {
            "OAUTH2_GOOGLE" -> OAuthClients.GOOGLE_CLIENT_ID
            "OAUTH2_MICROSOFT" -> OAuthClients.MICROSOFT_CLIENT_ID
            "OAUTH2_CUSTOM" -> oauthConfig?.clientId ?: return null
            else -> return null
        }
        val scope = when (authType) {
            "OAUTH2_GOOGLE" -> GOOGLE_SCOPE
            "OAUTH2_MICROSOFT" -> MICROSOFT_SCOPE
            "OAUTH2_CUSTOM" -> oauthConfig?.scope ?: return null
            else -> return null
        }

        val redirectUri = if (authType == "OAUTH2_GOOGLE") GOOGLE_REDIRECT_URI else REDIRECT_URI
        val authRequest = AuthorizationRequest.Builder(
            serviceConfig, clientId, ResponseTypeValues.CODE, Uri.parse(redirectUri)
        ).setScope(scope).build()

        return AuthorizationService(context).getAuthorizationRequestIntent(authRequest)
    }

    fun storeTokensFromResponse(
        email: String,
        accessToken: String,
        refreshToken: String,
        expirationTime: Long?,
        credentialStore: CredentialStore
    ) {
        val expiry = expirationTime ?: (System.currentTimeMillis() + 3_600_000L)
        credentialStore.setOAuthTokens(email, OAuthTokens(accessToken, refreshToken, expiry))
    }

    private suspend fun serviceConfigFor(account: EmailAccount): AuthorizationServiceConfiguration? {
        val oauthConfig = account.oauthConfig?.let {
            try { gson.fromJson(it, OAuthConfig::class.java) } catch (_: Exception) { null }
        }
        return when (account.authType) {
            "OAUTH2_GOOGLE" -> fetchDiscovery(GOOGLE_DISCOVERY)
            "OAUTH2_MICROSOFT" -> fetchDiscovery(MICROSOFT_DISCOVERY)
            "OAUTH2_CUSTOM" -> oauthConfig?.let {
                AuthorizationServiceConfiguration(Uri.parse(it.authEndpoint), Uri.parse(it.tokenEndpoint))
            }
            else -> null
        }
    }

    private fun clientIdFor(account: EmailAccount): String = when (account.authType) {
        "OAUTH2_GOOGLE" -> OAuthClients.GOOGLE_CLIENT_ID
        "OAUTH2_MICROSOFT" -> OAuthClients.MICROSOFT_CLIENT_ID
        "OAUTH2_CUSTOM" -> account.oauthConfig?.let {
            try { gson.fromJson(it, OAuthConfig::class.java).clientId } catch (_: Exception) { "" }
        } ?: ""
        else -> ""
    }

    private suspend fun logTokenInfo(email: String, accessToken: String) {
        withContext(Dispatchers.IO) {
            try {
                val url = java.net.URL("https://oauth2.googleapis.com/tokeninfo?access_token=$accessToken")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val code = conn.responseCode
                val body = if (code == 200) conn.inputStream.bufferedReader().readText()
                           else conn.errorStream?.bufferedReader()?.readText() ?: "(sin cuerpo)"
                conn.disconnect()
                val json = org.json.JSONObject(body)
                val scope = json.optString("scope", "(sin scope)")
                val expiresIn = json.optString("expires_in", "?")
                val aud = json.optString("aud", "?")
                val errorDesc = json.optString("error_description", "")
                if (code == 200) {
                    Log.i(TAG, "[$email] tokeninfo → scope=\"$scope\" | expires_in=${expiresIn}s | aud=$aud")
                } else {
                    Log.w(TAG, "[$email] tokeninfo HTTP $code → $errorDesc")
                }
            } catch (e: Exception) {
                Log.w(TAG, "[$email] tokeninfo excepción: ${e.message}")
            }
        }
    }

    private suspend fun fetchDiscovery(url: String): AuthorizationServiceConfiguration? =
        suspendCancellableCoroutine { cont ->
            AuthorizationServiceConfiguration.fetchFromUrl(
                Uri.parse(url)
            ) { config, _ -> cont.resume(config) }
        }
}
