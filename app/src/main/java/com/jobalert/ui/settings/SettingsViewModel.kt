package com.jobalert.ui.settings

import androidx.lifecycle.ViewModel
import com.jobalert.JobAlertApp
import com.jobalert.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    private val _soundEnabled = MutableStateFlow(repository.soundEnabled)
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    fun setSoundEnabled(enabled: Boolean) {
        repository.soundEnabled = enabled
        _soundEnabled.value = enabled
    }

    private val _vibrationEnabled = MutableStateFlow(repository.vibrationEnabled)
    val vibrationEnabled: StateFlow<Boolean> = _vibrationEnabled.asStateFlow()

    fun setVibrationEnabled(enabled: Boolean) {
        repository.vibrationEnabled = enabled
        _vibrationEnabled.value = enabled
    }

    private val _quietHoursEnabled = MutableStateFlow(repository.quietHoursEnabled)
    val quietHoursEnabled: StateFlow<Boolean> = _quietHoursEnabled.asStateFlow()

    fun setQuietHoursEnabled(enabled: Boolean) {
        repository.quietHoursEnabled = enabled
        _quietHoursEnabled.value = enabled
    }

    private val _quietHoursStartHour = MutableStateFlow(repository.quietHoursStartHour)
    val quietHoursStartHour: StateFlow<Int> = _quietHoursStartHour.asStateFlow()

    fun setQuietHoursStartHour(hour: Int) {
        repository.quietHoursStartHour = hour
        _quietHoursStartHour.value = hour
    }

    private val _quietHoursEndHour = MutableStateFlow(repository.quietHoursEndHour)
    val quietHoursEndHour: StateFlow<Int> = _quietHoursEndHour.asStateFlow()

    fun setQuietHoursEndHour(hour: Int) {
        repository.quietHoursEndHour = hour
        _quietHoursEndHour.value = hour
    }

    private val _minIntervalMinutes = MutableStateFlow(repository.minIntervalMinutes)
    val minIntervalMinutes: StateFlow<Int> = _minIntervalMinutes.asStateFlow()

    fun setMinIntervalMinutes(minutes: Int) {
        repository.minIntervalMinutes = minutes
        _minIntervalMinutes.value = minutes
    }

    private val _imapPollIntervalMinutes = MutableStateFlow(repository.imapPollIntervalMinutes)
    val imapPollIntervalMinutes: StateFlow<Int> = _imapPollIntervalMinutes.asStateFlow()

    fun setImapPollIntervalMinutes(minutes: Int, app: JobAlertApp) {
        repository.imapPollIntervalMinutes = minutes
        _imapPollIntervalMinutes.value = minutes
        app.reschedulePolling()
    }

    private val _darkMode = MutableStateFlow(repository.darkMode)
    val darkMode: StateFlow<Boolean> = _darkMode.asStateFlow()

    fun setDarkMode(enabled: Boolean, app: JobAlertApp) {
        repository.darkMode = enabled
        _darkMode.value = enabled
        app.setDarkMode(enabled)
    }

    private val _alarmSoundUri = MutableStateFlow(repository.alarmSoundUri)
    val alarmSoundUri: StateFlow<String?> = _alarmSoundUri.asStateFlow()

    fun setAlarmSoundUri(uri: String?) {
        repository.alarmSoundUri = uri
        _alarmSoundUri.value = uri
    }

    private val _snippetMaxChars = MutableStateFlow(repository.snippetMaxChars)
    val snippetMaxChars: StateFlow<Int> = _snippetMaxChars.asStateFlow()

    fun setSnippetMaxChars(chars: Int) {
        repository.snippetMaxChars = chars
        _snippetMaxChars.value = chars
    }

    private val _muteUntil = MutableStateFlow(repository.muteUntil)
    val muteUntil: StateFlow<Long> = _muteUntil.asStateFlow()

    fun muteFor(durationMs: Long) {
        val until = System.currentTimeMillis() + durationMs
        repository.muteUntil = until
        _muteUntil.value = until
    }

    fun unmute() {
        repository.muteUntil = 0L
        _muteUntil.value = 0L
    }
}
