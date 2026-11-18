package org.opensleep.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.opensleep.app.data.local.db.SessionType
import org.opensleep.app.data.local.db.dao.SleepDebtDao
import org.opensleep.app.data.local.db.dao.SleepSessionDao
import org.opensleep.app.data.local.db.entity.DailySleepDebtSnapshotEntity
import org.opensleep.app.data.local.db.entity.UserSleepProfileEntity
import org.opensleep.app.domain.engine.SleepDebtEngine
import org.opensleep.app.domain.model.SleepDebtAnalysis
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class SleepDebtRepository @Inject constructor(
    private val sleepSessionDao: SleepSessionDao,
    private val sleepDebtDao: SleepDebtDao,
    private val engine: SleepDebtEngine
) {

    fun observeProfile(): Flow<UserSleepProfileEntity?> = sleepDebtDao.observeProfile()

    fun observeLatestSnapshot(): Flow<DailySleepDebtSnapshotEntity?> = sleepDebtDao.observeLatestDebtSnapshot()

    fun observeLast14DaysSnapshots(todayEpochDay: Long = LocalDate.now().toEpochDay()): Flow<List<DailySleepDebtSnapshotEntity>> {
        return sleepDebtDao.observeSnapshotsInRange(todayEpochDay - 13, todayEpochDay)
    }

    fun observeCurrentAnalysis(todayEpochDay: Long = LocalDate.now().toEpochDay()): Flow<SleepDebtAnalysis> {
        val lookbackStart = todayEpochDay - (SleepDebtEngine.WINDOW_DAYS - 1)
        return combine(
            sleepDebtDao.observeProfile(),
            sleepSessionDao.observeSessionsInRange(lookbackStart, todayEpochDay)
        ) { profileOrNull, sessions ->
            val profile = profileOrNull ?: UserSleepProfileEntity()
            val grouped = sessions.groupBy { it.referenceDateEpochDay }
            val aggregates = grouped.mapValues { (day, list) ->
                engine.aggregateSessionsForDay(day, list)
            }
            engine.analyzeSleepDebtForDay(todayEpochDay, profile, aggregates)
        }
    }

    /**
     * Gọi mỗi khi hoàn thành 1 phiên ngủ hoặc user sửa/đánh giá giấc ngủ.
     * Cập nhật: (1) Giờ thức trung bình Process C, (2) Auto-calibrate, (3) Snapshot 14 ngày.
     */
    suspend fun recalculateAfterSessionChange(referenceEpochDay: Long) {
        val currentProfile = sleepDebtDao.getProfile() ?: UserSleepProfileEntity()
        val calibrationStart = referenceEpochDay - 21
        val sessions21Days = sleepSessionDao.getSessionsInRange(calibrationStart, referenceEpochDay)
        val moods21Days = sleepSessionDao.getMoodsInRange(calibrationStart, referenceEpochDay)

        // 1. Cập nhật giờ thức dậy trung bình từ các giấc ngủ NIGHT gần đây
        val nightSessions = sessions21Days.filter { it.sessionType == SessionType.NIGHT && !it.isIncomplete }
        val updatedWakeMin = if (nightSessions.isNotEmpty()) {
            val avgWake = nightSessions.takeLast(7).map { s ->
                val offset = ZoneOffset.ofTotalSeconds(s.zoneOffsetSeconds)
                val localWake = Instant.ofEpochMilli(s.wakeUpTimeMs).atOffset(offset).toLocalTime()
                localWake.hour * 60 + localWake.minute
            }.average().roundToInt()
            avgWake
        } else {
            currentProfile.typicalWakeMinutesOfDay
        }

        // 2. Tự hiệu chuẩn nhu cầu ngủ nếu được bật
        val updatedCalibratedNeed = if (currentProfile.autoCalibrateEnabled) {
            engine.autoCalibrateSleepNeed(
                currentNeedMinutes = currentProfile.calibratedSleepNeedMinutes,
                recentSessions = sessions21Days,
                recentMoods = moods21Days
            )
        } else {
            currentProfile.calibratedSleepNeedMinutes
        }

        val newProfile = currentProfile.copy(
            typicalWakeMinutesOfDay = updatedWakeMin,
            calibratedSleepNeedMinutes = updatedCalibratedNeed
        )
        sleepDebtDao.upsertProfile(newProfile)

        // 3. Tính và lưu lại Snapshot cho 14 ngày tính đến hôm nay
        val today = maxOf(LocalDate.now().toEpochDay(), referenceEpochDay)
        val fetchStart = today - 28
        val allSessions = sleepSessionDao.getSessionsInRange(fetchStart, today)
        val aggregates = allSessions.groupBy { it.referenceDateEpochDay }.mapValues { (day, list) ->
            engine.aggregateSessionsForDay(day, list)
        }

        val nowMs = System.currentTimeMillis()
        val snapshotsToSave = ((today - 13)..today).map { day ->
            val analysis = engine.analyzeSleepDebtForDay(day, newProfile, aggregates)
            DailySleepDebtSnapshotEntity(
                dateEpochDay = day,
                sleepNeedMinutes = analysis.activeSleepNeedMinutes,
                nightSleepMinutes = analysis.nightSleepMinutes,
                napSleepMinutes = analysis.napSleepMinutes,
                effectiveSleepMinutes = analysis.effectiveSleepMinutes,
                dailyDeltaMinutes = analysis.dailyDeltaMinutes,
                cumulativeDebtMinutes = analysis.cumulativeDebtMinutes,
                debtStatusLevel = analysis.statusLevel,
                updatedAtMs = nowMs
            )
        }
        sleepDebtDao.upsertSnapshots(snapshotsToSave)
    }
}
