package org.opensleep.app.ui.dashboard

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import org.opensleep.app.domain.engine.SleepDebtEngine
import org.opensleep.app.domain.model.SleepDebtAnalysis
import org.opensleep.app.service.alarm.AlarmScheduler
import org.opensleep.app.service.tracking.NightTimeService
import org.opensleep.app.service.tracking.SonarSleepSensorSource
import org.opensleep.app.service.tracking.TrackingStateHolder
import org.opensleep.app.service.tracking.TrackingUiState
import javax.inject.Inject

data class BedtimeConfigState(
    val useAlarm: Boolean = false, // Mặc định khuyến khích Không báo thức để đo Nợ ngủ chuẩn
    val alarmHour: Int = 7,
    val alarmMinute: Int = 0,
    val smartWakeEnabled: Boolean = true
)

data class TodayUiState(
    val analysis: SleepDebtAnalysis? = null,
    val tracking: TrackingUiState = TrackingUiState(),
    val bedtime: BedtimeConfigState = BedtimeConfigState(),
    val effectiveSmartWindowMin: Int = 30,
    val availableNotes: List<SleepNoteEntity> = emptyList(),
    val pendingCheckInSessionId: Long? = null
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sleepDebtRepository: SleepDebtRepository,
    private val sleepDebtEngine: SleepDebtEngine,
    private val sleepSessionDao: SleepSessionDao,
    private val alarmDao: AlarmDao,
    private val alarmScheduler: AlarmScheduler,
    private val trackingStateHolder: TrackingStateHolder,
    sonarSensorSource: SonarSleepSensorSource
) : ViewModel() {

    private val bedtimeConfig = MutableStateFlow(BedtimeConfigState())
    val liveWaveFlow: SharedFlow<Float> = sonarSensorSource.liveWaveFlow

    val uiState: StateFlow<TodayUiState> = combine(
        sleepDebtRepository.observeCurrentAnalysis(),
        trackingStateHolder.state,
        bedtimeConfig,
        sleepSessionDao.observeAllNotes()
    ) { analysis, tracking, bed, notes ->
        val debtMin = analysis.cumulativeDebtMinutes
        val effWindow = sleepDebtEngine.computeDebtAwareSmartWindow(
            baseWindowMinutes = if (bed.smartWakeEnabled) 30 else 0,
            debtAwareOverride = true,
            cumulativeDebtMinutes = debtMin
        )
        TodayUiState(
            analysis = analysis,
            tracking = tracking,
            bedtime = bed,
            effectiveSmartWindowMin = effWindow,
            availableNotes = notes,
            pendingCheckInSessionId = tracking.pendingCheckInSessionId
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    fun setUseAlarm(useAlarm: Boolean) {
        bedtimeConfig.update { it.copy(useAlarm = useAlarm) }
    }

    fun setAlarmTime(hour: Int, minute: Int) {
        bedtimeConfig.update { it.copy(alarmHour = hour, alarmMinute = minute) }
    }

    fun setSmartWakeEnabled(enabled: Boolean) {
        bedtimeConfig.update { it.copy(smartWakeEnabled = enabled) }
    }

    fun startNightSleep() {
        viewModelScope.launch {
            val cfg = bedtimeConfig.value
            val wakeMode = if (cfg.useAlarm) WakeMode.HARD_ALARM else WakeMode.NATURAL_NO_ALARM

            if (cfg.useAlarm) {
                // Cập nhật lại báo thức ngủ đêm nếu đã tồn tại để tránh tạo trùng nhiều dòng qua từng đêm
                val existingId = alarmDao.observeAlarms().firstOrNull()
                    ?.firstOrNull { it.label == "Báo thức ngủ đêm" }?.id ?: 0L

                val alarm = AlarmConfigEntity(
                    id = existingId,
                    label = "Báo thức ngủ đêm",
                    hour = cfg.alarmHour,
                    minute = cfg.alarmMinute,
                    isEnabled = true,
                    smartWindowMinutes = if (cfg.smartWakeEnabled) 30 else 0,
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
        val intent = Intent(context, NightTimeService::class.java).apply {
            action = NightTimeService.ACTION_START_TRACKING
            putExtra(NightTimeService.EXTRA_SESSION_TYPE, SessionType.NAP.name)
            putExtra(NightTimeService.EXTRA_WAKE_MODE, WakeMode.NATURAL_NO_ALARM.name)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopTracking() {
        val intent = Intent(context, NightTimeService::class.java).apply {
            action = NightTimeService.ACTION_STOP_TRACKING
        }
        context.startService(intent)
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