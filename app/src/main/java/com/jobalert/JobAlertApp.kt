package com.jobalert

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.jobalert.background.EmailPollWorker
import com.jobalert.data.local.credential.CredentialStore
import com.jobalert.data.local.db.AppDatabase
import com.jobalert.data.remote.MailAuthGatewayImpl
import com.jobalert.data.repository.AlertRepositoryImpl
import com.jobalert.data.repository.EmailAccountRepositoryImpl
import com.jobalert.data.repository.RuleRepositoryImpl
import com.jobalert.data.repository.SettingsRepositoryImpl
import com.jobalert.domain.repository.AlertRepository
import com.jobalert.domain.repository.EmailAccountRepository
import com.jobalert.domain.repository.MailAuthGateway
import com.jobalert.domain.repository.RuleRepository
import com.jobalert.domain.repository.SettingsRepository
import com.jobalert.ui.overlay.OverlayManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class JobAlertApp : Application() {

    companion object {
        const val CHANNEL_ID = "jobalert_service"
        private const val WORK_NAME = "email_poll"
        const val SCAN_NOW_WORK = "email_poll_now"
    }

    private val _darkMode = MutableStateFlow(false)
    val darkMode: StateFlow<Boolean> = _darkMode.asStateFlow()

    fun setDarkMode(enabled: Boolean) { _darkMode.value = enabled }

    override fun onCreate() {
        super.onCreate()
        // Seed the dark mode flow from persisted preference after the lazy repo is ready
        _darkMode.value = settingsRepository.darkMode
        createNotificationChannel()
        schedulePolling()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "JobAlert",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notificaciones de JobAlert"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun schedulePolling() {
        val intervalMinutes = settingsRepository.imapPollIntervalMinutes.toLong()
        val request = PeriodicWorkRequestBuilder<EmailPollWorker>(intervalMinutes, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun reschedulePolling() = schedulePolling()

    fun scanNow() {
        val request = OneTimeWorkRequestBuilder<EmailPollWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            SCAN_NOW_WORK, ExistingWorkPolicy.REPLACE, request
        )
    }

    val database by lazy { AppDatabase.getInstance(this) }
    val credentialStore by lazy { CredentialStore(this) }
    val alertRepository: AlertRepository by lazy { AlertRepositoryImpl(database.alertDao()) }
    val ruleRepository: RuleRepository by lazy { RuleRepositoryImpl(database.ruleDao()) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepositoryImpl(this) }
    val overlayManager by lazy { OverlayManager(this, settingsRepository) }
    val emailAccountRepository: EmailAccountRepository by lazy {
        EmailAccountRepositoryImpl(database.emailAccountDao(), credentialStore)
    }
    val mailAuthGateway: MailAuthGateway by lazy { MailAuthGatewayImpl(credentialStore) }
}
