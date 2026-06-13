package com.jobalert.domain.repository

interface SettingsRepository {
    var soundEnabled: Boolean
    var vibrationEnabled: Boolean
    var muteUntil: Long
    val isMuted: Boolean
    var quietHoursEnabled: Boolean
    var quietHoursStartHour: Int
    var quietHoursEndHour: Int
    val isInQuietHours: Boolean
    var minIntervalMinutes: Int
    var lastOverlayShownAt: Long
    var imapPollIntervalMinutes: Int
    var darkMode: Boolean
    var alarmSoundUri: String?
    var snippetMaxChars: Int
    val isWithinMinInterval: Boolean
}
