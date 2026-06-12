package com.jobalert.work

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.jobalert.JobAlertApp
import com.jobalert.data.model.AlertEntity
import com.jobalert.oauth.OAuthTokenManager
import com.jobalert.rules.RuleEngine
import com.jobalert.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "JobAlert-Worker"

class EmailPollWorker(ctx: android.content.Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    companion object {
        const val KEY_NEW_COUNT = "new_count"
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as JobAlertApp
        val accounts = app.emailAccountRepository.getEnabledAccountsOnce()
        val rules = app.ruleRepository.getAllRulesOnce()

        Log.i(TAG, "Poll started — ${accounts.size} account(s), ${rules.size} rule(s)")

        var newCount = 0

        for (account in accounts) {
            val credential: String? = if (account.authType == "PASSWORD") {
                app.credentialStore.getPassword(account.email)
            } else {
                OAuthTokenManager.getValidToken(account, app.credentialStore, applicationContext)
            }

            if (credential == null) {
                Log.w(TAG, "[${account.email}] No credential (may need re-auth) — skipping")
                notifyReauthRequired(account.email)
                app.emailAccountRepository.setNeedsReauth(account.id, true)
                continue
            }

            val fetchResult = withContext(Dispatchers.IO) {
                com.jobalert.imap.ImapClient.fetchNew(account, credential, app.settingsRepository.snippetMaxChars)
            }

            if (fetchResult.error != null) {
                Log.w(TAG, "[${account.email}] Fetch error: ${fetchResult.error}")
            }

            Log.i(TAG, "[${account.email}] Fetched ${fetchResult.messages.size} message(s) — lastSeenUid=${fetchResult.newLastSeenUid}, uidValidity=${fetchResult.uidValidity}")

            for (data in fetchResult.messages) {
                if (RuleEngine.match(data, rules)) {
                    Log.i(TAG, "[${account.email}] Rule matched — saving alert: ${data.subject}")
                    app.alertRepository.insert(
                        AlertEntity(
                            timestamp = System.currentTimeMillis(),
                            sender = data.sender,
                            subject = data.subject,
                            snippet = data.snippet
                        )
                    )
                    val s = app.settingsRepository
                    when {
                        s.isMuted -> Log.i(TAG, "SILENCIADO — overlay omitido")
                        s.isInQuietHours -> Log.i(TAG, "HORARIO SILENCIOSO — overlay omitido")
                        s.isWithinMinInterval -> Log.i(TAG, "INTERVALO MÍNIMO — overlay omitido")
                        else -> {
                            s.lastOverlayShownAt = System.currentTimeMillis()
                            withContext(Dispatchers.Main) { app.overlayManager.show(data) }
                        }
                    }
                    newCount++
                }
            }

            app.emailAccountRepository.updateUidState(
                account.id,
                fetchResult.newLastSeenUid,
                fetchResult.uidValidity
            )
            if (account.needsReauth && fetchResult.error == null) {
                app.emailAccountRepository.setNeedsReauth(account.id, false)
            }
        }

        return Result.success(workDataOf(KEY_NEW_COUNT to newCount))
    }

    private fun notifyReauthRequired(email: String) {
        val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            applicationContext, email.hashCode(), launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, JobAlertApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("JobAlert — Re-autorización requerida")
            .setContentText("La cuenta $email necesita autorización nuevamente.")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        applicationContext.getSystemService(NotificationManager::class.java)
            .notify(email.hashCode(), notification)
    }
}
