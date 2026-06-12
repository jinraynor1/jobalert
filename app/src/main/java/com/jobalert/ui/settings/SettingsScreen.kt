package com.jobalert.ui.settings

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jobalert.JobAlertApp
import com.jobalert.ui.theme.JobAlertTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val INTERVAL_OPTIONS = listOf(0, 1, 2, 5, 10, 15, 30, 60)
private val SNIPPET_OPTIONS = listOf(100, 200, 500, 1000, 2000, 3000, 4000)

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as JobAlertApp
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory { initializer { SettingsViewModel(app.settingsRepository) } }
    )

    val darkMode by viewModel.darkMode.collectAsState()
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    val alarmSoundUri by viewModel.alarmSoundUri.collectAsState()
    val vibrationEnabled by viewModel.vibrationEnabled.collectAsState()
    val quietHoursEnabled by viewModel.quietHoursEnabled.collectAsState()
    val quietHoursStartHour by viewModel.quietHoursStartHour.collectAsState()
    val quietHoursEndHour by viewModel.quietHoursEndHour.collectAsState()
    val minIntervalMinutes by viewModel.minIntervalMinutes.collectAsState()
    val muteUntil by viewModel.muteUntil.collectAsState()
    val imapPollIntervalMinutes by viewModel.imapPollIntervalMinutes.collectAsState()
    val snippetMaxChars by viewModel.snippetMaxChars.collectAsState()

    val ringtonePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            viewModel.setAlarmSoundUri(uri?.toString())
        }
    }

    val soundTitle = remember(alarmSoundUri) {
        val s = alarmSoundUri
        if (s == null) "Predeterminado del sistema"
        else runCatching {
            RingtoneManager.getRingtone(context, Uri.parse(s))?.getTitle(context)
        }.getOrNull() ?: "Predeterminado del sistema"
    }

    SettingsScreenContent(
        darkMode = darkMode,
        soundEnabled = soundEnabled,
        soundTitle = soundTitle,
        vibrationEnabled = vibrationEnabled,
        quietHoursEnabled = quietHoursEnabled,
        quietHoursStartHour = quietHoursStartHour,
        quietHoursEndHour = quietHoursEndHour,
        minIntervalMinutes = minIntervalMinutes,
        muteUntil = muteUntil,
        imapPollIntervalMinutes = imapPollIntervalMinutes,
        snippetMaxChars = snippetMaxChars,
        onDarkModeChange = { viewModel.setDarkMode(it, app) },
        onSoundEnabledChange = { viewModel.setSoundEnabled(it) },
        onRingtonePickerLaunch = {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Sonido de la alarma")
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(
                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                    alarmSoundUri?.let { Uri.parse(it) }
                )
            }
            ringtonePicker.launch(intent)
        },
        onVibrationEnabledChange = { viewModel.setVibrationEnabled(it) },
        onQuietHoursEnabledChange = { viewModel.setQuietHoursEnabled(it) },
        onQuietHoursStartHourChange = { viewModel.setQuietHoursStartHour(it) },
        onQuietHoursEndHourChange = { viewModel.setQuietHoursEndHour(it) },
        onMinIntervalMinutesChange = { viewModel.setMinIntervalMinutes(it) },
        onMuteFor = { viewModel.muteFor(it) },
        onUnmute = { viewModel.unmute() },
        onImapPollIntervalMinutesChange = { viewModel.setImapPollIntervalMinutes(it, app) },
        onSnippetMaxCharsChange = { viewModel.setSnippetMaxChars(it) }
    )
}

@Composable
private fun SettingsScreenContent(
    darkMode: Boolean,
    soundEnabled: Boolean,
    soundTitle: String,
    vibrationEnabled: Boolean,
    quietHoursEnabled: Boolean,
    quietHoursStartHour: Int,
    quietHoursEndHour: Int,
    minIntervalMinutes: Int,
    muteUntil: Long,
    imapPollIntervalMinutes: Int,
    snippetMaxChars: Int,
    onDarkModeChange: (Boolean) -> Unit,
    onSoundEnabledChange: (Boolean) -> Unit,
    onRingtonePickerLaunch: () -> Unit,
    onVibrationEnabledChange: (Boolean) -> Unit,
    onQuietHoursEnabledChange: (Boolean) -> Unit,
    onQuietHoursStartHourChange: (Int) -> Unit,
    onQuietHoursEndHourChange: (Int) -> Unit,
    onMinIntervalMinutesChange: (Int) -> Unit,
    onMuteFor: (Long) -> Unit,
    onUnmute: () -> Unit,
    onImapPollIntervalMinutesChange: (Int) -> Unit,
    onSnippetMaxCharsChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineSmall)

        ToggleCard(
            title = "Modo oscuro",
            description = "Usa el tema oscuro en toda la app",
            checked = darkMode,
            onCheckedChange = onDarkModeChange
        )

        val isMuted = muteUntil > System.currentTimeMillis()
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Silenciar alertas", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Suprime el overlay pero sigue guardando en historial",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                if (isMuted) {
                    val timeStr = remember(muteUntil) {
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(muteUntil))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Silenciado hasta $timeStr", style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = onUnmute) { Text("Reactivar") }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "1h" to 60 * 60_000L,
                            "2h" to 2 * 60 * 60_000L,
                            "8h" to 8 * 60 * 60_000L
                        ).forEach { (label, ms) ->
                            OutlinedButton(
                                onClick = { onMuteFor(ms) },
                                modifier = Modifier.weight(1f)
                            ) { Text(label) }
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Intervalo de revisión de correo", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Frecuencia con la que JobAlert consulta los correos nuevos",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                val pollOptions = listOf(15, 30, 60)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pollOptions.forEach { minutes ->
                        val selected = imapPollIntervalMinutes == minutes
                        if (selected) {
                            Button(
                                onClick = {},
                                modifier = Modifier.weight(1f)
                            ) { Text("${minutes}m") }
                        } else {
                            OutlinedButton(
                                onClick = { onImapPollIntervalMinutesChange(minutes) },
                                modifier = Modifier.weight(1f)
                            ) { Text("${minutes}m") }
                        }
                    }
                }
            }
        }

        ToggleCard(
            title = "Sonido de alarma",
            description = "Reproduce el sonido de alarma del sistema al recibir una alerta",
            checked = soundEnabled,
            onCheckedChange = onSoundEnabledChange
        )

        if (soundEnabled) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = onRingtonePickerLaunch
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Tono de la alarma", style = MaterialTheme.typography.titleSmall)
                    Text(soundTitle, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        ToggleCard(
            title = "Vibración",
            description = "Vibra al recibir una alerta, incluso con el teléfono en silencio",
            checked = vibrationEnabled,
            onCheckedChange = onVibrationEnabledChange
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Horario silencioso", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "El overlay no interrumpe en este rango de horas",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(checked = quietHoursEnabled, onCheckedChange = onQuietHoursEnabledChange)
                }
                if (quietHoursEnabled) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        HourStepper(
                            label = "Inicio",
                            hour = quietHoursStartHour,
                            onDecrement = { onQuietHoursStartHourChange((quietHoursStartHour - 1 + 24) % 24) },
                            onIncrement = { onQuietHoursStartHourChange((quietHoursStartHour + 1) % 24) }
                        )
                        HourStepper(
                            label = "Fin",
                            hour = quietHoursEndHour,
                            onDecrement = { onQuietHoursEndHourChange((quietHoursEndHour - 1 + 24) % 24) },
                            onIncrement = { onQuietHoursEndHourChange((quietHoursEndHour + 1) % 24) }
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Longitud del fragmento de correo",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "Número máximo de caracteres del cuerpo del correo guardados en el historial",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                val rows = SNIPPET_OPTIONS.chunked(4)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rows.forEach { rowOptions ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowOptions.forEach { chars ->
                                val selected = snippetMaxChars == chars
                                val label = if (chars >= 1000) "${chars / 1000}k" else "${chars}c"
                                if (selected) {
                                    Button(
                                        onClick = {},
                                        modifier = Modifier.weight(1f)
                                    ) { Text(label) }
                                } else {
                                    OutlinedButton(
                                        onClick = { onSnippetMaxCharsChange(chars) },
                                        modifier = Modifier.weight(1f)
                                    ) { Text(label) }
                                }
                            }
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Intervalo mínimo entre alertas", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Suprime el overlay si ya se mostró una alerta recientemente. El historial sigue guardándose.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                val rows = INTERVAL_OPTIONS.chunked(4)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rows.forEach { rowOptions ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowOptions.forEach { minutes ->
                                val selected = minIntervalMinutes == minutes
                                val label = if (minutes == 0) "Off" else "${minutes}m"
                                if (selected) {
                                    Button(
                                        onClick = {},
                                        modifier = Modifier.weight(1f)
                                    ) { Text(label) }
                                } else {
                                    OutlinedButton(
                                        onClick = { onMinIntervalMinutesChange(minutes) },
                                        modifier = Modifier.weight(1f)
                                    ) { Text(label) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun HourStepper(label: String, hour: Int, onDecrement: () -> Unit, onIncrement: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDecrement) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Reducir hora")
            }
            Text(
                text = String.format("%02d:00", hour),
                style = MaterialTheme.typography.titleMedium
            )
            IconButton(onClick = onIncrement) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Aumentar hora")
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SettingsScreenPreview() {
    JobAlertTheme(darkTheme = false) {
        SettingsScreenContent(
            darkMode = false,
            soundEnabled = true,
            soundTitle = "Alarma predeterminada",
            vibrationEnabled = false,
            quietHoursEnabled = true,
            quietHoursStartHour = 23,
            quietHoursEndHour = 7,
            minIntervalMinutes = 5,
            muteUntil = 0L,
            imapPollIntervalMinutes = 15,
            snippetMaxChars = 500,
            onDarkModeChange = {},
            onSoundEnabledChange = {},
            onRingtonePickerLaunch = {},
            onVibrationEnabledChange = {},
            onQuietHoursEnabledChange = {},
            onQuietHoursStartHourChange = {},
            onQuietHoursEndHourChange = {},
            onMinIntervalMinutesChange = {},
            onMuteFor = {},
            onUnmute = {},
            onImapPollIntervalMinutesChange = {},
            onSnippetMaxCharsChange = {}
        )
    }
}
