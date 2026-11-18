package org.opensleep.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.opensleep.app.data.local.db.dao.SleepDebtDao
import org.opensleep.app.data.local.db.entity.DailySleepDebtSnapshotEntity
import org.opensleep.app.data.local.db.entity.UserSleepProfileEntity
import org.opensleep.app.data.repository.SleepDebtRepository
import org.opensleep.app.domain.model.SleepDebtAnalysis
import org.opensleep.app.service.tracking.SonarNativeBridge
import org.opensleep.app.service.tracking.SonarSleepSensorSource
import org.opensleep.app.service.tracking.TrackingStateHolder
import org.opensleep.app.service.tracking.TrackingUiState
import javax.inject.Inject

data class CalibrationState(
    val isRunning: Boolean = false,
    val lastSnrDb: Float? = null,
    val isSupported: Boolean? = null
)

data class DashboardUiState(
    val analysis: SleepDebtAnalysis? = null,
    val history14Days: List<DailySleepDebtSnapshotEntity> = emptyList(),
    val tracking: TrackingUiState = TrackingUiState(),
    val calibration: CalibrationState = CalibrationState()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    sleepDebtRepository: SleepDebtRepository,
    private val sleepDebtDao: SleepDebtDao,
    private val trackingStateHolder: TrackingStateHolder,
    private val sonarBridge: SonarNativeBridge,
    private val sonarSensorSource: SonarSleepSensorSource
) : ViewModel() {

    private val calibrationState = MutableStateFlow(CalibrationState())

    val liveWaveFlow: SharedFlow<Float> = sonarSensorSource.liveWaveFlow

    val uiState: StateFlow<DashboardUiState> = combine(
        sleepDebtRepository.observeCurrentAnalysis(),
        sleepDebtRepository.observeLast14DaysSnapshots(),
        trackingStateHolder.state,
        calibrationState
    ) { analysis, snapshots, tracking, calib ->
        DashboardUiState(analysis, snapshots, tracking, calib)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun runSonarCalibration() {
        viewModelScope.launch {
            calibrationState.value = CalibrationState(isRunning = true)
            val (supported, snr) = sonarBridge.runHardwareCalibrationTest()
            val profile = sleepDebtDao.getProfile() ?: UserSleepProfileEntity()
            sleepDebtDao.upsertProfile(profile.copy(sonarHardwareSupported = supported))
            calibrationState.value = CalibrationState(isRunning = false, lastSnrDb = snr, isSupported = supported)
        }
    }
}
