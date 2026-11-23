package org.opensleep.app.service.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BedtimeReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        const val ACTION_BEDTIME_REMINDER = "org.opensleep.app.ACTION_BEDTIME_REMINDER"
        const val EXTRA_BEDTIME_LABEL = "extra_bedtime_label"
        private const val REQUEST_CODE = 7701
    }

    fun syncReminder(enabled: Boolean, bedtimeMinutesOfDay: Int) {
        if (!enabled) {
            cancelReminder()
            return
        }
        val reminderMinutes = ((bedtimeMinutesOfDay - 30) % 1440 + 1440) % 1440
        val hour = reminderMinutes / 60
        val minute = reminderMinutes % 60
        val bedtimeLabel = String.format("%02d:%02d", bedtimeMinutesOfDay / 60, bedtimeMinutesOfDay % 60)

        val now = ZonedDateTime.now()
        var target = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!target.isAfter(now)) {
            target = target.plusDays(1)
        }

        val triggerMs = target.toInstant().toEpochMilli()
        val pi = buildPendingIntent(bedtimeLabel)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        }
    }

    fun cancelReminder() {
        alarmManager.cancel(buildPendingIntent("22:30"))
    }

    private fun buildPendingIntent(bedtimeLabel: String): PendingIntent {
        val intent = Intent(context, BedtimeReminderReceiver::class.java).apply {
            action = ACTION_BEDTIME_REMINDER
            putExtra(EXTRA_BEDTIME_LABEL, bedtimeLabel)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}