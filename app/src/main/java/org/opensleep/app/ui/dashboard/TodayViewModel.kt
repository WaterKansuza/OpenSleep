package org.opensleep.app.ui.dashboard

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.opensleep.app.data.local.db.SessionType
import org.opensleep.app.data.local.db.WakeMode
import org.opensleep.app.data.local.db.dao.AlarmDao
import org.opensleep.app.data.local.db.dao.SleepSessionDao
import org.opensleep.app.data.local.db.entity.AlarmConfigEntity
import org.opensleep.app.data.local.db.entity.DreamJournalEntity
import org.opensleep.app.data.local.db.entity.MoodEntryEntity
import org.opensleep.app.data.local.db.entity.SessionNoteCrossRef
import org.opensleep.app.data.local.db.entity.SleepNoteEntity
import org.opensleep.app.data.repository.SleepDebtRepository
import org.opensleep.app.data.repository.UserPreferencesRepository
import org.opensleep.app.domain.engine.SleepDebtEngine
import org.opensleep.app.domain.engine.WakeTimePlannerEngine
import org.opensleep.app.domain.model.BedtimeRecommendation
import org.opensleep.app.domain.model.SleepDebtAnalysis
import org.opensleep.app.service.alarm.AlarmScheduler
import org.opensleep.app.service.alarm.BedtimeReminderScheduler
import org.opensleep.app.service.tracking.NightTimeService
import org.opensleep.app.service.tracking.SonarSleepSensorSource
import org.opensleep.app.service.tracking.TrackingStateHolder
import org.opensleep.app.service.tracking.TrackingUiState
import java.time.LocalDate
import javax.inject.Inject

enum class TodaySleepModeOption {
    ALARM,
    NO_ALARM,
    NAP
}

data class TodayUiState(
    val analysis: SleepDebtAnalysis? = null,
    val recommendation: BedtimeRecommendation? = null,
    val selectedSleepMode: TodaySleepModeOption = TodaySleepModeOption.ALARM,
    val smartWakeEnabled: Boolean = true,
    val effectiveSmartWindowMin: Int = 30,
    val optionalCheckInEnabled: Boolean = false,
    val powerNapDurationMin: Int = 20,
    val tracking: TrackingUiState = TrackingUiState(),
    val availableNotes: List<SleepNoteEntity> = emptyList(),
    val pendingCheckInSessionId: Long? = null
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sleepDebtRepository: SleepDebtRepository,
    private val sleepDebtEngine: SleepDebtEngine,
    private val wakeTimePlannerEngine: WakeTimePlannerEngine,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val sleepSessionDao: SleepSessionDao,
    private val alarmDao: AlarmDao,
    private val alarmScheduler: AlarmScheduler,
    private val bedtimeReminderScheduler: BedtimeReminderScheduler,
    private val trackingStateHolder: TrackingStateHolder,
    sonarSensorSource: SonarSleepSensorSource
) : ViewModel() {

    private val todayEpochFlow = MutableStateFlow(LocalDate.now().toEpochDay())
    private val selectedModeFlow = MutableStateFlow(TodaySleepModeOption.ALARM)

    val liveWaveFlow: SharedFlow<Float> = sonarSensorSource.liveWaveFlow

    fun refreshTodayIfNeeded() {
        val nowEpoch = LocalDate.now().toEpochDay()
        if (todayEpochFlow.value != nowEpoch) {
            todayEpochFlow.value = nowEpoch
        }
    }

    init {
        viewModelScope.launch {
            val prefs = userPreferencesRepository.getSnapshot()
            selectedModeFlow.value = if (prefs.defaultUseAlarm) {
                TodaySleepModeOption.ALARM
            } else {
                TodaySleepModeOption.NO_ALARM
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TodayUiState> = todayEpochFlow.flatMapLatest { todayEpoch ->
        combine(
            sleepDebtRepository.observeCurrentAnalysis(todayEpoch),
            sleepSessionDao.observeSessionsInRange(todayEpoch - 21, todayEpoch),
            userPreferencesRepository.preferencesFlow,
            trackingStateHolder.state,
            selectedModeFlow,
            sleepSessionDao.observeAllNotes()
        ) { array ->
            val analysis = array[0] as SleepDebtAnalysis
            @Suppress("UNCHECKED_CAST")
            val sessions = array[1] as List<org.opensleep.app.data.local.db.entity.SleepSessionEntity>
            val prefs = array[2] as org.opensleep.app.data.repository.UserPreferencesState
            val tracking = array[3] as TrackingUiState
            val mode = array[4] as TodaySleepModeOption
            @Suppress("UNCHECKED_CAST")
            val notes = array[5] as List<SleepNoteEntity>

            val recommendation = wakeTimePlannerEngine.computeRecommendation(
                prefs = prefs,
                personalSleepNeedMinutes = analysis.activeSleepNeedMinutes,
                recentSessions = sessions,
                estimatedDebtMinutes = analysis.cumulativeDebtMinutes,
                today = LocalDate.ofEpochDay(todayEpoch),
                debtStatusLevel = analysis.statusLevel,
                typicalWakeMinutesOfDay = analysis.circadianForecast.morningPeakStartMin - 120
            )
            val effWindow = sleepDebtEngine.computeDebtAwareSmartWindow(
                baseWindowMinutes = if (prefs.smartWakeEnabled) 30 else 0,
                debtAwareOverride = true,
                cumulativeDebtMinutes = analysis.cumulativeDebtMinutes
            )

            TodayUiState(
                analysis = analysis,
                recommendation = recommendation,
                selectedSleepMode = mode,
                smartWakeEnabled = prefs.smartWakeEnabled,
                effectiveSmartWindowMin = effWindow,
                optionalCheckInEnabled = prefs.optionalCheckInEnabled,
                powerNapDurationMin = prefs.powerNapDurationMinutes,
                tracking = tracking,
                availableNotes = notes,
                pendingCheckInSessionId = tracking.pendingCheckInSessionId
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    fun selectSleepMode(option: TodaySleepModeOption) {
        selectedModeFlow.value = option
        if (option != TodaySleepModeOption.NAP) {
            viewModelScope.launch {
                userPreferencesRepository.setDefaultSleepMode(
                    useAlarm = option == TodaySleepModeOption.ALARM,
                    smartWakeEnabled = uiState.value.smartWakeEnabled
                )
            }
        }
    }

    fun updateActiveWakeTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            val isRestSchedule = uiState.value.recommendation?.isWeekendScheduleApplied == true
            if (isRestSchedule) {
                userPreferencesRepository.setWeekendSchedule(true, hour, minute)
            } else {
                userPreferencesRepository.setWeekdayWakeTime(hour, minute)
            }
            syncBedtimeReminderWithLatestPrefs()
        }
    }

    fun updateCustomBedtime(hour: Int, minute: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setCustomBedtime(hour, minute)
            syncBedtimeReminderWithLatestPrefs()
        }
    }

    fun resetBedtimeToRecommended() {
        viewModelScope.launch {
            userPreferencesRepository.clearCustomBedtime()
            syncBedtimeReminderWithLatestPrefs()
        }
    }

    private suspend fun syncBedtimeReminderWithLatestPrefs() {
        val prefs = userPreferencesRepository.getSnapshot()
        val analysis = uiState.value.analysis
        val today = LocalDate.ofEpochDay(todayEpochFlow.value)
        val sessions = sleepSessionDao.getSessionsInRange(todayEpochFlow.value - 21, todayEpochFlow.value)
        val rec = wakeTimePlannerEngine.computeRecommendation(
            prefs = prefs,
            personalSleepNeedMinutes = analysis?.activeSleepNeedMinutes ?: 480,
            recentSessions = sessions,
            estimatedDebtMinutes = analysis?.cumulativeDebtMinutes ?: 0,
            today = today
        )
        bedtimeReminderScheduler.syncReminder(prefs.bedtimeReminderEnabled, rec.recommendedBedtimeMinutesOfDay)
    }

    fun setSmartWakeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val useAlarm = selectedModeFlow.value == TodaySleepModeOption.ALARM
            userPreferencesRepository.setDefaultSleepMode(useAlarm, enabled)
        }
    }

    fun startSelectedSleep() {
        when (selectedModeFlow.value) {
            TodaySleepModeOption.NAP -> startPowerNap()
            TodaySleepModeOption.ALARM -> startNightSleep(useAlarm = true)
            TodaySleepModeOption.NO_ALARM -> startNightSleep(useAlarm = false)
        }
    }

    private fun startNightSleep(useAlarm: Boolean) {
        viewModelScope.launch {
            val rec = uiState.value.recommendation
            val wakeMin = rec?.targetWakeMinutesOfDay ?: (6 * 60 + 45)
            val wakeHour = wakeMin / 60
            val wakeMinute = wakeMin % 60
            val smartOn = uiState.value.smartWakeEnabled

            val wakeMode = if (useAlarm) WakeMode.HARD_ALARM else WakeMode.NATURAL_NO_ALARM
            if (useAlarm) {
                val existingId = alarmDao.observeAlarms().firstOrNull()
                    ?.firstOrNull { it.label == "Báo thức ngủ đêm" }?.id ?: 0L
                val alarm = AlarmConfigEntity(
                    id = existingId,
                    label = "Báo thức ngủ đêm",
                    hour = wakeHour,
                    minute = wakeMinute,
                    isEnabled = true,
                    smartWindowMinutes = if (smartOn) 30 else 0,
                    debtAwareOverride = true
                )
                val alarmId = alarmDao.upsertAlarm(alarm)
                alarmScheduler.scheduleAlarm(alarm.copy(id = alarmId))
                trackingStateHolder.update { it.copy(activeAlarmId = alarmId) }
            }

            val intent = Intent(context, NightTimeService::class.java).apply {
                action = NightTimeService.ACTION_START_TRACKING
                putExtra(NightTimeService.EXTRA_SESSION_TYPE, SessionType.NIGHT.name)
                putExtra(NightTimeService.EXTRA_WAKE_MODE, wakeMode.name)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    fun startPowerNap() {
        val napDuration = uiState.value.powerNapDurationMin
        val intent = Intent(context, NightTimeService::class.java).apply {
            action = NightTimeService.ACTION_START_TRACKING
            putExtra(NightTimeService.EXTRA_SESSION_TYPE, SessionType.NAP.name)
            putExtra(NightTimeService.EXTRA_WAKE_MODE, WakeMode.HARD_ALARM.name)
            putExtra(NightTimeService.EXTRA_NAP_DURATION_MIN, napDuration)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    /** Quick Power Nap theo thời lượng đã cài đặt (mặc định 20p) — hết giờ sẽ reo chuông báo thức */
    fun startQuickPowerNap() {
        startPowerNap()
    }

    fun stopTracking() {
        val intent = Intent(context, NightTimeService::class.java).apply {
            action = NightTimeService.ACTION_STOP_TRACKING
        }
        context.startService(intent)
    }

    fun submitQuickFeeling(sessionId: Long, energyScore: Int) {
        viewModelScope.launch {
            val session = sleepSessionDao.getSessionById(sessionId)
            if (session != null) {
                sleepSessionDao.updateSession(session.copy(sleepRating = energyScore))
                sleepSessionDao.insertMood(
                    MoodEntryEntity(
                        sessionId = sessionId,
                        dateEpochDay = session.referenceDateEpochDay,
                        morningEnergyScore = energyScore,
                        moodLabel = if (energyScore >= 4) "Khỏe khoắn" else "Vẫn mệt",
                        createdAtMs = System.currentTimeMillis()
                    )
                )
                sleepDebtRepository.recalculateAfterSessionChange(session.referenceDateEpochDay)
            }
            trackingStateHolder.clearPendingCheckIn()
        }
    }

    fun submitMorningCheckIn(
        sessionId: Long,
        energyScore: Int,
        selectedNoteIds: Set<Long>,
        dreamContent: String
    ) {
        viewModelScope.launch {
            val session = sleepSessionDao.getSessionById(sessionId)
            if (session != null) {
                sleepSessionDao.updateSession(session.copy(sleepRating = energyScore))
                sleepSessionDao.insertMood(
                    MoodEntryEntity(
                        sessionId = sessionId,
                        dateEpochDay = session.referenceDateEpochDay,
                        morningEnergyScore = energyScore,
                        moodLabel = when (energyScore) {
                            5 -> "Rất tỉnh táo"
                            4 -> "Khỏe khoắn"
                            3 -> "Bình thường"
                            2 -> "Hơi uể oải"
                            else -> "Kiệt sức"
                        },
                        createdAtMs = System.currentTimeMillis()
                    )
                )
                selectedNoteIds.forEach { noteId ->
                    sleepSessionDao.attachNoteToSession(SessionNoteCrossRef(sessionId, noteId))
                }
                if (dreamContent.isNotBlank()) {
                    sleepSessionDao.insertDream(
                        DreamJournalEntity(
                            sessionId = sessionId,
                            dateEpochDay = session.referenceDateEpochDay,
                            content = dreamContent.trim(),
                            createdAtMs = System.currentTimeMillis()
                        )
                    )
                }
                sleepDebtRepository.recalculateAfterSessionChange(session.referenceDateEpochDay)
            }
            trackingStateHolder.clearPendingCheckIn()
        }
    }

    fun dismissMorningCheckIn() {
        trackingStateHolder.clearPendingCheckIn()
    }
}