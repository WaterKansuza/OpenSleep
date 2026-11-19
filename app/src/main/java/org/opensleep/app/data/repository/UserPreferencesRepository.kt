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
    val weekendWakeHour: Int = 7,
    val weekendWakeMinute: Int = 30,
    val defaultUseAlarm: Boolean = true,
    val smartWakeEnabled: Boolean = true,
    val optionalCheckInEnabled: Boolean = false,
    val defaultLatencyFallbackMin: Int = 15,
    val fontKey: String = "sans",
    val demoSessionIds: Set<Long> = emptySet(),
    val dismissedWarningEpochs: Map<String, Long> = emptyMap()
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val WEEKDAY_WAKE_HOUR = intPreferencesKey("weekday_wake_hour")
        val WEEKDAY_WAKE_MINUTE = intPreferencesKey("weekday_wake_minute")
        val WEEKEND_ENABLED = booleanPreferencesKey("weekend_schedule_enabled")
        val WEEKEND_WAKE_HOUR = intPreferencesKey("weekend_wake_hour")
        val WEEKEND_WAKE_MINUTE = intPreferencesKey("weekend_wake_minute")
        val DEFAULT_USE_ALARM = booleanPreferencesKey("default_use_alarm")
        val SMART_WAKE_ENABLED = booleanPreferencesKey("smart_wake_enabled")
        val OPTIONAL_CHECKIN_ENABLED = booleanPreferencesKey("optional_checkin_enabled")
        val DEFAULT_LATENCY_MIN = intPreferencesKey("default_latency_fallback_min")
        val FONT_KEY = stringPreferencesKey("font_key")
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
            weekendWakeHour = this[Keys.WEEKEND_WAKE_HOUR] ?: 7,
            weekendWakeMinute = this[Keys.WEEKEND_WAKE_MINUTE] ?: 30,
            defaultUseAlarm = this[Keys.DEFAULT_USE_ALARM] ?: true,
            smartWakeEnabled = this[Keys.SMART_WAKE_ENABLED] ?: true,
            optionalCheckInEnabled = this[Keys.OPTIONAL_CHECKIN_ENABLED] ?: false,
            defaultLatencyFallbackMin = this[Keys.DEFAULT_LATENCY_MIN] ?: 15,
            fontKey = this[Keys.FONT_KEY] ?: "sans",
            demoSessionIds = demoIds,
            dismissedWarningEpochs = dismissed
        )
    }
}