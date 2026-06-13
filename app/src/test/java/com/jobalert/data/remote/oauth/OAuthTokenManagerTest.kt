package com.jobalert.data.remote.oauth

import com.jobalert.data.local.credential.OAuthTokens
import org.junit.Assert.assertEquals
import org.junit.Test

class OAuthTokenManagerTest {

    @Test
    fun `isTokenFresh returns true when expiry is more than 60s away`() {
        val now = System.currentTimeMillis()
        val tokens = OAuthTokens("access", "refresh", now + 120_000L)
        assertEquals(true, OAuthTokenManager.isTokenFresh(tokens, now))
    }

    @Test
    fun `isTokenFresh returns false when expiry is within 60s`() {
        val now = System.currentTimeMillis()
        val tokens = OAuthTokens("access", "refresh", now + 30_000L)
        assertEquals(false, OAuthTokenManager.isTokenFresh(tokens, now))
    }

    @Test
    fun `isTokenFresh returns false when expiry is -1`() {
        val now = System.currentTimeMillis()
        val tokens = OAuthTokens("access", "refresh", -1L)
        assertEquals(false, OAuthTokenManager.isTokenFresh(tokens, now))
    }

    @Test
    fun `isTokenFresh returns false when expiry is exactly 60s boundary`() {
        val now = System.currentTimeMillis()
        val tokens = OAuthTokens("access", "refresh", now + 60_000L)
        assertEquals(false, OAuthTokenManager.isTokenFresh(tokens, now))
    }
}
