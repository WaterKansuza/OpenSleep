package org.opensleep.app.domain.model

import org.opensleep.app.data.local.db.DebtStatusLevel

data class CircadianForecast(
    val morningPeakStartMin: Int,      // Phút trong ngày [0..1439]
    val morningPeakEndMin: Int,
    val afternoonDipStartMin: Int,     // Khung giờ vàng cho Power Nap
    val afternoonDipEndMin: Int,
    val eveningPeakStartMin: Int,      // "Second wind" buổi tối
    val eveningPeakEndMin: Int,
    val melatoninWindowStartMin: Int,  // Giờ đi ngủ tối ưu
    val melatoninWindowEndMin: Int
)

data class DailySleepAggregate(
    val dateEpochDay: Long,
    val hasData: Boolean,
    val nightSleepMinutes: Int,
    val napSleepMinutes: Int,
    val weightedQualityFactor: Float
)

data class SleepDebtAnalysis(
    val dateEpochDay: Long,
    val activeSleepNeedMinutes: Int,
    val nightSleepMinutes: Int,
    val napSleepMinutes: Int,
    val effectiveSleepMinutes: Int,
    val dailyDeltaMinutes: Int,
    val cumulativeDebtMinutes: Int,
    val statusLevel: DebtStatusLevel,
    val circadianForecast: CircadianForecast,
    val recommendedNapMinutes: Int     // 0 nếu không cần, 20 nếu nợ cao
)
