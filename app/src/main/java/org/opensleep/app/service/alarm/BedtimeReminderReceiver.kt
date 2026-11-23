package org.opensleep.app.service.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import org.opensleep.app.ui.MainActivity

class BedtimeReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "opensleep_bedtime_reminder"
        private const val NOTIFICATION_ID = 3001
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BedtimeReminderScheduler.ACTION_BEDTIME_REMINDER) return
        val bedtimeLabel = intent.getStringExtra(BedtimeReminderScheduler.EXTRA_BEDTIME_LABEL) ?: "sắp tới"

        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Nhắc giờ đi ngủ",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPi = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Sắp đến giờ đi ngủ ($bedtimeLabel)")
            .setContentText("Thư giãn nhẹ và chạm để mở OpenSleep khi bạn sẵn sàng ngủ.")
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }
}