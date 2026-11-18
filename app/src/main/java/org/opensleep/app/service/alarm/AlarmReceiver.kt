package org.opensleep.app.service.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.opensleep.app.data.local.db.dao.AlarmDao
import org.opensleep.app.service.tracking.TrackingStateHolder
import javax.inject.Inject

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var trackingStateHolder: TrackingStateHolder
    @Inject lateinit var alarmDao: AlarmDao
    @Inject lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        if (alarmId == -1L) return

        when (intent.action) {
            AlarmScheduler.ACTION_ENTER_SMART_WINDOW -> {
                // Báo cho NightTimeService biết đã vào cửa sổ đánh thức nhẹ
                trackingStateHolder.update {
                    it.copy(isInSmartWakeWindow = true, activeAlarmId = alarmId)
                }
            }
            AlarmScheduler.ACTION_TRIGGER_HARD_ALARM -> {
                val serviceIntent = Intent(context, AlarmRingingService::class.java).apply {
                    action = AlarmRingingService.ACTION_START_RINGING
                    putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                }
                ContextCompat.startForegroundService(context, serviceIntent)

                // Nếu là báo thức lặp lại theo tuần -> tự đặt lịch cho tuần kế tiếp
                val isSnooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false)
                if (!isSnooze) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val alarm = alarmDao.getAlarmById(alarmId)
                            if (alarm != null && alarm.daysOfWeekBitmask != 0) {
                                alarmScheduler.scheduleAlarm(alarm)
                            }
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }
}
