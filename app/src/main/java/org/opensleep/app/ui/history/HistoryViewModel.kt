package org.opensleep.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.opensleep.app.data.local.db.SessionType
import org.opensleep.app.data.local.db.SleepStage
import org.opensleep.app.data.local.db.TrackingMode
import org.opensleep.app.data.local.db.WakeMode
import org.opensleep.app.data.local.db.dao.SleepDebtDao
import org.opensleep.app.data.local.db.dao.SleepSessionDao
import org.opensleep.app.data.local.db.entity.DailySleepDebtSnapshotEntity
import org.opensleep.app.data.local.db.entity.SleepEpochEntity
import org.opensleep.app.data.local.db.entity.SleepSessionEntity
import org.opensleep.app.data.local.db.entity.SoundEventEntity
import org.opensleep.app.data.repository.SleepDebtRepository
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.random.Random

data class HistoryUiState(
    val daysStrip: List<DaySleepStripItem> = emptyList(),
    val selectedEpochDay: Long = LocalDate.now().toEpochDay(),
    val selectedSession: SleepSessionEntity? = null,
    val selectedEpochs: List<SleepEpochEntity> = emptyList(),
    val selectedSoundEvents: List<SoundEventEntity> = emptyList(),
    val debtSnapshots14Days: List<DailySleepDebtSnapshotEntity> = emptyList()
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val sleepSessionDao: SleepSessionDao,
    private val sleepDebtDao: SleepDebtDao,
    private val sleepDebtRepository: SleepDebtRepository
) : ViewModel() {

    private val todayEpochFlow = MutableStateFlow(LocalDate.now().toEpochDay())
    private val selectedDayFlow = MutableStateFlow(todayEpochFlow.value)

    fun refreshTodayIfNeeded() {
        val nowEpoch = LocalDate.now().toEpochDay()
        if (todayEpochFlow.value != nowEpoch) {
            todayEpochFlow.value = nowEpoch
            selectedDayFlow.value = nowEpoch
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val detailFlow = selectedDayFlow.flatMapLatest { day ->
        sleepSessionDao.observeSessionsInRange(day, day).map { sessions ->
            val mainSession = sessions.firstOrNull { it.sessionType == SessionType.NIGHT && !it.isIncomplete }
                ?: sessions.firstOrNull { !it.isIncomplete }
            if (mainSession == null) {
                Triple(null, emptyList<SleepEpochEntity>(), emptyList<SoundEventEntity>())
            } else {
                val epochs = sleepSessionDao.getEpochsForSession(mainSession.id)
                val sounds = sleepSessionDao.getSoundEventsForSession(mainSession.id)
                Triple(mainSession, epochs, sounds)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HistoryUiState> = todayEpochFlow.flatMapLatest { todayEpoch ->
        combine(
            sleepSessionDao.observeSessionsInRange(todayEpoch - 13, todayEpoch),
            sleepDebtDao.observeProfile(),
            sleepDebtRepository.observeLast14DaysSnapshots(todayEpoch),
            selectedDayFlow,
            detailFlow
        ) { sessions14, profile, snapshots, selDay, detail ->
            val targetNeed = if (profile?.autoCalibrateEnabled == true) {
                profile.calibratedSleepNeedMinutes
            } else {
                profile?.targetSleepNeedMinutes ?: 480
            }
            val sessionsByDay = sessions14.filter { !it.isIncomplete }.groupBy { it.referenceDateEpochDay }

            val strip = ((todayEpoch - 13)..todayEpoch).map { day ->
                val totalMin = sessionsByDay[day]?.sumOf { (it.sleepDurationMs / 60_000L).toInt() } ?: 0
                DaySleepStripItem(
                    epochDay = day,
                    date = LocalDate.ofEpochDay(day),
                    totalSleepMinutes = totalMin,
                    targetNeedMinutes = targetNeed
                )
            }

            HistoryUiState(
                daysStrip = strip,
                selectedEpochDay = selDay,
                selectedSession = detail.first,
                selectedEpochs = detail.second,
                selectedSoundEvents = detail.third,
                debtSnapshots14Days = snapshots
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun selectDay(epochDay: Long) {
        selectedDayFlow.value = epochDay
    }

    fun logManualSleepForSelectedDay(
        sleepMinutes: Int,
        wakeMode: WakeMode,
        generateDemoStages: Boolean = false
    ) {
        viewModelScope.launch {
            val day = selectedDayFlow.value
            val zone = ZoneId.systemDefault()
            val wakeInstant = LocalDate.ofEpochDay(day).atTime(7, 0).atZone(zone).toInstant()
            val wakeMs = wakeInstant.toEpochMilli()
            val sleepMs = sleepMinutes * 60_000L
            val startMs = wakeMs - sleepMs
            val offsetSec = zone.rules.getOffset(wakeInstant).totalSeconds

            val awakeMs = if (generateDemoStages) (sleepMs * 0.04).toLong() else 0L
            val remMs = if (generateDemoStages) (sleepMs * 0.22).toLong() else 0L
            val deepMs = if (generateDemoStages) (sleepMs * 0.22).toLong() else 0L
            val coreMs = sleepMs - remMs - deepMs

            val sessionId = sleepSessionDao.insertSession(
                SleepSessionEntity(
                    referenceDateEpochDay = day,
                    sessionType = SessionType.NIGHT,
                    trackingMode = TrackingMode.MANUAL,
                    wakeMode = wakeMode,
                    startTimeMs = startMs,
                    endTimeMs = wakeMs,
                    asleepTimeMs = startMs,
                    wakeUpTimeMs = wakeMs,
                    zoneOffsetSeconds = offsetSec,
                    awakeDurationMs = awakeMs,
                    sleepDurationMs = sleepMs,
                    lightSleepMs = coreMs,
                    deepSleepMs = deepMs,
                    remSleepMs = remMs,
                    microAwakeningsCount = if (generateDemoStages) 2 else 0,
                    sleepRating = 4,
                    isIncomplete = false
                )
            )

            if (generateDemoStages) {
                val totalEpochs = (sleepMinutes * 2).coerceAtLeast(10)
                val epochs = (0 until totalEpochs).map { idx ->
                    val cyclePos = (idx % 180) / 180f
                    val stage = when {
                        idx < 4 -> SleepStage.AWAKE
                        cyclePos in 0.15f..0.40f -> SleepStage.DEEP
                        cyclePos in 0.72f..0.92f -> SleepStage.REM
                        cyclePos > 0.96f && Random.nextFloat() < 0.35f -> SleepStage.AWAKE
                        else -> SleepStage.LIGHT
                    }
                    SleepEpochEntity(
                        sessionId = sessionId,
                        timestampMs = startMs + idx * 30_000L,
                        stage = stage,
                        motionIntensity = if (stage == SleepStage.AWAKE) 0.4f else 0.02f,
                        breathingRateBpm = if (stage == SleepStage.DEEP) 12.2f else 15.0f
                    )
                }
                sleepSessionDao.insertEpochs(epochs)
            }
            sleepDebtRepository.recalculateAfterSessionChange(day)
        }
    }
}