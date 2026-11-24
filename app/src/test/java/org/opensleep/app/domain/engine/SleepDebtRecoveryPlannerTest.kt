package org.opensleep.app.domain.engine

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.opensleep.app.data.local.db.DebtStatusLevel

class SleepDebtRecoveryPlannerTest {

    private lateinit var planner: SleepDebtRecoveryPlanner

    // Giờ thức dậy điển hình: 7:00 (420 phút)
    private val typicalWakeMin = 7 * 60

    @Before
    fun setUp() {
        planner = SleepDebtRecoveryPlanner()
    }

    // ─── OPTIMAL ───────────────────────────────────────────────────────────────

    @Test
    fun `OPTIMAL debt returns MAINTAIN strategy with zero extra bedtime`() {
        val plan = planner.computePlan(
            debtStatusLevel = DebtStatusLevel.OPTIMAL,
            cumulativeDebtMinutes = 60,
            typicalWakeMinutesOfDay = typicalWakeMin,
            isRestDay = false
        )

        assertEquals(SleepDebtRecoveryPlanner.RecoveryStrategy.MAINTAIN, plan.recoveryStrategy)
        assertEquals(0, plan.extraBedtimeMinutes)
        assertEquals(0, plan.napRecommendationMin)
        assertEquals(0, plan.weekendSleepCapMin)
    }

    @Test
    fun `OPTIMAL debt on rest day still returns MAINTAIN with zero weekend cap`() {
        val plan = planner.computePlan(
            debtStatusLevel = DebtStatusLevel.OPTIMAL,
            cumulativeDebtMinutes = 50,
            typicalWakeMinutesOfDay = typicalWakeMin,
            isRestDay = true
        )
        assertEquals(SleepDebtRecoveryPlanner.RecoveryStrategy.MAINTAIN, plan.recoveryStrategy)
        assertEquals(0, plan.weekendSleepCapMin)
    }

    // ─── MILD ──────────────────────────────────────────────────────────────────

    @Test
    fun `MILD debt returns MICRO_REPAY with 15 extra bedtime minutes`() {
        val plan = planner.computePlan(
            debtStatusLevel = DebtStatusLevel.MILD,
            cumulativeDebtMinutes = 180,
            typicalWakeMinutesOfDay = typicalWakeMin,
            isRestDay = false
        )

        assertEquals(SleepDebtRecoveryPlanner.RecoveryStrategy.MICRO_REPAY, plan.recoveryStrategy)
        assertEquals(15, plan.extraBedtimeMinutes)
        assertEquals(20, plan.napRecommendationMin)
        assertEquals(0, plan.weekendSleepCapMin)
    }

    @Test
    fun `MILD debt on rest day includes 60min weekend cap`() {
        val plan = planner.computePlan(
            debtStatusLevel = DebtStatusLevel.MILD,
            cumulativeDebtMinutes = 180,
            typicalWakeMinutesOfDay = typicalWakeMin,
            isRestDay = true
        )
        assertEquals(60, plan.weekendSleepCapMin)
    }

    @Test
    fun `MILD debt below 150min does not add Power Nap to summary`() {
        val plan = planner.computePlan(
            debtStatusLevel = DebtStatusLevel.MILD,
            cumulativeDebtMinutes = 130,
            typicalWakeMinutesOfDay = typicalWakeMin,
            isRestDay = false
        )
        assertFalse("Summary should not mention Power Nap for low MILD debt",
            plan.summaryText.contains("Power Nap"))
    }

    @Test
    fun `MILD debt above 150min adds Power Nap to summary`() {
        val plan = planner.computePlan(
            debtStatusLevel = DebtStatusLevel.MILD,
            cumulativeDebtMinutes = 200,
            typicalWakeMinutesOfDay = typicalWakeMin,
            isRestDay = false
        )
        assertTrue("Summary should mention Power Nap for high MILD debt",
            plan.summaryText.contains("Power Nap"))
    }

    // ─── HIGH ──────────────────────────────────────────────────────────────────

    @Test
    fun `HIGH debt returns ACTIVE_REPAY with up to 25 extra bedtime minutes`() {
        val plan = planner.computePlan(
            debtStatusLevel = DebtStatusLevel.HIGH,
            cumulativeDebtMinutes = 400,
            typicalWakeMinutesOfDay = typicalWakeMin,
            isRestDay = false
        )

        assertEquals(SleepDebtRecoveryPlanner.RecoveryStrategy.ACTIVE_REPAY, plan.recoveryStrategy)
        assertTrue("extraBedtimeMinutes should be <= 25", plan.extraBedtimeMinutes <= 25)
        assertTrue("extraBedtimeMinutes should be > 0", plan.extraBedtimeMinutes > 0)
        assertEquals(25, plan.napRecommendationMin)
    }

    @Test
    fun `HIGH debt extra bedtime capped at 25min`() {
        // Nợ rất cao: 500 phút → 500/20 = 25
        val plan = planner.computePlan(
            debtStatusLevel = DebtStatusLevel.HIGH,
            cumulativeDebtMinutes = 500,
            typicalWakeMinutesOfDay = typicalWakeMin,
            isRestDay = false
        )
        assertEquals(25, plan.extraBedtimeMinutes)
    }

    @Test
    fun `HIGH debt on rest day includes 90min weekend cap`() {
        val plan = planner.computePlan(
            debtStatusLevel = DebtStatusLevel.HIGH,
            cumulativeDebtMinutes = 400,
            typicalWakeMinutesOfDay = typicalWakeMin,
            isRestDay = true
        )
        assertEquals(90, plan.weekendSleepCapMin)
    }

    // ─── CRITICAL ──────────────────────────────────────────────────────────────

    @Test
    fun `CRITICAL debt returns CRITICAL_REST with 30 extra bedtime minutes`() {
        val plan = planner.computePlan(
            debtStatusLevel = DebtStatusLevel.CRITICAL,
            cumulativeDebtMinutes = 600,
            typicalWakeMinutesOfDay = typicalWakeMin,
            isRestDay = false
        )

        assertEquals(SleepDebtRecoveryPlanner.RecoveryStrategy.CRITICAL_REST, plan.recoveryStrategy)
        assertEquals(30, plan.extraBedtimeMinutes)
        assertEquals(25, plan.napRecommendationMin)
        assertTrue("CRITICAL summary should contain warning", plan.summaryText.contains("⚠"))
    }

    @Test
    fun `CRITICAL debt on rest day includes 90min weekend cap`() {
        val plan = planner.computePlan(
            debtStatusLevel = DebtStatusLevel.CRITICAL,
            cumulativeDebtMinutes = 600,
            typicalWakeMinutesOfDay = typicalWakeMin,
            isRestDay = true
        )
        assertEquals(90, plan.weekendSleepCapMin)
    }

    // ─── NAP WINDOW ────────────────────────────────────────────────────────────

    @Test
    fun `Nap window is calculated from typical wake time plus 6-5 to 8 hours`() {
        // Wake 7:00 → napStart = 420+390=810 (13:30), napEnd = 420+480=900 (15:00)
        val plan = planner.computePlan(
            debtStatusLevel = DebtStatusLevel.MILD,
            cumulativeDebtMinutes = 200,
            typicalWakeMinutesOfDay = typicalWakeMin,
            isRestDay = false
        )

        val expectedStart = (typicalWakeMin + 390) % 1440
        val expectedEnd = (typicalWakeMin + 480) % 1440
        assertEquals(expectedStart, plan.napWindowStartMin)
        assertEquals(expectedEnd, plan.napWindowEndMin)
    }

    @Test
    fun `Nap window wraps correctly for late wake times`() {
        // Wake 23:00 (1380 min) → napStart = (1380+390)%1440 = 1770%1440=330 (5:30)
        val lateWake = 23 * 60
        val plan = planner.computePlan(
            debtStatusLevel = DebtStatusLevel.MILD,
            cumulativeDebtMinutes = 200,
            typicalWakeMinutesOfDay = lateWake,
            isRestDay = false
        )
        val expectedStart = (lateWake + 390) % 1440
        val expectedEnd = (lateWake + 480) % 1440
        assertEquals(expectedStart, plan.napWindowStartMin)
        assertEquals(expectedEnd, plan.napWindowEndMin)
    }

    @Test
    fun `Nap window values are always within valid day range`() {
        listOf(0, 6*60, 12*60, 18*60, 23*60).forEach { wakeMin ->
            listOf(DebtStatusLevel.OPTIMAL, DebtStatusLevel.MILD, DebtStatusLevel.HIGH, DebtStatusLevel.CRITICAL)
                .forEach { level ->
                    val plan = planner.computePlan(level, 300, wakeMin, false)
                    assertTrue("napWindowStartMin out of range for wakeMin=$wakeMin level=$level",
                        plan.napWindowStartMin in 0 until 1440)
                    assertTrue("napWindowEndMin out of range for wakeMin=$wakeMin level=$level",
                        plan.napWindowEndMin in 0 until 1440)
                }
        }
    }
}
