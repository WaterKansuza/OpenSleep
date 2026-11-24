package org.opensleep.app.domain.engine

import org.opensleep.app.data.local.db.DebtStatusLevel
import org.opensleep.app.data.local.db.SessionType
import org.opensleep.app.data.local.db.TrackingMode
import org.opensleep.app.data.local.db.entity.SleepSessionEntity
import org.opensleep.app.data.repository.UserPreferencesState
import org.opensleep.app.domain.model.BedtimeRecommendation
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class WakeTimePlannerEngine @Inject constructor(
    private val recoveryPlanner: SleepDebtRecoveryPlanner
) {

    companion object {
        private const val MINUTES_IN_DAY = 1440
    }

    fun computeRecommendation(
        prefs: UserPreferencesState,
        personalSleepNeedMinutes: Int,
        recentSessions: List<SleepSessionEntity>,
        estimatedDebtMinutes: Int,
        today: LocalDate = LocalDate.now(),
        debtStatusLevel: DebtStatusLevel = DebtStatusLevel.OPTIMAL,
        typicalWakeMinutesOfDay: Int = -1
    ): BedtimeRecommendation {
        val tomorrow = today.plusDays(1)
        val dayBit = tomorrow.dayOfWeek.ordinal // MONDAY=0 .. SUNDAY=6
        val isRestDayTomorrow = (prefs.restDaysBitmask and (1 shl dayBit)) != 0
        val useRestSchedule = prefs.weekendScheduleEnabled && isRestDayTomorrow

        val targetHour = if (useRestSchedule) prefs.weekendWakeHour else prefs.weekdayWakeHour
        val targetMin = if (useRestSchedule) prefs.weekendWakeMinute else prefs.weekdayWakeMinute
        val targetWakeMinOfDay = targetHour.coerceIn(0, 23) * 60 + targetMin.coerceIn(0, 59)

        val latencySamples = recentSessions
            .asSequence()
            .filter {
                !it.isIncomplete &&
                    it.id !in prefs.demoSessionIds &&
                    it.sessionType == SessionType.NIGHT &&
                    it.trackingMode != TrackingMode.MANUAL &&
                    it.asleepTimeMs > it.startTimeMs
            }
            .map { ((it.asleepTimeMs - it.startTimeMs) / 60_000L).toInt() }
            .filter { it in 1..120 }
            .toList()
            .takeLast(14)

        val hasEnoughLatency = latencySamples.size >= 3
        val estimatedLatency = if (hasEnoughLatency) {
            linearMedian(latencySamples)
        } else {
            prefs.defaultLatencyFallbackMin.coerceIn(5, 45)
        }

        // Giờ lên giường cơ bản (chưa tính trả nợ)
        val baseBedtimeMin = if (prefs.customBedtimeEnabled) {
            prefs.customBedtimeHour.coerceIn(0, 23) * 60 + prefs.customBedtimeMinute.coerceIn(0, 59)
        } else {
            wrapMinutes(targetWakeMinOfDay - personalSleepNeedMinutes - estimatedLatency)
        }

        // Tính kế hoạch phục hồi nợ ngủ từ SleepDebtRecoveryPlanner
        val effectiveDebtLevel = if (debtStatusLevel == DebtStatusLevel.OPTIMAL && estimatedDebtMinutes >= 120) {
            when {
                estimatedDebtMinutes >= 480 -> DebtStatusLevel.CRITICAL
                estimatedDebtMinutes >= 300 -> DebtStatusLevel.HIGH
                else -> DebtStatusLevel.MILD
            }
        } else {
            debtStatusLevel
        }

        val effectiveTypicalWake = if (typicalWakeMinutesOfDay >= 0) typicalWakeMinutesOfDay
            else targetWakeMinOfDay
        val recoveryPlan = recoveryPlanner.computePlan(
            debtStatusLevel = effectiveDebtLevel,
            cumulativeDebtMinutes = estimatedDebtMinutes,
            typicalWakeMinutesOfDay = effectiveTypicalWake,
            isRestDay = isRestDayTomorrow
        )

        // Giờ lên giường sau khi áp dụng kế hoạch trả nợ
        // Chỉ điều chỉnh nếu người dùng chưa set custom bedtime
        val adjustedBedtimeMin = if (!prefs.customBedtimeEnabled) {
            wrapMinutes(baseBedtimeMin - recoveryPlan.extraBedtimeMinutes)
        } else {
            baseBedtimeMin
        }

        val plannedSleepMinutes = wrapMinutes(targetWakeMinOfDay - baseBedtimeMin)

        // Giữ chuỗi gợi ý tương thích ngược cho các màn hình cũ
        val debtSuggestion = recoveryPlan.summaryText.takeIf {
            recoveryPlan.recoveryStrategy != SleepDebtRecoveryPlanner.RecoveryStrategy.MAINTAIN
        }

        return BedtimeRecommendation(
            recommendedBedtimeMinutesOfDay = baseBedtimeMin,
            recommendedBedtimeLabel = formatClock(baseBedtimeMin),
            targetWakeMinutesOfDay = targetWakeMinOfDay,
            targetWakeLabel = formatClock(targetWakeMinOfDay),
            plannedSleepMinutes = plannedSleepMinutes,
            isCustomBedtimeActive = prefs.customBedtimeEnabled,
            isWeekendScheduleApplied = useRestSchedule,
            personalSleepNeedMinutes = personalSleepNeedMinutes,
            estimatedLatencyMinutes = estimatedLatency,
            isLatencyEstimatedDefault = !hasEnoughLatency,
            debtRecoverySuggestion = debtSuggestion,
            recoveryPlan = recoveryPlan,
            adjustedBedtimeMinutesOfDay = adjustedBedtimeMin
        )
    }

    fun formatClock(minutesOfDay: Int): String {
        val m = wrapMinutes(minutesOfDay)
        return String.format("%02d:%02d", m / 60, m % 60)
    }

    private fun wrapMinutes(min: Int): Int =
        ((min % MINUTES_IN_DAY) + MINUTES_IN_DAY) % MINUTES_IN_DAY

    private fun linearMedian(values: List<Int>): Int {
        if (values.isEmpty()) return 0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            ((sorted[mid - 1] + sorted[mid]) / 2.0).roundToInt()
        }
    }
}
