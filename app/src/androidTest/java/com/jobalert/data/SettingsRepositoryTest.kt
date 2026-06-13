package com.jobalert.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jobalert.data.repository.SettingsRepositoryImpl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {
    private lateinit var repo: SettingsRepositoryImpl

    @Before
    fun setup() {
        repo = SettingsRepositoryImpl(ApplicationProvider.getApplicationContext())
        repo.muteUntil = 0L
    }

    @Test
    fun isMuted_falseWhenZero() {
        repo.muteUntil = 0L
        assertFalse(repo.isMuted)
    }

    @Test
    fun isMuted_falseWhenInPast() {
        repo.muteUntil = System.currentTimeMillis() - 1_000L
        assertFalse(repo.isMuted)
    }

    @Test
    fun isMuted_trueWhenInFuture() {
        repo.muteUntil = System.currentTimeMillis() + 60_000L
        assertTrue(repo.isMuted)
    }
}
