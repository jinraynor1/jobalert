package com.jobalert.data.local.credential

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson

data class OAuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiry: Long  // epoch ms; -1 = unknown → forces refresh on next run
)

class CredentialStore(context: Context) {

    private val gson = Gson()

    private val prefs = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "jobalert_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // --- Password auth ---
    fun setPassword(email: String, password: String) {
        prefs.edit().putString(email, password).apply()
    }

    fun getPassword(email: String): String? = prefs.getString(email, null)

    // --- OAuth auth (keyed separately to avoid collisions with password keys) ---
    fun setOAuthTokens(email: String, tokens: OAuthTokens) {
        prefs.edit().putString("oauth_$email", gson.toJson(tokens)).apply()
    }

    fun getOAuthTokens(email: String): OAuthTokens? {
        val json = prefs.getString("oauth_$email", null) ?: return null
        return try { gson.fromJson(json, OAuthTokens::class.java) } catch (_: Exception) { null }
    }

    // Clears both password and OAuth tokens for the account
    fun removePassword(email: String) {
        prefs.edit()
            .remove(email)
            .remove("oauth_$email")
            .apply()
    }
}
