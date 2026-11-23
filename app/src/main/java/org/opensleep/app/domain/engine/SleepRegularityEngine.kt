package org.opensleep.app.domain.engine

import org.opensleep.app.data.local.db.SessionType
import org.opensleep.app.data.local.db.entity.SleepSessionEntity
import org.opensleep.app.domain.model.RegularityNightPoint
import org.opensleep.app.domain.model.SleepRegularitySummary
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Singleton
class SleepRegularityEngine @Inject constructor() {

    companion object {
        private const val MINUTES_IN_DAY = 1440
    }

    fun analyze(
        sessions: List<SleepSessionEntity>,
        demoSessionIds: Set<Long>,
        startEpochDay: Long,
        endEpochDay: Long
    ): SleepRegularitySummary {
        val totalDays = (endEpochDay - startEpochDay + 1).toInt().coerceAtLeast(1)
        val requiredNights = if (totalDays <= 7) 5 else min(10, ceil(totalDays * 0.6).toInt())

        val validMainByDay = sessions
            .asSequence()
            .filter {
                !it.isIncomplete &&
                    it.id !in demoSessionIds &&
                    it.sessionType == SessionType.NIGHT &&
                    it.sleepDurationMs > 0L &&
                    it.referenceDateEpochDay in startEpochDay..endEpochDay
            }
            .groupBy { it.referenceDateEpochDay }
            .mapValues { (_, list) -> list.maxByOrNull { it.sleepDurationMs }!! }

        val dateFmt = DateTimeFormatter.ofPattern("dd/MM")
        val points = validMainByDay.keys.sorted().map { day ->
            val s = validMainByDay[day]!!
            val offset = ZoneOffset.ofTotalSeconds(s.zoneOffsetSeconds)
            val bedLocal = Instant.ofEpochMilli(s.asleepTimeMs).atOffset(offset).toLocalTime()
            val wakeLocal = Instant.ofEpochMilli(s.wakeUpTimeMs).atOffset(offset).toLocalTime()
            val bedMin = bedLocal.hour * 60 + bedLocal.minute
            val wakeMin = wakeLocal.hour * 60 + wakeLocal.minute
            val date = LocalDate.ofEpochDay(day)
            val isWeekend =
                date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
            RegularityNightPoint(
                epochDay = day,
                dayLabel = date.format(dateFmt),
                bedtimeMinutes = bedMin,
                wakeMinutes = wakeMin,
                durationMinutes = (s.sleepDurationMs / 60_000L).toInt(),
                isWeekend = isWeekend
            )
        }

        val coverage = ((points.size.toFloat() / totalDays.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
        if (points.isEmpty()) {
            return SleepRegularitySummary(
                windowDays = totalDays,
                validNightsCount = 0,
                requiredNights = requiredNights,
                hasSufficientData = false,
                coveragePct = 0,
                typicalBedtimeLabel = "--:--",
                typicalWakeLabel = "--:--",
                sleepMidpointLabel = "--:--",
                bedtimeVariabilityMin = 0,
                wakeVariabilityMin = 0,
                durationVariabilityMin = 0,
                weekendShiftMin = null,
                nightlyPoints = emptyList()
            )
        }

        val (bedMed, bedMad) = circularMedianAndMad(points.map { it.bedtimeMinutes })
        val (wakeMed, wakeMad) = circularMedianAndMad(points.map { it.wakeMinutes })

        val midpoints = points.map { p ->
            val dur = wrapMinutes(p.wakeMinutes - p.bedtimeMinutes)
            wrapMinutes(p.bedtimeMinutes + dur / 2)
        }
        val (midMed, _) = circularMedianAndMad(midpoints)

        val durations = points.map { it.durationMinutes }
        val durMed = linearMedian(durations)
        val durMad = linearMedian(durations.map { abs(it - durMed) })

        val weekdayMids = points.filter { !it.isWeekend }.map { p ->
            val dur = wrapMinutes(p.wakeMinutes - p.bedtimeMinutes)
            wrapMinutes(p.bedtimeMinutes + dur / 2)
        }
        val weekendMids = points.filter { it.isWeekend }.map { p ->
            val dur = wrapMinutes(p.wakeMinutes - p.bedtimeMinutes)
            wrapMinutes(p.bedtimeMinutes + dur / 2)
        }
        val weekendShift = if (weekdayMids.size >= 2 && weekendMids.isNotEmpty()) {
            abs(circularDiffMinutes(circularMeanMinutes(weekendMids), circularMeanMinutes(weekdayMids)))
        } else {
            null
        }

        return SleepRegularitySummary(
            windowDays = totalDays,
            validNightsCount = points.size,
            requiredNights = requiredNights,
            hasSufficientData = points.size >= requiredNights,
            coveragePct = coverage,
            typicalBedtimeLabel = formatClock(bedMed),
            typicalWakeLabel = formatClock(wakeMed),
            sleepMidpointLabel = formatClock(midMed),
            bedtimeVariabilityMin = bedMad,
            wakeVariabilityMin = wakeMad,
            durationVariabilityMin = durMad,
            weekendShiftMin = weekendShift,
            nightlyPoints = points
        )
    }

    fun circularMeanMinutes(minutesList: List<Int>): Int {
        if (minutesList.isEmpty()) return 0
        var sinSum = 0.0
        var cosSum = 0.0
        for (m in minutesList) {
            val rad = (m.toDouble() / MINUTES_IN_DAY) * 2.0 * PI
            sinSum += sin(rad)
            cosSum += cos(rad)
        }
        var angle = atan2(sinSum / minutesList.size, cosSum / minutesList.size)
        if (angle < 0.0) angle += 2.0 * PI
        return wrapMinutes(((angle / (2.0 * PI)) * MINUTES_IN_DAY).roundToInt())
    }

    fun circularDiffMinutes(a: Int, b: Int): Int {
        var diff = a - b
        while (diff > 720) diff -= MINUTES_IN_DAY
        while (diff < -720) diff += MINUTES_IN_DAY
        return diff
    }

    fun circularMedianAndMad(minutesList: List<Int>): Pair<Int, Int> {
        if (minutesList.isEmpty()) return 0 to 0
        val anchor = circularMeanMinutes(minutesList)
        val offsets = minutesList.map { circularDiffMinutes(it, anchor) }.sorted()
        val medOffset = linearMedian(offsets)
        val median = wrapMinutes(anchor + medOffset)
        val absDevs = minutesList.map { abs(circularDiffMinutes(it, median)) }.sorted()
        val mad = linearMedian(absDevs)
        return median to mad
    }

    private fun linearMedian(sortedOrRaw: List<Int>): Int {
        if (sortedOrRaw.isEmpty()) return 0
        val sorted = sortedOrRaw.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            ((sorted[mid - 1] + sorted[mid]) / 2.0).roundToInt()
        }
    }

    private fun wrapMinutes(min: Int): Int =
        ((min % MINUTES_IN_DAY) + MINUTES_IN_DAY) % MINUTES_IN_DAY

    private fun formatClock(min: Int): String {
        val m = wrapMinutes(min)
        return String.format("%02d:%02d", m / 60, m % 60)
    }
}