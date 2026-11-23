package org.opensleep.app.domain.engine

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
class WakeTimePlannerEngine @Inject constructor() {

    companion object {
        private const val MINUTES_IN_DAY = 1440
    }

    fun computeRecommendation(
        prefs: UserPreferencesState,
        personalSleepNeedMinutes: Int,
        recentSessions: List<SleepSessionEntity>,
        estimatedDebtMinutes: Int,
        today: LocalDate = LocalDate.now()
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

        val bedtimeMin = if (prefs.customBedtimeEnabled) {
            prefs.customBedtimeHour.coerceIn(0, 23) * 60 + prefs.customBedtimeMinute.coerceIn(0, 59)
        } else {
            wrapMinutes(targetWakeMinOfDay - personalSleepNeedMinutes - estimatedLatency)
        }

        val plannedSleepMinutes = wrapMinutes(targetWakeMinOfDay - bedtimeMin)

        val debtSuggestion = if (estimatedDebtMinutes >= 150) {
            val h = estimatedDebtMinutes / 60
            val m = estimatedDebtMinutes % 60
            "Nợ ngủ ước tính ${h}h ${m}p · Có thể cân nhắc nghỉ sớm hơn ~20–30p (tùy chọn)."
        } else {
            null
        }

        return BedtimeRecommendation(
            recommendedBedtimeMinutesOfDay = bedtimeMin,
            recommendedBedtimeLabel = formatClock(bedtimeMin),
            targetWakeMinutesOfDay = targetWakeMinOfDay,
            targetWakeLabel = formatClock(targetWakeMinOfDay),
            plannedSleepMinutes = plannedSleepMinutes,
            isCustomBedtimeActive = prefs.customBedtimeEnabled,
            isWeekendScheduleApplied = useRestSchedule,
            personalSleepNeedMinutes = personalSleepNeedMinutes,
            estimatedLatencyMinutes = estimatedLatency,
            isLatencyEstimatedDefault = !hasEnoughLatency,
            debtRecoverySuggestion = debtSuggestion
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