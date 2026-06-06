package com.jobalert.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import com.jobalert.ui.theme.JobAlertTheme
import com.jobalert.ui.theme.LocalAlertColor
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.jobalert.data.repository.SettingsRepository
import com.jobalert.domain.NotificationData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OverlayManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var currentView: ComposeView? = null
    private var currentOwner: ServiceLifecycleOwner? = null
    private val queue: ArrayDeque<NotificationData> = ArrayDeque()
    private var ringtone: android.media.Ringtone? = null

    fun show(data: NotificationData) {
        Log.i("JobAlert", "[6] OverlayManager.show() — currentView=${if (currentView != null) "visible (en cola)" else "null (mostrando)"}")
        if (currentView != null) {
            queue.addLast(data)
            return
        }
        showInternal(data)
    }

    private fun showInternal(data: NotificationData) {
        Log.i("JobAlert", "[6] showInternal() — agregando vista al WindowManager")
        val owner = ServiceLifecycleOwner().also { it.start() }
        currentOwner = owner

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE
        )

        val view = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                JobAlertTheme(darkTheme = settingsRepository.darkMode) {
                    AlertOverlayContent(data = data, onAcknowledge = { dismiss() })
                }
            }
        }

        try {
            windowManager.addView(view, params)
            currentView = view
            Log.i("JobAlert", "[6] WindowManager.addView OK — overlay visible")
        } catch (e: Exception) {
            Log.e("JobAlert", "[6] ERROR al agregar overlay: ${e.message}", e)
            currentOwner?.destroy()
            currentOwner = null
            return
        }

        if (settingsRepository.soundEnabled) playSound()
        if (settingsRepository.vibrationEnabled) vibrate()
    }

    fun dismiss() {
        stopSound()
        currentView?.let { windowManager.removeView(it) }
        currentView = null
        currentOwner?.destroy()
        currentOwner = null
        queue.removeFirstOrNull()?.let { showInternal(it) }
    }

    private fun playSound() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ringtone = RingtoneManager.getRingtone(context, uri)
        ringtone?.play()
    }

    private fun stopSound() {
        ringtone?.stop()
        ringtone = null
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val pattern = longArrayOf(0, 400, 150, 400)
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            vibrator.vibrate(pattern, -1)
        }
    }
}

@Composable
fun AlertOverlayContent(data: NotificationData, onAcknowledge: () -> Unit) {
    val timeStr = remember(data) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = LocalAlertColor.current
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .padding(top = 48.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Encabezado fijo
            Text(
                text = "ALERTA",
                style = MaterialTheme.typography.displayMedium,
                color = Color.White
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = timeStr,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(24.dp))

            // Cuerpo del mensaje — scrollable
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "De: ${data.sender}",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = data.subject,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (data.snippet.isNotBlank() && data.snippet != data.subject) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = data.snippet,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Botón siempre visible en la parte inferior
            Button(
                onClick = onAcknowledge,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Text(
                    text = "ATENDIDO",
                    style = MaterialTheme.typography.titleLarge,
                    color = LocalAlertColor.current
                )
            }
        }
    }
}
