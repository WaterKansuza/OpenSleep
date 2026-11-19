package org.opensleep.app.service.tracking

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import org.opensleep.app.data.local.db.SessionType
import org.opensleep.app.data.local.db.SleepStage
import org.opensleep.app.data.local.db.TrackingMode
import org.opensleep.app.data.local.db.WakeMode
import org.opensleep.app.data.local.db.dao.SleepSessionDao
import org.opensleep.app.data.local.db.entity.SleepEpochEntity
import org.opensleep.app.data.local.db.entity.SleepSessionEntity
import org.opensleep.app.data.repository.SleepDebtRepository
import org.opensleep.app.domain.engine.SleepDebtEngine
import org.opensleep.app.domain.engine.SleepStagingEngine
import org.opensleep.app.service.alarm.AlarmRingingService
import org.opensleep.app.service.alarm.AlarmScheduler
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@AndroidEntryPoint
class NightTimeService : Service() {

    @Inject lateinit var sleepSessionDao: SleepSessionDao
    @Inject lateinit var sleepDebtRepository: SleepDebtRepository
    @Inject lateinit var sleepDebtEngine: SleepDebtEngine
    @Inject lateinit var sleepStagingEngine: SleepStagingEngine
    @Inject lateinit var trackingStateHolder: TrackingStateHolder
    @Inject lateinit var alarmScheduler: AlarmScheduler
    @Inject lateinit var sensorSource: SleepSensorSource

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var trackingJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var activeSessionId: Long = -1L
    private var smartWakeTriggered = false
    private val epochBuffer = mutableListOf<SleepEpochEntity>()
    private val recentEpochs = ArrayDeque<SleepEpochEntity>(10)
    private var totalEpochCount = 0

    companion object {
        const val ACTION_START_TRACKING = "org.opensleep.app.ACTION_START_TRACKING"
        const val ACTION_STOP_TRACKING = "org.opensleep.app.ACTION_STOP_TRACKING"
        const val EXTRA_SESSION_TYPE = "extra_session_type"
        const val EXTRA_WAKE_MODE = "extra_wake_mode"

        private const val CHANNEL_ID = "opensleep_tracking_channel"
        private const val NOTIFICATION_ID = 1001
        private const val EPOCH_INTERVAL_MS = 30_000L // 30 giây / Epoch
        private const val WAKELOCK_TIMEOUT_MS = 12 * 60 * 60 * 1000L // Tối đa 12 giờ
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                if (trackingJob?.isActive == true) return START_STICKY
                val typeName = intent.getStringExtra(EXTRA_SESSION_TYPE)
                val sessionType = runCatching { SessionType.valueOf(typeName ?: "NIGHT") }
                    .getOrDefault(SessionType.NIGHT)
                val wakeModeName = intent.getStringExtra(EXTRA_WAKE_MODE)
                val wakeMode = runCatching { WakeMode.valueOf(wakeModeName ?: "NATURAL_NO_ALARM") }
                    .getOrDefault(WakeMode.NATURAL_NO_ALARM)
                startTrackingPipeline(sessionType, wakeMode)
            }
            ACTION_STOP_TRACKING -> {
                if (activeSessionId != -1L) {
                    serviceScope.launch { finalizeAndStop() }
                } else {
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun startTrackingPipeline(sessionType: SessionType, wakeMode: WakeMode) {
        acquireWakeLock()
        promoteToForeground()
        smartWakeTriggered = false
        recentEpochs.clear()

        trackingJob = serviceScope.launch {
            val nowMs = System.currentTimeMillis()
            val zoneOffsetSec = ZoneId.systemDefault().rules.getOffset(java.time.Instant.ofEpochMilli(nowMs)).totalSeconds
            val initialSession = SleepSessionEntity(
                referenceDateEpochDay = LocalDate.now().toEpochDay(),
                sessionType = sessionType,
                trackingMode = sensorSource.mode,
                wakeMode = wakeMode,
                startTimeMs = nowMs,
                endTimeMs = nowMs,
                asleepTimeMs = nowMs,
                wakeUpTimeMs = nowMs,
                zoneOffsetSeconds = zoneOffsetSec,
                isIncomplete = true
            )
            activeSessionId = withContext(Dispatchers.IO) {
                sleepSessionDao.insertSession(initialSession)
            }

            trackingStateHolder.update {
                it.copy(
                    isTracking = true,
                    activeSessionId = activeSessionId,
                    startTimeMs = nowMs,
                    trackingMode = sensorSource.mode,
                    wakeMode = wakeMode,
                    currentStage = SleepStage.AWAKE
                )
            }

            sensorSource.start()
            var windowStart = System.currentTimeMillis()

            while (isActive) {
                delay(EPOCH_INTERVAL_MS)
                val windowEnd = System.currentTimeMillis()
                val reading = sensorSource.collectLatestEpochReading(windowStart, windowEnd)
                windowStart = windowEnd

                // Phân tầng thời gian thực có ngữ cảnh lịch sử từ SleepStagingEngine
                totalEpochCount++
                val stage = sleepStagingEngine.classifyRealtimeEpoch(reading, recentEpochs.toList(), totalEpochCount)
                val epoch = SleepEpochEntity(
                    sessionId = activeSessionId,
                    timestampMs = windowEnd,
                    stage = stage,
                    motionIntensity = reading.motionIntensity,
                    breathingRateBpm = reading.breathingRateBpm,
                    confidence = reading.confidence
                )
                epochBuffer.add(epoch)

                if (recentEpochs.size >= 10) {
                    recentEpochs.removeFirst()
                }
                recentEpochs.addLast(epoch)

                // Ghi DB theo lô 10 epoch (5 phút/lần) để bảo vệ bộ nhớ flash & tiết kiệm pin
                if (epochBuffer.size >= 10) {
                    flushEpochBuffer()
                }

                trackingStateHolder.update {
                    it.copy(
                        currentStage = stage,
                        latestMotionIntensity = reading.motionIntensity,
                        latestBreathingBpm = reading.breathingRateBpm
                    )
                }

                // Kiểm tra Smart Wake Window
                checkSmartWakeTrigger(stage)
            }
        }
    }

    private fun checkSmartWakeTrigger(currentStage: SleepStage) {
        if (smartWakeTriggered) return
        val uiState = trackingStateHolder.state.value
        if (!uiState.isInSmartWakeWindow) return

        val elapsedMin = (System.currentTimeMillis() - uiState.startTimeMs) / 60_000L
        // Đảm bảo đã theo dõi ít nhất 20 phút và đang ở giấc ngủ nông hoặc tỉnh nhẹ
        if (elapsedMin >= 20 && (currentStage == SleepStage.LIGHT || currentStage == SleepStage.AWAKE)) {
            smartWakeTriggered = true
            trackingStateHolder.update { it.copy(wakeMode = WakeMode.SMART_ALARM) }
            val alarmId = uiState.activeAlarmId ?: -1L
            if (alarmId != -1L) {
                alarmScheduler.cancelAlarm(alarmId)
            }
            val ringIntent = Intent(this, AlarmRingingService::class.java).apply {
                action = AlarmRingingService.ACTION_START_RINGING
                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            }
            ContextCompat.startForegroundService(this, ringIntent)
        }
    }

    private suspend fun flushEpochBuffer() {
        if (epochBuffer.isEmpty()) return
        val copy = epochBuffer.toList()
        epochBuffer.clear()
        withContext(Dispatchers.IO) {
            sleepSessionDao.insertEpochs(copy)
        }
    }

    private suspend fun finalizeAndStop() {
        trackingJob?.cancel()
        sensorSource.stop()
        flushEpochBuffer()

        val sessionId = activeSessionId
        activeSessionId = -1L

        if (sessionId != -1L) {
            withContext(Dispatchers.IO) {
                val session = sleepSessionDao.getSessionById(sessionId)
                val rawEpochs = sleepSessionDao.getEpochsForSession(sessionId)
                if (session != null) {
                    val endMs = System.currentTimeMillis()
                    val refDay = LocalDate.now().toEpochDay()

                    // Hậu xử lý toàn đêm (Webster Smoothing Rules)
                    val epochs = sleepStagingEngine.postProcessNightEpochs(rawEpochs)
                    if (epochs.isNotEmpty()) {
                        sleepSessionDao.insertEpochs(epochs) // Ghi đè bản đã làm mượt vào DB
                    }
                    val lightMs = epochs.count { it.stage == SleepStage.LIGHT } * EPOCH_INTERVAL_MS
                    val deepMs = epochs.count { it.stage == SleepStage.DEEP } * EPOCH_INTERVAL_MS
                    val remMs = epochs.count { it.stage == SleepStage.REM } * EPOCH_INTERVAL_MS
                    val awakeMs = epochs.count { it.stage == SleepStage.AWAKE } * EPOCH_INTERVAL_MS
                    val totalSleepMs = lightMs + deepMs + remMs
                    val microAwakenings = countMicroAwakenings(epochs)

                    val firstAsleep = epochs.firstOrNull { it.stage != SleepStage.AWAKE }?.timestampMs ?: session.startTimeMs
                    val lastWake = epochs.lastOrNull { it.stage != SleepStage.AWAKE }?.timestampMs ?: endMs

                    val finalWakeMode = trackingStateHolder.state.value.wakeMode

                    val draftSession = session.copy(
                        referenceDateEpochDay = refDay,
                        wakeMode = finalWakeMode,
                        endTimeMs = endMs,
                        asleepTimeMs = firstAsleep,
                        wakeUpTimeMs = lastWake,
                        awakeDurationMs = awakeMs,
                        sleepDurationMs = totalSleepMs,
                        lightSleepMs = lightMs,
                        deepSleepMs = deepMs,
                        remSleepMs = remMs,
                        microAwakeningsCount = microAwakenings,
                        isIncomplete = false
                    )
                    val quality = sleepDebtEngine.computeSessionQualityFactor(draftSession)
                    sleepSessionDao.updateSession(draftSession.copy(sleepQualityFactor = quality))

                    // Cập nhật ngay Nợ ngủ cho người dùng
                    sleepDebtRepository.recalculateAfterSessionChange(refDay)
                }
            }
        }

        // Bật Morning Check-in cho người dùng
        trackingStateHolder.update { TrackingUiState(pendingCheckInSessionId = sessionId) }
        recentEpochs.clear()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun countMicroAwakenings(epochs: List<SleepEpochEntity>): Int {
        var count = 0
        for (i in 1 until epochs.size) {
            if (epochs[i - 1].stage != SleepStage.AWAKE && epochs[i].stage == SleepStage.AWAKE) {
                count++
            }
        }
        return count
    }

    private fun promoteToForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(CHANNEL_ID, "Theo dõi giấc ngủ", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(ch)
        }

        val stopIntent = Intent(this, NightTimeService::class.java).apply { action = ACTION_STOP_TRACKING }
        val stopPi = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("OpenSleep đang theo dõi giấc ngủ")
            .setContentText("Xử lý 100% cục bộ, không kết nối mạng")
            .setOngoing(true)
            .addAction(0, "Thức dậy & Lưu", stopPi)
            .build()

        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else 0

        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OpenSleep::NightTimeWakeLock").apply {
                setReferenceCounted(false)
                acquire(WAKELOCK_TIMEOUT_MS)
            }
        }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        trackingJob?.cancel()
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }
}
