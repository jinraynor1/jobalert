package com.jobalert.data.remote.oauth

import com.google.gson.Gson
import com.jobalert.data.local.credential.OAuthTokens
import org.junit.Assert.assertEquals
import org.junit.Test

class OAuthTokensSerializationTest {
    private val gson = Gson()

    @Test
    fun `OAuthTokens survives Gson roundtrip`() {
        val original = OAuthTokens(
            accessToken = "access_abc",
            refreshToken = "refresh_xyz",
            accessTokenExpiry = 1_700_000_000_000L
        )
        val json = gson.toJson(original)
        val restored = gson.fromJson(json, OAuthTokens::class.java)
        assertEquals(original, restored)
    }

    @Test
    fun `expiry of -1 survives roundtrip`() {
        val original = OAuthTokens("a", "r", -1L)
        val json = gson.toJson(original)
        val restored = gson.fromJson(json, OAuthTokens::class.java)
        assertEquals(-1L, restored.accessTokenExpiry)
    }
}
