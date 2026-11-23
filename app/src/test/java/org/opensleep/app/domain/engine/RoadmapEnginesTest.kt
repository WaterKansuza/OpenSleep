package org.opensleep.app.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensleep.app.data.local.db.SessionType
import org.opensleep.app.data.local.db.TrackingMode
import org.opensleep.app.data.local.db.WakeMode
import org.opensleep.app.data.local.db.entity.SleepSessionEntity
import org.opensleep.app.data.repository.UserPreferencesState
import java.time.LocalDate
import java.time.ZoneOffset

class RoadmapEnginesTest {

    @Test
    fun wakeTimePlannerWrapsCleanlyPastMidnight() {
        val engine = WakeTimePlannerEngine()
        val prefs = UserPreferencesState(
            weekdayWakeHour = 6,
            weekdayWakeMinute = 30,
            weekendScheduleEnabled = false,
            defaultLatencyFallbackMin = 15
        )
        // 06:30 - 8h00 (480p) - 15p = 22:15 (1335)
        val rec = engine.computeRecommendation(
            prefs = prefs,
            personalSleepNeedMinutes = 480,
            recentSessions = emptyList(),
            estimatedDebtMinutes = 420, // nợ cao nhưng KHÔNG được dịch giờ dậy
            today = LocalDate.of(2026, 11, 18) // Thứ 4 -> mai Thứ 5
        )
        assertEquals("06:30", rec.targetWakeLabel)
        assertEquals("22:15", rec.recommendedBedtimeLabel)
        assertFalse(rec.isCustomBedtimeActive)
        assertEquals(495, rec.plannedSleepMinutes)
        assertTrue(rec.debtRecoverySuggestion != null)
    }

    @Test
    fun customOneMinuteBedtimeAndFlexibleRestDayWorkAcrossMidnight() {
        val engine = WakeTimePlannerEngine()
        // Thứ 5 2026-11-19 -> ngày mai là Thứ 6 (bit 4)
        val prefs = UserPreferencesState(
            weekdayWakeHour = 6,
            weekdayWakeMinute = 0,
            weekendScheduleEnabled = true,
            restDaysBitmask = (1 shl 4), // Thứ 6 là ngày nghỉ
            weekendWakeHour = 8,
            weekendWakeMinute = 13,
            customBedtimeEnabled = true,
            customBedtimeHour = 23,
            customBedtimeMinute = 47
        )
        val rec = engine.computeRecommendation(
            prefs = prefs,
            personalSleepNeedMinutes = 480,
            recentSessions = emptyList(),
            estimatedDebtMinutes = 0,
            today = LocalDate.of(2026, 11, 19)
        )
        assertTrue(rec.isWeekendScheduleApplied)
        assertTrue(rec.isCustomBedtimeActive)
        assertEquals("23:47", rec.recommendedBedtimeLabel)
        assertEquals("08:13", rec.targetWakeLabel)
        assertEquals(8 * 60 + 26, rec.plannedSleepMinutes)
    }

    @Test
    fun regularityHandlesMidnightCrossing2350And0010() {
        val regEngine = SleepRegularityEngine()
        val times = listOf(23 * 60 + 50, 0 * 60 + 10, 23 * 60 + 55, 0 * 60 + 5, 23 * 60 + 58)
        val (med, mad) = regEngine.circularMedianAndMad(times)
        // Trung vị phải rơi gần 00:00 (0 hoặc 1438) và MAD <= 10 phút, KHÔNG phải 12:00!
        val diffFromMidnight = kotlin.math.abs(regEngine.circularDiffMinutes(med, 0))
        assertTrue("Median must be near midnight, was $med", diffFromMidnight <= 10)
        assertTrue("MAD must be small across midnight, was $mad", mad <= 10)
    }

    @Test
    fun manualEntryKeepsSolNullAndDemoSessionsAreExcluded() {
        val qEngine = SleepQualityEngine()
        val rEngine = SleepReportEngine(qEngine, SleepRegularityEngine())
        val baseDay = 20000L

        val manualSession = SleepSessionEntity(
            id = 10L,
            referenceDateEpochDay = baseDay,
            sessionType = SessionType.NIGHT,
            trackingMode = TrackingMode.MANUAL,
            wakeMode = WakeMode.NATURAL_NO_ALARM,
            startTimeMs = 1_000_000L,
            endTimeMs = 1_000_000L + 450 * 60_000L,
            asleepTimeMs = 1_000_000L,
            wakeUpTimeMs = 1_000_000L + 450 * 60_000L,
            zoneOffsetSeconds = ZoneOffset.UTC.totalSeconds,
            sleepDurationMs = 450 * 60_000L
        )
        val metrics = qEngine.computeMetrics(manualSession, emptyList(), isDemoData = false)
        assertNull(metrics.solMinutes)
        assertNull(metrics.sleepEfficiencyPct)

        val report = rEngine.buildReport(
            sessions = listOf(manualSession),
            demoSessionIds = setOf(10L),
            startEpochDay = baseDay - 6,
            endEpochDay = baseDay,
            rangeLabel = "7d",
            targetSleepMinutes = 480,
            latestEstimatedDebtMinutes = 0
        )
        assertEquals(0, report.validNightsCount)
        assertEquals(1, report.excludedDemoCount)
        assertFalse(report.regularity.hasSufficientData)
    }
}