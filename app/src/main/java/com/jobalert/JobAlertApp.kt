package com.jobalert

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
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
import com.jobalert.ui.MainActivity
import com.jobalert.ui.overlay.OverlayManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class JobAlertApp : Application() {

    companion object {
        const val CHANNEL_ID = "jobalert_service"
        const val BADGE_CHANNEL_ID = "jobalert_badge"
        const val BADGE_NOTIFICATION_ID = 0xBAD9E
        private const val WORK_NAME = "email_poll"
        const val SCAN_NOW_WORK = "email_poll_now"
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _darkMode = MutableStateFlow(false)
    val darkMode: StateFlow<Boolean> = _darkMode.asStateFlow()

    fun setDarkMode(enabled: Boolean) { _darkMode.value = enabled }

    override fun onCreate() {
        super.onCreate()
        // Seed the dark mode flow from persisted preference after the lazy repo is ready
        _darkMode.value = settingsRepository.darkMode
        createNotificationChannel()
        schedulePolling()
        appScope.launch {
            alertRepository.unacknowledgedCount.collect { count -> updatePendingBadge(count) }
        }
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "JobAlert", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Notificaciones de JobAlert"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(BADGE_CHANNEL_ID, "Alertas pendientes", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Muestra el número de alertas sin revisar"
                setShowBadge(true)
            }
        )
    }

    private fun updatePendingBadge(count: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        if (count <= 0) { nm.cancel(BADGE_NOTIFICATION_ID); return }

        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (count == 1) "1 alerta pendiente por revisar"
                   else "$count alertas pendientes por revisar"
        val notification = NotificationCompat.Builder(this, BADGE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("JobAlert")
            .setContentText(text)
            .setNumber(count)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        nm.notify(BADGE_NOTIFICATION_ID, notification)
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
