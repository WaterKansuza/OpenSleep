package org.opensleep.app.ui.history

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.opensleep.app.core.util.SleepReportExporter
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
import org.opensleep.app.data.repository.UserPreferencesRepository
import org.opensleep.app.domain.engine.SleepQualityEngine
import org.opensleep.app.domain.engine.SleepReportEngine
import org.opensleep.app.domain.engine.SleepWarningEngine
import org.opensleep.app.domain.model.SleepHealthReport
import org.opensleep.app.domain.model.SleepHealthWarning
import org.opensleep.app.domain.model.SleepQualityMetrics
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.random.Random

enum class ReportRangeOption {
    LAST_7_DAYS,
    LAST_30_DAYS,
    CUSTOM
}

data class HistoryUiState(
    val daysStrip: List<DaySleepStripItem> = emptyList(),
    val selectedEpochDay: Long = LocalDate.now().toEpochDay(),
    val selectedSession: SleepSessionEntity? = null,
    val selectedSessionIsDemo: Boolean = false,
    val selectedQualityMetrics: SleepQualityMetrics? = null,
    val selectedEpochs: List<SleepEpochEntity> = emptyList(),
    val selectedSoundEvents: List<SoundEventEntity> = emptyList(),
    val debtSnapshots14Days: List<DailySleepDebtSnapshotEntity> = emptyList(),
    val reportRange: ReportRangeOption = ReportRangeOption.LAST_7_DAYS,
    val customStartEpochDay: Long = LocalDate.now().minusDays(13).toEpochDay(),
    val customEndEpochDay: Long = LocalDate.now().toEpochDay(),
    val healthReport: SleepHealthReport? = null,
    val warnings: List<SleepHealthWarning> = emptyList(),
    val exportStatusMessage: String? = null
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val sleepSessionDao: SleepSessionDao,
    private val sleepDebtDao: SleepDebtDao,
    private val sleepDebtRepository: SleepDebtRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val qualityEngine: SleepQualityEngine,
    private val reportEngine: SleepReportEngine,
    private val warningEngine: SleepWarningEngine,
    private val reportExporter: SleepReportExporter
) : ViewModel() {

    private val todayEpochFlow = MutableStateFlow(LocalDate.now().toEpochDay())
    private val selectedDayFlow = MutableStateFlow(todayEpochFlow.value)
    private val reportRangeFlow = MutableStateFlow(ReportRangeOption.LAST_7_DAYS)
    private val customBoundsFlow = MutableStateFlow(
        (todayEpochFlow.value - 13) to todayEpochFlow.value
    )
    private val exportStatusFlow = MutableStateFlow<String?>(null)

    fun refreshTodayIfNeeded() {
        val nowEpoch = LocalDate.now().toEpochDay()
        if (todayEpochFlow.value != nowEpoch) {
            val wasFollowingToday = selectedDayFlow.value == todayEpochFlow.value
            todayEpochFlow.value = nowEpoch
            if (wasFollowingToday) {
                selectedDayFlow.value = nowEpoch
            }
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
            sleepSessionDao.observeSessionsInRange(todayEpoch - 89, todayEpoch),
            sleepDebtDao.observeProfile(),
            sleepDebtRepository.observeLast14DaysSnapshots(todayEpoch),
            userPreferencesRepository.preferencesFlow,
            selectedDayFlow,
            detailFlow,
            reportRangeFlow,
            customBoundsFlow,
            exportStatusFlow
        ) { array ->
            @Suppress("UNCHECKED_CAST")
            val sessions90 = array[0] as List<SleepSessionEntity>
            val profile = array[1] as org.opensleep.app.data.local.db.entity.UserSleepProfileEntity?
            @Suppress("UNCHECKED_CAST")
            val snapshots = array[2] as List<DailySleepDebtSnapshotEntity>
            val prefs = array[3] as org.opensleep.app.data.repository.UserPreferencesState
            val selDay = array[4] as Long
            @Suppress("UNCHECKED_CAST")
            val detail = array[5] as Triple<SleepSessionEntity?, List<SleepEpochEntity>, List<SoundEventEntity>>
            val rangeOption = array[6] as ReportRangeOption
            @Suppress("UNCHECKED_CAST")
            val customBounds = array[7] as Pair<Long, Long>
            val exportMsg = array[8] as String?

            val targetNeed = if (profile?.autoCalibrateEnabled == true) {
                profile.calibratedSleepNeedMinutes
            } else {
                profile?.targetSleepNeedMinutes ?: 480
            }

            val sessionsByDay = sessions90
                .filter { !it.isIncomplete && it.referenceDateEpochDay in (todayEpoch - 13)..todayEpoch }
                .groupBy { it.referenceDateEpochDay }

            val strip = ((todayEpoch - 13)..todayEpoch).map { day ->
                val daySessions = sessionsByDay[day].orEmpty()
                val totalMin = daySessions.sumOf { (it.sleepDurationMs / 60_000L).toInt() }
                val hasDemo = daySessions.any { it.id in prefs.demoSessionIds }
                DaySleepStripItem(
                    epochDay = day,
                    date = LocalDate.ofEpochDay(day),
                    totalSleepMinutes = totalMin,
                    targetNeedMinutes = targetNeed,
                    isDemoData = hasDemo
                )
            }

            val selectedSession = detail.first
            val selectedIsDemo = selectedSession?.id in prefs.demoSessionIds
            val qualityMetrics = selectedSession?.let {
                qualityEngine.computeMetrics(it, detail.second, isDemoData = selectedIsDemo)
            }

            val (reportStart, reportEnd, rangeLabel) = when (rangeOption) {
                ReportRangeOption.LAST_7_DAYS ->
                    Triple(todayEpoch - 6, todayEpoch, "7 ngày gần nhất")
                ReportRangeOption.LAST_30_DAYS ->
                    Triple(todayEpoch - 29, todayEpoch, "30 ngày gần nhất")
                ReportRangeOption.CUSTOM -> {
                    val s = minOf(customBounds.first, customBounds.second)
                    val e = maxOf(customBounds.first, customBounds.second)
                    Triple(s, e, "${LocalDate.ofEpochDay(s)} → ${LocalDate.ofEpochDay(e)}")
                }
            }

            val latestDebt = snapshots.lastOrNull()?.cumulativeDebtMinutes ?: 0
            val activeReport = reportEngine.buildReport(
                sessions = sessions90,
                demoSessionIds = prefs.demoSessionIds,
                startEpochDay = reportStart,
                endEpochDay = reportEnd,
                rangeLabel = rangeLabel,
                targetSleepMinutes = targetNeed,
                latestEstimatedDebtMinutes = latestDebt
            )

            val report7d = reportEngine.buildReport(
                sessions = sessions90,
                demoSessionIds = prefs.demoSessionIds,
                startEpochDay = todayEpoch - 6,
                endEpochDay = todayEpoch,
                rangeLabel = "7 ngày gần nhất",
                targetSleepMinutes = targetNeed,
                latestEstimatedDebtMinutes = latestDebt
            )

            val sessions7d = sessions90.filter {
                it.referenceDateEpochDay in (todayEpoch - 6)..todayEpoch
            }
            val moods7d = sleepSessionDao.getMoodsInRange(todayEpoch - 6, todayEpoch)
            val soundMap7d = sessions7d
                .filter { it.id !in prefs.demoSessionIds && !it.isIncomplete }
                .associate { it.id to sleepSessionDao.getSoundEventsForSession(it.id) }

            val warnings = warningEngine.evaluate(
                report7d = report7d,
                recentSessions7d = sessions7d,
                recentMoods7d = moods7d,
                soundEventsBySession = soundMap7d,
                demoSessionIds = prefs.demoSessionIds,
                targetSleepMinutes = targetNeed,
                dismissedEpochByType = prefs.dismissedWarningEpochs,
                todayEpochDay = todayEpoch
            )

            HistoryUiState(
                daysStrip = strip,
                selectedEpochDay = selDay,
                selectedSession = selectedSession,
                selectedSessionIsDemo = selectedIsDemo,
                selectedQualityMetrics = qualityMetrics,
                selectedEpochs = detail.second,
                selectedSoundEvents = detail.third,
                debtSnapshots14Days = snapshots,
                reportRange = rangeOption,
                customStartEpochDay = customBounds.first,
                customEndEpochDay = customBounds.second,
                healthReport = activeReport,
                warnings = warnings,
                exportStatusMessage = exportMsg
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun selectDay(epochDay: Long) {
        selectedDayFlow.value = epochDay
    }

    fun selectReportRange(option: ReportRangeOption) {
        reportRangeFlow.value = option
    }

    fun setCustomReportBounds(startEpochDay: Long, endEpochDay: Long) {
        customBoundsFlow.value = startEpochDay to endEpochDay
        reportRangeFlow.value = ReportRangeOption.CUSTOM
    }

    fun dismissWarning(warningTypeName: String) {
        viewModelScope.launch {
            userPreferencesRepository.dismissWarning(warningTypeName, todayEpochFlow.value)
        }
    }

    fun clearExportMessage() {
        exportStatusFlow.value = null
    }

    fun exportCurrentReportCsv(uri: Uri) {
        viewModelScope.launch {
            val report = uiState.value.healthReport ?: return@launch
            val prefs = userPreferencesRepository.getSnapshot()
            val realSessions = sleepSessionDao
                .getSessionsInRange(report.startEpochDay, report.endEpochDay)
                .filter { it.id !in prefs.demoSessionIds && !it.isIncomplete }
            val result = reportExporter.exportCsvToUri(uri, report, realSessions)
            exportStatusFlow.value = result.fold(
                onSuccess = { "Đã xuất CSV (${it} đêm dữ liệu thật)." },
                onFailure = { "Lỗi xuất CSV: ${it.message}" }
            )
        }
    }

    fun exportCurrentReportPdf(uri: Uri) {
        viewModelScope.launch {
            val report = uiState.value.healthReport ?: return@launch
            val prefs = userPreferencesRepository.getSnapshot()
            val realSessions = sleepSessionDao
                .getSessionsInRange(report.startEpochDay, report.endEpochDay)
                .filter { it.id !in prefs.demoSessionIds && !it.isIncomplete }
            val result = reportExporter.exportPdfToUri(uri, report, realSessions)
            exportStatusFlow.value = result.fold(
                onSuccess = { "Đã xuất báo cáo PDF cục bộ thành công." },
                onFailure = { "Lỗi xuất PDF: ${it.message}" }
            )
        }
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
                userPreferencesRepository.markSessionAsDemo(sessionId)
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
            } else {
                userPreferencesRepository.unmarkSessionDemo(sessionId)
            }
            sleepDebtRepository.recalculateAfterSessionChange(day)
        }
    }
}