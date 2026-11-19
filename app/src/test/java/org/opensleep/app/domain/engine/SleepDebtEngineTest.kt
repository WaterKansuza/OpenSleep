package org.opensleep.app.domain.engine

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.opensleep.app.data.local.db.DebtStatusLevel
import org.opensleep.app.data.local.db.SessionType
import org.opensleep.app.data.local.db.TrackingMode
import org.opensleep.app.data.local.db.entity.SleepSessionEntity
import org.opensleep.app.data.local.db.entity.UserSleepProfileEntity
import org.opensleep.app.domain.model.DailySleepAggregate

class SleepDebtEngineTest {

    private lateinit var engine: SleepDebtEngine
    private val baseProfile = UserSleepProfileEntity(
        targetSleepNeedMinutes = 480,      // 8.0h
        calibratedSleepNeedMinutes = 480,
        autoCalibrateEnabled = false,
        typicalWakeMinutesOfDay = 420,     // 07:00
        decayLambda = 0.085f
    )

    @Before
    fun setup() {
        engine = SleepDebtEngine()
    }

    @Test
    fun `new user sleeping 6h on day 1 accumulates exactly 2h debt without penalty for prior empty days`() {
        val today = 20000L
        val aggregates = mapOf(
            today to DailySleepAggregate(today, hasData = true, nightSleepMinutes = 360, napSleepMinutes = 0, weightedQualityFactor = 1.0f)
        )

        val result = engine.analyzeSleepDebtForDay(today, baseProfile, aggregates)

        assertEquals(120, result.cumulativeDebtMinutes)
        assertEquals(120, result.dailyDeltaMinutes)
        assertEquals(DebtStatusLevel.MILD, result.statusLevel)
    }

    @Test
    fun `chronic 14-day sleep restriction of 6h per night reaches CRITICAL debt`() {
        val today = 20000L
        val aggregates = (0 until 14).associate { k ->
            val day = today - k
            day to DailySleepAggregate(day, hasData = true, nightSleepMinutes = 360, napSleepMinutes = 0, weightedQualityFactor = 1.0f)
        }

        val result = engine.analyzeSleepDebtForDay(today, baseProfile, aggregates)

        // Tổng tích lũy suy giảm hàm mũ 14 ngày * 120p > 900p
        assertTrue("Expected > 800 min debt, got ${result.cumulativeDebtMinutes}", result.cumulativeDebtMinutes > 800)
        assertEquals(DebtStatusLevel.CRITICAL, result.statusLevel)
        assertEquals(25, result.recommendedNapMinutes)
    }

    @Test
    fun `recovery asymmetry prevents single 12h binge sleep from erasing heavy chronic debt`() {
        val today = 20000L
        val aggregates = mutableMapOf<Long, DailySleepAggregate>()
        // 5 đêm trước đó mỗi đêm chỉ ngủ 5h (nợ 3h/đêm)
        for (k in 1..5) {
            aggregates[today - k] = DailySleepAggregate(today - k, true, 300, 0, 1.0f)
        }
        // Hôm nay ngủ bù 12h (720 phút)
        aggregates[today] = DailySleepAggregate(today, true, 720, 0, 1.0f)

        val result = engine.analyzeSleepDebtForDay(today, baseProfile, aggregates)

        // Dù ngủ dư 4h, tối đa chỉ trả được 90 phút nên tổng nợ vẫn cao (> 500 phút)
        assertTrue("Debt should still remain substantial: ${result.cumulativeDebtMinutes}", result.cumulativeDebtMinutes > 500)
        assertEquals(-90, result.dailyDeltaMinutes)
    }

    @Test
    fun `power nap of 20 mins adds 17 mins credit while micro nap under 15 mins is ignored`() {
        val day = 20000L
        val night = mockSession(day, SessionType.NIGHT, sleepMins = 420)
        val validNap = mockSession(day, SessionType.NAP, sleepMins = 20)
        val shortNap = mockSession(day, SessionType.NAP, sleepMins = 10)

        val aggValid = engine.aggregateSessionsForDay(day, listOf(night, validNap))
        val aggIgnored = engine.aggregateSessionsForDay(day, listOf(night, shortNap))

        assertEquals(420 + 17, engine.calculateEffectiveSleepMinutes(aggValid))
        assertEquals(420, engine.calculateEffectiveSleepMinutes(aggIgnored))
    }

    @Test
    fun `high sleep fragmentation reduces quality factor and effective sleep`() {
        val day = 20000L
        val fragmented = mockSession(day, SessionType.NIGHT, sleepMins = 480, microAwakenings = 24)
        val q = engine.computeSessionQualityFactor(fragmented)

        assertTrue("Quality factor should drop below 0.90, was $q", q < 0.90f)
    }

    @Test
    fun `smart alarm shrinks wake window when sleep debt is HIGH or CRITICAL`() {
        assertEquals(30, engine.computeDebtAwareSmartWindow(30, true, cumulativeDebtMinutes = 60))
        assertEquals(20, engine.computeDebtAwareSmartWindow(30, true, cumulativeDebtMinutes = 180))
        assertEquals(10, engine.computeDebtAwareSmartWindow(30, true, cumulativeDebtMinutes = 350))
        assertEquals(0, engine.computeDebtAwareSmartWindow(30, true, cumulativeDebtMinutes = 520))
    }

    @Test
    fun `auto calibration adjusts sleep need gradually toward observed high-quality nights`() {
        val sessions = (1..6).map { i ->
            mockSession(20000L - i, SessionType.NIGHT, sleepMins = 540, rating = 5) // Thực tế cần 9h
        }
        val newNeed = engine.autoCalibrateSleepNeed(currentNeedMinutes = 480, recentSessions = sessions, recentMoods = emptyList())

        // Chênh 60p * 0.25 = +15p (nằm trong giới hạn bước nhảy ±15p)
        assertEquals(495, newNeed)
    }

    private fun mockSession(
        epochDay: Long,
        type: SessionType,
        sleepMins: Int,
        microAwakenings: Int = 0,
        rating: Int? = null
    ) = SleepSessionEntity(
        referenceDateEpochDay = epochDay,
        sessionType = type,
        trackingMode = TrackingMode.PASSIVE,
        startTimeMs = 0L,
        endTimeMs = sleepMins * 60_000L,
        asleepTimeMs = 0L,
        wakeUpTimeMs = sleepMins * 60_000L,
        zoneOffsetSeconds = 0,
        sleepDurationMs = sleepMins * 60_000L,
        microAwakeningsCount = microAwakenings,
        sleepRating = rating
    )
}
