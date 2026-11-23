package org.opensleep.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private val Context.openSleepDataStore by preferencesDataStore(name = "opensleep_prefs")

data class UserPreferencesState(
    val weekdayWakeHour: Int = 6,
    val weekdayWakeMinute: Int = 45,
    val weekendScheduleEnabled: Boolean = true,
    val restDaysBitmask: Int = DEFAULT_REST_DAYS_BITMASK,
    val weekendWakeHour: Int = 7,
    val weekendWakeMinute: Int = 30,
    val customBedtimeEnabled: Boolean = false,
    val customBedtimeHour: Int = 22,
    val customBedtimeMinute: Int = 30,
    val defaultUseAlarm: Boolean = true,
    val smartWakeEnabled: Boolean = true,
    val optionalCheckInEnabled: Boolean = false,
    val defaultLatencyFallbackMin: Int = 15,
    val fontKey: String = "sans",
    val soundClassificationEnabled: Boolean = true,
    val soundRetentionDays: Int = 30,
    val bedtimeReminderEnabled: Boolean = false,
    val autoCheckUpdatesEnabled: Boolean = true,
    val dismissedUpdateTag: String? = null,
    val demoSessionIds: Set<Long> = emptySet(),
    val dismissedWarningEpochs: Map<String, Long> = emptyMap()
) {
    companion object {
        // Bit 0 = Thứ 2 ... Bit 5 = Thứ 7, Bit 6 = Chủ nhật
        const val DEFAULT_REST_DAYS_BITMASK = (1 shl 5) or (1 shl 6)
    }
}

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val WEEKDAY_WAKE_HOUR = intPreferencesKey("weekday_wake_hour")
        val WEEKDAY_WAKE_MINUTE = intPreferencesKey("weekday_wake_minute")
        val WEEKEND_ENABLED = booleanPreferencesKey("weekend_schedule_enabled")
        val REST_DAYS_BITMASK = intPreferencesKey("rest_days_bitmask")
        val WEEKEND_WAKE_HOUR = intPreferencesKey("weekend_wake_hour")
        val WEEKEND_WAKE_MINUTE = intPreferencesKey("weekend_wake_minute")
        val CUSTOM_BEDTIME_ENABLED = booleanPreferencesKey("custom_bedtime_enabled")
        val CUSTOM_BEDTIME_HOUR = intPreferencesKey("custom_bedtime_hour")
        val CUSTOM_BEDTIME_MINUTE = intPreferencesKey("custom_bedtime_minute")
        val DEFAULT_USE_ALARM = booleanPreferencesKey("default_use_alarm")
        val SMART_WAKE_ENABLED = booleanPreferencesKey("smart_wake_enabled")
        val OPTIONAL_CHECKIN_ENABLED = booleanPreferencesKey("optional_checkin_enabled")
        val DEFAULT_LATENCY_MIN = intPreferencesKey("default_latency_fallback_min")
        val FONT_KEY = stringPreferencesKey("font_key")
        val SOUND_CLASSIFICATION_ENABLED = booleanPreferencesKey("sound_classification_enabled")
        val SOUND_RETENTION_DAYS = intPreferencesKey("sound_retention_days")
        val BEDTIME_REMINDER_ENABLED = booleanPreferencesKey("bedtime_reminder_enabled")
        val AUTO_CHECK_UPDATES_ENABLED = booleanPreferencesKey("auto_check_updates_enabled")
        val DISMISSED_UPDATE_TAG = stringPreferencesKey("dismissed_update_tag")
        val DEMO_SESSION_IDS = stringSetPreferencesKey("demo_session_ids")
        val DISMISSED_WARNINGS = stringSetPreferencesKey("dismissed_warnings")
    }

    val preferencesFlow: Flow<UserPreferencesState> = context.openSleepDataStore.data.map { prefs ->
        prefs.toState()
    }

    suspend fun getSnapshot(): UserPreferencesState =
        context.openSleepDataStore.data.first().toState()

    suspend fun setWeekdayWakeTime(hour: Int, minute: Int) {
        context.openSleepDataStore.edit {
            it[Keys.WEEKDAY_WAKE_HOUR] = hour.coerceIn(0, 23)
            it[Keys.WEEKDAY_WAKE_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    suspend fun setWeekendSchedule(enabled: Boolean, hour: Int? = null, minute: Int? = null) {
        context.openSleepDataStore.edit {
            it[Keys.WEEKEND_ENABLED] = enabled
            if (hour != null) it[Keys.WEEKEND_WAKE_HOUR] = hour.coerceIn(0, 23)
            if (minute != null) it[Keys.WEEKEND_WAKE_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    suspend fun setRestDaysBitmask(bitmask: Int) {
        context.openSleepDataStore.edit {
            it[Keys.REST_DAYS_BITMASK] = bitmask and 0b1111111
        }
    }

    suspend fun setCustomBedtime(hour: Int, minute: Int) {
        context.openSleepDataStore.edit {
            it[Keys.CUSTOM_BEDTIME_ENABLED] = true
            it[Keys.CUSTOM_BEDTIME_HOUR] = hour.coerceIn(0, 23)
            it[Keys.CUSTOM_BEDTIME_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    suspend fun clearCustomBedtime() {
        context.openSleepDataStore.edit {
            it[Keys.CUSTOM_BEDTIME_ENABLED] = false
        }
    }

    suspend fun setDefaultSleepMode(useAlarm: Boolean, smartWakeEnabled: Boolean) {
        context.openSleepDataStore.edit {
            it[Keys.DEFAULT_USE_ALARM] = useAlarm
            it[Keys.SMART_WAKE_ENABLED] = smartWakeEnabled
        }
    }

    suspend fun setOptionalCheckInEnabled(enabled: Boolean) {
        context.openSleepDataStore.edit {
            it[Keys.OPTIONAL_CHECKIN_ENABLED] = enabled
        }
    }

    suspend fun setDefaultLatencyFallbackMin(minutes: Int) {
        context.openSleepDataStore.edit {
            it[Keys.DEFAULT_LATENCY_MIN] = minutes.coerceIn(5, 45)
        }
    }

    suspend fun setFontKey(fontKey: String) {
        context.openSleepDataStore.edit {
            it[Keys.FONT_KEY] = fontKey
        }
    }

    suspend fun setSoundClassificationEnabled(enabled: Boolean) {
        context.openSleepDataStore.edit {
            it[Keys.SOUND_CLASSIFICATION_ENABLED] = enabled
        }
    }

    suspend fun setSoundRetentionDays(days: Int) {
        context.openSleepDataStore.edit {
            it[Keys.SOUND_RETENTION_DAYS] = days.coerceAtLeast(0)
        }
    }

    suspend fun setBedtimeReminderEnabled(enabled: Boolean) {
        context.openSleepDataStore.edit {
            it[Keys.BEDTIME_REMINDER_ENABLED] = enabled
        }
    }

    suspend fun setAutoCheckUpdatesEnabled(enabled: Boolean) {
        context.openSleepDataStore.edit {
            it[Keys.AUTO_CHECK_UPDATES_ENABLED] = enabled
        }
    }

    suspend fun setDismissedUpdateTag(tag: String?) {
        context.openSleepDataStore.edit {
            if (tag.isNullOrBlank()) {
                it.remove(Keys.DISMISSED_UPDATE_TAG)
            } else {
                it[Keys.DISMISSED_UPDATE_TAG] = tag
            }
        }
    }

    suspend fun markSessionAsDemo(sessionId: Long) {
        context.openSleepDataStore.edit {
            val current = it[Keys.DEMO_SESSION_IDS] ?: emptySet()
            it[Keys.DEMO_SESSION_IDS] = current + sessionId.toString()
        }
    }

    suspend fun unmarkSessionDemo(sessionId: Long) {
        context.openSleepDataStore.edit {
            val current = it[Keys.DEMO_SESSION_IDS] ?: emptySet()
            it[Keys.DEMO_SESSION_IDS] = current - sessionId.toString()
        }
    }

    suspend fun dismissWarning(warningKey: String, epochDay: Long = LocalDate.now().toEpochDay()) {
        context.openSleepDataStore.edit {
            val current = (it[Keys.DISMISSED_WARNINGS] ?: emptySet())
                .filterNot { entry -> entry.startsWith("${warningKey}|") }
                .toSet()
            it[Keys.DISMISSED_WARNINGS] = current + "${warningKey}|${epochDay}"
        }
    }

    private fun Preferences.toState(): UserPreferencesState {
        val demoIds = (this[Keys.DEMO_SESSION_IDS] ?: emptySet())
            .mapNotNull { it.toLongOrNull() }
            .toSet()
        val dismissed = (this[Keys.DISMISSED_WARNINGS] ?: emptySet())
            .mapNotNull { raw ->
                val parts = raw.split("|")
                val day = parts.getOrNull(1)?.toLongOrNull()
                if (parts.isNotEmpty() && day != null) parts[0] to day else null
            }
            .toMap()

        return UserPreferencesState(
            weekdayWakeHour = this[Keys.WEEKDAY_WAKE_HOUR] ?: 6,
            weekdayWakeMinute = this[Keys.WEEKDAY_WAKE_MINUTE] ?: 45,
            weekendScheduleEnabled = this[Keys.WEEKEND_ENABLED] ?: true,
            restDaysBitmask = this[Keys.REST_DAYS_BITMASK] ?: UserPreferencesState.DEFAULT_REST_DAYS_BITMASK,
            weekendWakeHour = this[Keys.WEEKEND_WAKE_HOUR] ?: 7,
            weekendWakeMinute = this[Keys.WEEKEND_WAKE_MINUTE] ?: 30,
            customBedtimeEnabled = this[Keys.CUSTOM_BEDTIME_ENABLED] ?: false,
            customBedtimeHour = this[Keys.CUSTOM_BEDTIME_HOUR] ?: 22,
            customBedtimeMinute = this[Keys.CUSTOM_BEDTIME_MINUTE] ?: 30,
            defaultUseAlarm = this[Keys.DEFAULT_USE_ALARM] ?: true,
            smartWakeEnabled = this[Keys.SMART_WAKE_ENABLED] ?: true,
            optionalCheckInEnabled = this[Keys.OPTIONAL_CHECKIN_ENABLED] ?: false,
            defaultLatencyFallbackMin = this[Keys.DEFAULT_LATENCY_MIN] ?: 15,
            fontKey = this[Keys.FONT_KEY] ?: "sans",
            soundClassificationEnabled = this[Keys.SOUND_CLASSIFICATION_ENABLED] ?: true,
            soundRetentionDays = this[Keys.SOUND_RETENTION_DAYS] ?: 30,
            bedtimeReminderEnabled = this[Keys.BEDTIME_REMINDER_ENABLED] ?: false,
            autoCheckUpdatesEnabled = this[Keys.AUTO_CHECK_UPDATES_ENABLED] ?: true,
            dismissedUpdateTag = this[Keys.DISMISSED_UPDATE_TAG],
            demoSessionIds = demoIds,
            dismissedWarningEpochs = dismissed
        )
    }
}