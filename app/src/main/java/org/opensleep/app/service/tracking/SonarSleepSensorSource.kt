package org.opensleep.app.service.tracking

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.opensleep.app.data.local.db.TrackingMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class SonarSleepSensorSource @Inject constructor(
    private val sonarBridge: SonarNativeBridge,
    private val passiveFallback: PassiveSleepSensorSource
) : SleepSensorSource {

    override val mode: TrackingMode = TrackingMode.SONAR

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollJob: Job? = null

    // Stream cho màn hình Wave Debugger (Phần 6)
    private val _liveWaveFlow = MutableSharedFlow<Float>(extraBufferCapacity = 64)
    val liveWaveFlow: SharedFlow<Float> = _liveWaveFlow.asSharedFlow()

    // Stream phát hiện vẫy tay Snooze không chạm
    private val _gestureSnoozeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val gestureSnoozeEvents: SharedFlow<Unit> = _gestureSnoozeEvents.asSharedFlow()

    private val lock = Any()
    private var motionSum = 0.0
    private var motionCount = 0
    private var lastValidBpm = 0f
    private var confidenceSum = 0.0
    private var lastGestureAtMs = 0L
    private var runningNative = false

    override suspend fun start() {
        synchronized(lock) {
            motionSum = 0.0
            motionCount = 0
            lastValidBpm = 0f
            confidenceSum = 0.0
        }

        runningNative = sonarBridge.startSonar(18750.0)
        if (!runningNative) {
            // Dự phòng an toàn nếu audio stream bị app khác chiếm dụng
            passiveFallback.start()
            return
        }

        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                delay(200L) // Poll 5 lần/giây từ Ring Buffer 100Hz
                val frame = sonarBridge.pollMetrics()
                _liveWaveFlow.tryEmit(frame.latestBreathWave)

                // Phát hiện xung vẫy tay trên không (Contactless Gesture Impulse)
                if (frame.gestureImpulse > 1.8f) {
                    val now = System.currentTimeMillis()
                    if (now - lastGestureAtMs > 2500L) { // Debounce 2.5s
                        lastGestureAtMs = now
                        _gestureSnoozeEvents.tryEmit(Unit)
                    }
                }

                synchronized(lock) {
                    motionSum += frame.motionIntensity
                    confidenceSum += frame.confidence
                    motionCount++
                    if (frame.breathingBpm in 6.0f..30.0f) {
                        lastValidBpm = frame.breathingBpm
                    }
                }
            }
        }
    }

    override suspend fun stop() {
        pollJob?.cancel()
        pollJob = null
        if (runningNative) {
            sonarBridge.stopSonar()
            runningNative = false
        } else {
            passiveFallback.stop()
        }
    }

    override fun collectLatestEpochReading(windowStartMs: Long, windowEndMs: Long): EpochReading {
        if (!runningNative) {
            return passiveFallback.collectLatestEpochReading(windowStartMs, windowEndMs)
        }

        val avgMotion: Float
        val avgConf: Float
        val bpm: Float?

        synchronized(lock) {
            avgMotion = if (motionCount > 0) (motionSum / motionCount).toFloat() else 0f
            avgConf = if (motionCount > 0) (confidenceSum / motionCount).toFloat() else 0.5f
            bpm = if (lastValidBpm > 0f) lastValidBpm else null
            motionSum = 0.0
            confidenceSum = 0.0
            motionCount = 0
        }

        return EpochReading(
            timestampMs = windowEndMs,
            motionIntensity = max(0f, avgMotion),
            soundEnergyDb = -60f,
            breathingRateBpm = bpm,
            confidence = avgConf
        )
    }
}
