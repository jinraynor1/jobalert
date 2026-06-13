package com.jobalert.data.repository

import android.content.Context
import com.jobalert.domain.repository.SettingsRepository
import java.util.Calendar

class SettingsRepositoryImpl(context: Context) : SettingsRepository {
    private val prefs = context.getSharedPreferences("jobalert_settings", Context.MODE_PRIVATE)

    override var soundEnabled: Boolean
        get() = prefs.getBoolean("sound_enabled", false)
        set(value) { prefs.edit().putBoolean("sound_enabled", value).apply() }

    override var vibrationEnabled: Boolean
        get() = prefs.getBoolean("vibration_enabled", false)
        set(value) { prefs.edit().putBoolean("vibration_enabled", value).apply() }

    override var muteUntil: Long
        get() = prefs.getLong("mute_until", 0L)
        set(value) { prefs.edit().putLong("mute_until", value).apply() }

    override val isMuted: Boolean
        get() = muteUntil > System.currentTimeMillis()

    override var quietHoursEnabled: Boolean
        get() = prefs.getBoolean("quiet_hours_enabled", false)
        set(value) { prefs.edit().putBoolean("quiet_hours_enabled", value).apply() }

    override var quietHoursStartHour: Int
        get() = prefs.getInt("quiet_hours_start", 23)
        set(value) { prefs.edit().putInt("quiet_hours_start", value).apply() }

    override var quietHoursEndHour: Int
        get() = prefs.getInt("quiet_hours_end", 7)
        set(value) { prefs.edit().putInt("quiet_hours_end", value).apply() }

    override val isInQuietHours: Boolean
        get() {
            if (!quietHoursEnabled) return false
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val start = quietHoursStartHour
            val end = quietHoursEndHour
            return if (start <= end) hour in start until end
            else hour >= start || hour < end  // overnight range
        }

    override var minIntervalMinutes: Int
        get() = prefs.getInt("min_interval_minutes", 0)
        set(value) { prefs.edit().putInt("min_interval_minutes", value).apply() }

    override var lastOverlayShownAt: Long
        get() = prefs.getLong("last_overlay_shown_at", 0L)
        set(value) { prefs.edit().putLong("last_overlay_shown_at", value).apply() }

    override var imapPollIntervalMinutes: Int
        get() = prefs.getInt("imap_poll_interval_minutes", 15)
        set(value) { prefs.edit().putInt("imap_poll_interval_minutes", value).apply() }

    override var darkMode: Boolean
        get() = prefs.getBoolean("dark_mode", false)
        set(value) { prefs.edit().putBoolean("dark_mode", value).apply() }

    override var alarmSoundUri: String?
        get() = prefs.getString("alarm_sound_uri", null)
        set(value) { prefs.edit().putString("alarm_sound_uri", value).apply() }

    override var snippetMaxChars: Int
        get() = prefs.getInt("snippet_max_chars", 500)
        set(value) { prefs.edit().putInt("snippet_max_chars", value).apply() }

    override val isWithinMinInterval: Boolean
        get() {
            val intervalMs = minIntervalMinutes * 60_000L
            if (intervalMs == 0L) return false
            return System.currentTimeMillis() - lastOverlayShownAt < intervalMs
        }
}
