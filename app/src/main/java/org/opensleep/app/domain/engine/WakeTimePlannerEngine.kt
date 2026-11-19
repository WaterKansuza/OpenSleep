package org.opensleep.app.domain.engine

import org.opensleep.app.data.local.db.SessionType
import org.opensleep.app.data.local.db.TrackingMode
import org.opensleep.app.data.local.db.entity.SleepSessionEntity
import org.opensleep.app.data.repository.UserPreferencesState
import org.opensleep.app.domain.model.BedtimeRecommendation
import java.time.DayOfWeek
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
        val isWeekendTomorrow =
            tomorrow.dayOfWeek == DayOfWeek.SATURDAY || tomorrow.dayOfWeek == DayOfWeek.SUNDAY
        val useWeekend = prefs.weekendScheduleEnabled && isWeekendTomorrow

        val targetHour = if (useWeekend) prefs.weekendWakeHour else prefs.weekdayWakeHour
        val targetMin = if (useWeekend) prefs.weekendWakeMinute else prefs.weekdayWakeMinute
        val targetWakeMinOfDay = targetHour * 60 + targetMin

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

        val rawBedtime = targetWakeMinOfDay - personalSleepNeedMinutes - estimatedLatency
        val recommendedBedtimeMin = wrapMinutes(rawBedtime)

        val debtSuggestion = if (estimatedDebtMinutes >= 150) {
            val h = estimatedDebtMinutes / 60
            val m = estimatedDebtMinutes % 60
            "Nợ ngủ ước tính ${h}h ${m}p · Có thể cân nhắc nghỉ sớm hơn ~20–30p (tùy chọn)."
        } else {
            null
        }

        return BedtimeRecommendation(
            recommendedBedtimeMinutesOfDay = recommendedBedtimeMin,
            recommendedBedtimeLabel = formatClock(recommendedBedtimeMin),
            targetWakeMinutesOfDay = targetWakeMinOfDay,
            targetWakeLabel = formatClock(targetWakeMinOfDay),
            isWeekendScheduleApplied = useWeekend,
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