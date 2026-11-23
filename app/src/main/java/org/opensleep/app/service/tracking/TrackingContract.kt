package org.opensleep.app.service.tracking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.opensleep.app.data.local.db.SleepStage
import org.opensleep.app.data.local.db.SoundCategory
import org.opensleep.app.data.local.db.TrackingMode
import org.opensleep.app.data.local.db.WakeMode
import javax.inject.Inject
import javax.inject.Singleton

data class EpochReading(
    val timestampMs: Long,
    val motionIntensity: Float,
    val soundEnergyDb: Float,
    val breathingRateBpm: Float?,
    val confidence: Float = 1.0f
)

data class DetectedSoundEvent(
    val timestampMs: Long,
    val durationSeconds: Int,
    val category: SoundCategory,
    val confidence: Float,
    val decibelPeak: Float
)

interface SleepSensorSource {
    val mode: TrackingMode
    suspend fun start()
    suspend fun stop()
    fun collectLatestEpochReading(windowStartMs: Long, windowEndMs: Long): EpochReading
    fun drainDetectedSoundEvents(): List<DetectedSoundEvent> = emptyList()
}

data class TrackingUiState(
    val isTracking: Boolean = false,
    val activeSessionId: Long? = null,
    val startTimeMs: Long = 0L,
    val trackingMode: TrackingMode = TrackingMode.PASSIVE,
    val wakeMode: WakeMode = WakeMode.NATURAL_NO_ALARM,
    val currentStage: SleepStage = SleepStage.AWAKE,
    val latestMotionIntensity: Float = 0f,
    val latestBreathingBpm: Float? = null,
    val isInSmartWakeWindow: Boolean = false,
    val activeAlarmId: Long? = null,
    val pendingCheckInSessionId: Long? = null
)

@Singleton
class TrackingStateHolder @Inject constructor() {
    private val _state = MutableStateFlow(TrackingUiState())
    val state: StateFlow<TrackingUiState> = _state.asStateFlow()

    fun update(transform: (TrackingUiState) -> TrackingUiState) {
        _state.value = transform(_state.value)
    }

    fun clearPendingCheckIn() {
        _state.value = _state.value.copy(pendingCheckInSessionId = null)
    }

    fun reset() {
        _state.value = TrackingUiState()
    }
}