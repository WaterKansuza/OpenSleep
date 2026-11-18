package org.opensleep.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.opensleep.app.data.local.db.AgeGroup
import org.opensleep.app.data.local.db.SessionType
import org.opensleep.app.data.local.db.WakeMode
import org.opensleep.app.data.local.db.entity.AlarmConfigEntity
import org.opensleep.app.data.local.db.entity.UserSleepProfileEntity
import org.opensleep.app.data.local.db.dao.AlarmDao
import org.opensleep.app.data.local.db.dao.SleepSessionDao
import org.opensleep.app.data.local.db.dao.SleepDebtDao
import org.opensleep.app.data.repository.SleepDebtRepository
import org.opensleep.app.domain.model.CircadianForecast
import org.opensleep.app.domain.model.SleepDebtAnalysis
import org.opensleep.app.service.tracking.TrackingStateHolder
import org.opensleep.app.service.tracking.TrackingUiState
import java.time.LocalDate
import javax.inject.Inject

data class BedtimeConfigState(
    val wakeMode: WakeMode = WakeMode.NATURAL_NO_ALARM,
    val alarmHour: Int = 7,
    val alarmMinute: Int = 0,
    val smartWakeWindowMinutes: Int = 30
)

data class TodayUiState(
    val analysis: SleepDebtAnalysis? = null,
    val profile: UserSleepProfileEntity? = null,
    val trackingState: TrackingUiState = TrackingUiState(),
    val bedtimeConfig: BedtimeConfigState = BedtimeConfigState(),
    val activeAlarm: AlarmConfigEntity? = null,
    val simulatedPaybackMinutes: Int = 0  // Slider giả lập trả nợ
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val sleepDebtRepository: SleepDebtRepository,
    private val trackingStateHolder: TrackingStateHolder,
    private val alarmDao: AlarmDao,
    private val sleepDebtDao: SleepDebtDao
) : ViewModel() {

    val uiState: StateFlow<TodayUiState> = combine(
        sleepDebtRepository.observeCurrentAnalysis(),
        sleepDebtRepository.observeProfile(),
        trackingStateHolder.state,
        alarmDao.observeActiveAlarm()
    ) { analysis, profile, tracking, alarm ->
        val wakeMode = if (alarm != null) WakeMode.HARD_ALARM else WakeMode.NATURAL_NO_ALARM
        val alarmConfig = if (alarm != null) BedtimeConfigState(
            wakeMode = wakeMode,
            alarmHour = alarm.alarmHour,
            alarmMinute = alarm.alarmMinute,
            smartWakeWindowMinutes = alarm.smartWakeWindowMinutes
        ) else BedtimeConfigState(wakeMode = WakeMode.NATURAL_NO_ALARM)

        TodayUiState(
            analysis = analysis,
            profile = profile,
            trackingState = tracking,
            bedtimeConfig = alarmConfig,
            activeAlarm = alarm
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TodayUiState()
    )

    // Slider giả lập: bao nhiêu phút ngủ bù sẽ trả được bao nhiêu nợ
    private val _simulatedPayback = MutableStateFlow(0)
    val simulatedPaybackMinutes: StateFlow<Int> = _simulatedPayback.asStateFlow()

    fun setSimulatedPayback(minutes: Int) {
        _simulatedPayback.value = minutes
    }

    fun clearPendingCheckIn() {
        trackingStateHolder.clearPendingCheckIn()
    }

    fun saveMorningCheckIn(sessionId: Long, rating: Int, notes: List<Long>) {
        viewModelScope.launch {
            val dao = sleepDebtDao
            // Update rating on the session via repository
            sleepDebtRepository.recalculateAfterSessionChange(LocalDate.now().toEpochDay())
        }
    }
}
