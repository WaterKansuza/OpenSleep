package org.opensleep.app.service.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import org.opensleep.app.data.local.db.dao.AlarmDao
import org.opensleep.app.data.local.db.dao.SleepDebtDao
import org.opensleep.app.data.local.db.entity.AlarmConfigEntity
import org.opensleep.app.domain.engine.SleepDebtEngine
import java.time.DayOfWeek
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmDao: AlarmDao,
    private val sleepDebtDao: SleepDebtDao,
    private val sleepDebtEngine: SleepDebtEngine
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        const val ACTION_TRIGGER_HARD_ALARM = "org.opensleep.app.ACTION_TRIGGER_HARD_ALARM"
        const val ACTION_ENTER_SMART_WINDOW = "org.opensleep.app.ACTION_ENTER_SMART_WINDOW"
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_IS_SNOOZE = "extra_is_snooze"
    }

    fun canScheduleExact(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    suspend fun scheduleAlarm(alarm: AlarmConfigEntity): Long? {
        if (!alarm.isEnabled) {
            cancelAlarm(alarm.id)
            return null
        }

        val triggerTimeMs = computeNextTriggerMillis(alarm)
        val currentDebt = sleepDebtDao.observeLatestDebtSnapshot().firstOrNull()?.cumulativeDebtMinutes ?: 0
        val effectiveSmartWindow = sleepDebtEngine.computeDebtAwareSmartWindow(
            baseWindowMinutes = alarm.smartWindowMinutes,
            debtAwareOverride = alarm.debtAwareOverride,
            cumulativeDebtMinutes = currentDebt
        )

        // 1. Đặt Hard Deadline bằng setAlarmClock (Đảm bảo luôn reo bất chấp Doze Mode)
        val hardPendingIntent = buildAlarmPendingIntent(alarm.id, ACTION_TRIGGER_HARD_ALARM, isSnooze = false)
        val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTimeMs, hardPendingIntent)
        if (canScheduleExact()) {
            alarmManager.setAlarmClock(alarmClockInfo, hardPendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, hardPendingIntent)
        }

        // 2. Đặt mốc mở cửa sổ Smart Wake (nếu effectiveSmartWindow > 0)
        if (effectiveSmartWindow > 0) {
            val smartStartMs = triggerTimeMs - effectiveSmartWindow * 60_000L
            if (smartStartMs > System.currentTimeMillis()) {
                val smartPendingIntent = buildAlarmPendingIntent(alarm.id, ACTION_ENTER_SMART_WINDOW, isSnooze = false)
                if (canScheduleExact()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, smartStartMs, smartPendingIntent)
                }
            }
        } else {
            cancelSmartWindowPendingIntent(alarm.id)
        }

        return triggerTimeMs
    }

    fun scheduleSnooze(alarmId: Long, snoozeMinutes: Int = 9) {
        val triggerMs = System.currentTimeMillis() + snoozeMinutes * 60_000L
        val pendingIntent = buildAlarmPendingIntent(alarmId, ACTION_TRIGGER_HARD_ALARM, isSnooze = true)
        val info = AlarmManager.AlarmClockInfo(triggerMs, pendingIntent)
        if (canScheduleExact()) {
            alarmManager.setAlarmClock(info, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent)
        }
    }

    fun cancelAlarm(alarmId: Long) {
        alarmManager.cancel(buildAlarmPendingIntent(alarmId, ACTION_TRIGGER_HARD_ALARM, isSnooze = false))
        cancelSmartWindowPendingIntent(alarmId)
    }

    private fun cancelSmartWindowPendingIntent(alarmId: Long) {
        alarmManager.cancel(buildAlarmPendingIntent(alarmId, ACTION_ENTER_SMART_WINDOW, isSnooze = false))
    }

    suspend fun rescheduleAllEnabledAlarms() {
        alarmDao.getEnabledAlarms().forEach { scheduleAlarm(it) }
    }

    fun computeNextTriggerMillis(alarm: AlarmConfigEntity, now: ZonedDateTime = ZonedDateTime.now()): Long {
        var candidate = now.withHour(alarm.hour).withMinute(alarm.minute).withSecond(0).withNano(0)
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusDays(1)
        }

        // daysOfWeekBitmask == 0 nghĩa là báo thức 1 lần
        if (alarm.daysOfWeekBitmask == 0) {
            return candidate.toInstant().toEpochMilli()
        }

        // Tìm ngày phù hợp gần nhất trong 7 ngày tới (bit 0 = MONDAY .. bit 6 = SUNDAY)
        for (offset in 0..7) {
            val check = candidate.plusDays(offset.toLong())
            val dayBit = dayOfWeekToBit(check.dayOfWeek)
            if ((alarm.daysOfWeekBitmask and (1 shl dayBit)) != 0) {
                return check.toInstant().toEpochMilli()
            }
        }
        return candidate.toInstant().toEpochMilli()
    }

    private fun dayOfWeekToBit(day: DayOfWeek): Int = when (day) {
        DayOfWeek.MONDAY -> 0
        DayOfWeek.TUESDAY -> 1
        DayOfWeek.WEDNESDAY -> 2
        DayOfWeek.THURSDAY -> 3
        DayOfWeek.FRIDAY -> 4
        DayOfWeek.SATURDAY -> 5
        DayOfWeek.SUNDAY -> 6
    }

    private fun buildAlarmPendingIntent(alarmId: Long, action: String, isSnooze: Boolean): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_IS_SNOOZE, isSnooze)
        }
        val requestCode = (alarmId.toInt() * 10) + if (action == ACTION_ENTER_SMART_WINDOW) 1 else 0
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
