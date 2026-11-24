package org.opensleep.app.domain.model

import org.opensleep.app.domain.engine.SleepDebtRecoveryPlanner

enum class DataProvenance(val labelVi: String) {
    MEASURED_SONAR("Đo trực tiếp Sonar 18.75kHz · Stage ước tính"),
    MEASURED_PASSIVE("Đo Passive (Gia tốc + Mic) · Stage ước tính"),
    ESTIMATED("Ước tính từ dữ liệu cảm biến"),
    MANUAL("Tự nhập thủ công (Không có stage thật)")
}

data class BedtimeRecommendation(
    val recommendedBedtimeMinutesOfDay: Int,
    val recommendedBedtimeLabel: String,
    val targetWakeMinutesOfDay: Int,
    val targetWakeLabel: String,
    val plannedSleepMinutes: Int,
    val isCustomBedtimeActive: Boolean,
    val isWeekendScheduleApplied: Boolean,
    val personalSleepNeedMinutes: Int,
    val estimatedLatencyMinutes: Int,
    val isLatencyEstimatedDefault: Boolean,
    /** Dòng gợi ý nợ ngủ ngắn gọn (giữ tương thích ngược). */
    val debtRecoverySuggestion: String?,
    /** Kế hoạch trả nợ ngủ chi tiết từ SleepDebtRecoveryPlanner — null trước khi tính xong. */
    val recoveryPlan: SleepDebtRecoveryPlanner.RecoveryPlan? = null,
    /** Giờ lên giường ĐÃ cộng thêm thời gian đi ngủ sớm theo kế hoạch trả nợ (phút trong ngày). */
    val adjustedBedtimeMinutesOfDay: Int = recommendedBedtimeMinutesOfDay
)

data class SleepQualityMetrics(
    val tstMinutes: Int,
    val tibMinutes: Int,
    val solMinutes: Int?,
    val wasoMinutes: Int?,
    val sleepEfficiencyPct: Int?,
    val awakeningsCount: Int,
    val coveragePct: Int,
    val provenance: DataProvenance,
    val isDemoData: Boolean
)

data class RegularityNightPoint(
    val epochDay: Long,
    val dayLabel: String,
    val bedtimeMinutes: Int,
    val wakeMinutes: Int,
    val durationMinutes: Int,
    val isWeekend: Boolean
)

data class SleepRegularitySummary(
    val windowDays: Int,
    val validNightsCount: Int,
    val requiredNights: Int,
    val hasSufficientData: Boolean,
    val coveragePct: Int,
    val typicalBedtimeLabel: String,
    val typicalWakeLabel: String,
    val sleepMidpointLabel: String,
    val bedtimeVariabilityMin: Int,
    val wakeVariabilityMin: Int,
    val durationVariabilityMin: Int,
    val weekendShiftMin: Int?,
    val nightlyPoints: List<RegularityNightPoint>
)

data class SleepHealthReport(
    val rangeLabel: String,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val totalDays: Int,
    val validNightsCount: Int,
    val coveragePct: Int,
    val avgSleepMinutes: Int,
    val medianSleepMinutes: Int,
    val avgEfficiencyPct: Int?,
    val avgSolMinutes: Int?,
    val avgWasoMinutes: Int?,
    val napCount: Int,
    val avgNapMinutes: Int,
    val dominantSourceLabel: String,
    val latestEstimatedDebtMinutes: Int,
    val regularity: SleepRegularitySummary,
    val observations: List<String>,
    val excludedDemoCount: Int
)

enum class WarningSeverity {
    INFO,
    ATTENTION
}

enum class SleepWarningType {
    SHORT_DURATION,
    IRREGULAR_SCHEDULE,
    FRAGMENTED_SLEEP,
    LOW_RESTORATION,
    NOCTURNAL_SOUND_SIGNAL
}

data class SleepHealthWarning(
    val type: SleepWarningType,
    val severity: WarningSeverity,
    val title: String,
    val message: String,
    val evidenceSummary: String,
    val confidencePct: Int,
    val observedDays: Int,
    val isLowConfidence: Boolean
)