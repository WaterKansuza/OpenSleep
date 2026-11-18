package org.opensleep.app.service.tracking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.opensleep.app.data.local.db.SleepStage
import org.opensleep.app.data.local.db.TrackingMode
import javax.inject.Inject
import javax.inject.Singleton

data class EpochReading(
    val timestampMs: Long,
    val motionIntensity: Float,    // [0.0 .. 1.0+]
    val soundEnergyDb: Float,      // Năng lượng âm thanh môi trường
    val breathingRateBpm: Float?,  // Chỉ có giá trị khi dùng Sonar
    val confidence: Float = 1.0f
)

interface SleepSensorSource {
    val mode: TrackingMode
    suspend fun start()
    suspend fun stop()
    fun collectLatestEpochReading(windowStartMs: Long, windowEndMs: Long): EpochReading
}

data class TrackingUiState(
    val isTracking: Boolean = false,
    val activeSessionId: Long? = null,
    val startTimeMs: Long = 0L,
    val trackingMode: TrackingMode = TrackingMode.PASSIVE,
    val currentStage: SleepStage = SleepStage.AWAKE,
    val latestMotionIntensity: Float = 0f,
    val latestBreathingBpm: Float? = null,
    val isInSmartWakeWindow: Boolean = false,
    val activeAlarmId: Long? = null
)

@Singleton
class TrackingStateHolder @Inject constructor() {
    private val _state = MutableStateFlow(TrackingUiState())
    val state: StateFlow<TrackingUiState> = _state.asStateFlow()

    fun update(transform: (TrackingUiState) -> TrackingUiState) {
        _state.value = transform(_state.value)
    }

    fun reset() {
        _state.value = TrackingUiState()
    }
}
