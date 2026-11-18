package org.opensleep.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.opensleep.app.data.local.db.SessionType
import org.opensleep.app.data.local.db.TrackingMode
import org.opensleep.app.data.local.db.WakeMode
import org.opensleep.app.data.local.db.entity.DailySleepDebtSnapshotEntity
import org.opensleep.app.data.local.db.entity.SleepEpochEntity
import org.opensleep.app.data.local.db.entity.SleepSessionEntity
import org.opensleep.app.data.local.db.entity.SoundEventEntity
import org.opensleep.app.data.local.db.dao.SleepSessionDao
import org.opensleep.app.data.repository.SleepDebtRepository
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject

data class HistoryUiState(
    val snapshots14Days: List<DailySleepDebtSnapshotEntity> = emptyList(),
    val selectedEpochDay: Long = LocalDate.now().toEpochDay(),
    val sessionsForSelectedDay: List<SleepSessionEntity> = emptyList(),
    val epochsForSelectedSession: List<SleepEpochEntity> = emptyList(),
    val soundEventsForSelectedSession: List<SoundEventEntity> = emptyList(),
    val selectedSessionId: Long? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val sleepDebtRepository: SleepDebtRepository,
    private val sleepSessionDao: SleepSessionDao
) : ViewModel() {

    private val _selectedEpochDay = MutableStateFlow(LocalDate.now().toEpochDay())
    private val _selectedSessionId = MutableStateFlow<Long?>(null)
    private val _epochsForSession = MutableStateFlow<List<SleepEpochEntity>>(emptyList())
    private val _soundEventsForSession = MutableStateFlow<List<SoundEventEntity>>(emptyList())

    val uiState: StateFlow<HistoryUiState> = combine(
        sleepDebtRepository.observeLast14DaysSnapshots(),
        _selectedEpochDay,
        _selectedSessionId,
        _epochsForSession,
        _soundEventsForSession
    ) { snapshots, epochDay, sessionId, epochs, sounds ->
        HistoryUiState(
            snapshots14Days = snapshots,
            selectedEpochDay = epochDay,
            sessionsForSelectedDay = emptyList(), // loaded separately
            epochsForSelectedSession = epochs,
            soundEventsForSelectedSession = sounds,
            selectedSessionId = sessionId
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState()
    )

    // Sessions for selected day (suspend load)
    private val _sessionsForDay = MutableStateFlow<List<SleepSessionEntity>>(emptyList())
    val sessionsForSelectedDay: StateFlow<List<SleepSessionEntity>> = _sessionsForDay.asStateFlow()

    fun selectDay(epochDay: Long) {
        _selectedEpochDay.value = epochDay
        viewModelScope.launch {
            _sessionsForDay.value = sleepSessionDao.getSessionsInRange(epochDay, epochDay)
        }
    }

    fun selectSession(sessionId: Long) {
        _selectedSessionId.value = sessionId
        viewModelScope.launch {
            _epochsForSession.value = sleepSessionDao.getEpochsForSession(sessionId)
            _soundEventsForSession.value = sleepSessionDao.getSoundEventsForSession(sessionId)
        }
    }

    fun logManualSleepForSelectedDay(
        bedtimeHour: Int, bedtimeMinute: Int,
        wakeHour: Int, wakeMinute: Int,
        rating: Int
    ) {
        viewModelScope.launch {
            val epochDay = _selectedEpochDay.value
            val date = LocalDate.ofEpochDay(epochDay)
            val zone = ZoneOffset.systemDefault().rules.getOffset(java.time.Instant.now())
            val zoneOffsetSec = zone.totalSeconds

            val bedMs = java.time.LocalDateTime.of(date.minusDays(1), java.time.LocalTime.of(bedtimeHour, bedtimeMinute))
                .toInstant(zone).toEpochMilli()
            val wakeMs = java.time.LocalDateTime.of(date, java.time.LocalTime.of(wakeHour, wakeMinute))
                .toInstant(zone).toEpochMilli()
            val durationMs = (wakeMs - bedMs).coerceAtLeast(0L)

            sleepSessionDao.insertSession(
                SleepSessionEntity(
                    referenceDateEpochDay = epochDay,
                    sessionType = SessionType.NIGHT,
                    trackingMode = TrackingMode.MANUAL,
                    wakeMode = WakeMode.NATURAL_NO_ALARM,
                    startTimeMs = bedMs,
                    endTimeMs = wakeMs,
                    asleepTimeMs = bedMs,
                    wakeUpTimeMs = wakeMs,
                    zoneOffsetSeconds = zoneOffsetSec,
                    sleepDurationMs = durationMs,
                    sleepRating = rating,
                    isIncomplete = false
                )
            )
            sleepDebtRepository.recalculateAfterSessionChange(epochDay)
        }
    }

    init {
        selectDay(LocalDate.now().toEpochDay())
    }
}
