package org.opensleep.app.service.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import org.opensleep.app.data.local.db.dao.AlarmDao
import org.opensleep.app.data.repository.UserPreferencesRepository
import org.opensleep.app.service.tracking.NightTimeService
import org.opensleep.app.service.tracking.SonarSleepSensorSource
import org.opensleep.app.service.tracking.TrackingStateHolder
import javax.inject.Inject

@AndroidEntryPoint
class AlarmRingingService : Service() {

    @Inject lateinit var alarmDao: AlarmDao
    @Inject lateinit var alarmScheduler: AlarmScheduler
    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository
    @Inject lateinit var sonarSource: SonarSleepSensorSource
    @Inject lateinit var trackingStateHolder: TrackingStateHolder

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaPlayer: MediaPlayer? = null
    private var fadeJob: Job? = null
    private var gestureJob: Job? = null
    private var vibrator: Vibrator? = null
    private var currentAlarmId: Long = -1L
    private var currentAlarmLabel: String? = null

    companion object {
        const val ACTION_START_RINGING = "org.opensleep.app.ACTION_START_RINGING"
        const val ACTION_SNOOZE = "org.opensleep.app.ACTION_SNOOZE"
        const val ACTION_DISMISS = "org.opensleep.app.ACTION_DISMISS"
        const val EXTRA_ALARM_LABEL = "extra_alarm_label"
        private const val CHANNEL_ID = "opensleep_alarm_channel"
        private const val NOTIFICATION_ID = 2002
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RINGING -> {
                currentAlarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
                currentAlarmLabel = intent.getStringExtra(EXTRA_ALARM_LABEL)
                startForegroundAlarm()
                scope.launch { startPlaybackAndVibration(currentAlarmId) }
            }
            ACTION_SNOOZE -> {
                val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, currentAlarmId)
                if (alarmId != -1L) {
                    alarmScheduler.scheduleSnooze(alarmId, snoozeMinutes = 9)
                }
                stopSelfGracefully()
            }
            ACTION_DISMISS -> {
                if (trackingStateHolder.state.value.isTracking) {
                    val stopTracking = Intent(this, NightTimeService::class.java).apply {
                        action = NightTimeService.ACTION_STOP_TRACKING
                    }
                    ContextCompat.startForegroundService(this, stopTracking)
                }
                stopSelfGracefully()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundAlarm() {
        createNotificationChannel()
        val title = currentAlarmLabel ?: "Đã đến giờ thức dậy"
        val fullScreenIntent = Intent().apply {
            setClassName(this@AlarmRingingService, "org.opensleep.app.ui.alarm.AlarmRingingActivity")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, currentAlarmId)
            putExtra(EXTRA_ALARM_LABEL, title)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozePi = buildActionPi(ACTION_SNOOZE, 1, currentAlarmId)
        val dismissPi = buildActionPi(ACTION_DISMISS, 2, currentAlarmId)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText("Nhấn để tắt hoặc hoãn báo thức")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(0, "Hoãn 9p", snoozePi)
            .addAction(0, "Tắt báo thức", dismissPi)
            .setOngoing(true)
            .build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else 0

        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private suspend fun startPlaybackAndVibration(alarmId: Long) {
        val config = withContext(Dispatchers.IO) {
            if (alarmId != -1L) alarmDao.getAlarmById(alarmId) else null
        }
        val prefs = withContext(Dispatchers.IO) { userPreferencesRepository.getSnapshot() }
        val customUri = prefs.alarmRingtoneUri?.let { Uri.parse(it) }

        val uri = config?.ringtoneUri?.let { Uri.parse(it) }
            ?: customUri
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // Bắt đầu ngay từ 0.30f để nghe rõ dứt khoát như đồng hồ báo thức thật, tăng lên 1.0f trong 12 giây
        val fadeSeconds = 12

        runCatching {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmRingingService, uri)
                isLooping = true
                prepare()
                setVolume(0.30f, 0.30f)
                start()
            }
        }

        fadeJob?.cancel()
        fadeJob = scope.launch {
            val steps = fadeSeconds * 2
            for (i in 1..steps) {
                val progress = i.toFloat() / steps
                val vol = (0.30f + 0.70f * progress).coerceIn(0.30f, 1.0f)
                mediaPlayer?.setVolume(vol, vol)
                delay(500L)
            }
        }

        if (config?.vibrateEnabled != false) {
            startVibration()
        }

        gestureJob?.cancel()
        if (config?.snoozeGestureEnabled != false) {
            gestureJob = scope.launch {
                if (trackingStateHolder.state.value.isTracking) {
                    sonarSource.gestureSnoozeEvents.collect {
                        val snoozeIntent = Intent(this@AlarmRingingService, AlarmRingingService::class.java).apply {
                            action = ACTION_SNOOZE
                            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                        }
                        startService(snoozeIntent)
                    }
                }
            }
        }
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 500, 200, 500, 200, 500, 800)
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    private fun stopSelfGracefully() {
        fadeJob?.cancel()
        gestureJob?.cancel()
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
        vibrator?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        fadeJob?.cancel()
        gestureJob?.cancel()
        mediaPlayer?.release()
        vibrator?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun buildActionPi(action: String, reqCode: Int, alarmId: Long): PendingIntent {
        val intent = Intent(this, AlarmRingingService::class.java).apply {
            this.action = action
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
        }
        return PendingIntent.getService(
            this,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(CHANNEL_ID, "Báo thức OpenSleep", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }
    }
}