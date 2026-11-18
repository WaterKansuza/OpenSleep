package org.opensleep.app.domain.engine

import org.opensleep.app.data.local.db.DebtStatusLevel
import org.opensleep.app.data.local.db.SessionType
import org.opensleep.app.data.local.db.TrackingMode
import org.opensleep.app.data.local.db.entity.MoodEntryEntity
import org.opensleep.app.data.local.db.entity.SleepSessionEntity
import org.opensleep.app.data.local.db.entity.UserSleepProfileEntity
import org.opensleep.app.domain.model.CircadianForecast
import org.opensleep.app.domain.model.DailySleepAggregate
import org.opensleep.app.domain.model.SleepDebtAnalysis
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Singleton
class SleepDebtEngine @Inject constructor() {

    companion object {
        const val WINDOW_DAYS = 14
        const val MIN_NAP_MINUTES = 15
        const val MAX_NAP_CREDIT_MINUTES = 90
        const val NAP_EFFICIENCY_MULTIPLIER = 0.85f

        const val MAX_DEBT_RECOVERY_PER_DAY_MIN = 90
        const val RECOVERY_EFFICIENCY_MULTIPLIER = 0.85f

        const val MIN_PHYSIOLOGICAL_NEED_MIN = 390 // 6.5h
        const val MAX_PHYSIOLOGICAL_NEED_MIN = 600 // 10.0h
        const val MINUTES_IN_DAY = 1440
    }

    /**
     * Tính hệ số chất lượng giấc ngủ Q_factor trong khoảng [0.75f .. 1.05f].
     */
    fun computeSessionQualityFactor(session: SleepSessionEntity): Float {
        val sleepMinutes = (session.sleepDurationMs / 60_000L).toInt()
        if (sleepMinutes <= 0) return 0.75f

        var factor = 1.0f

        // 1. Phạt phân mảnh giấc ngủ (số lần thức vi mô mỗi giờ)
        val sleepHours = max(1.0f, sleepMinutes / 60.0f)
        val awakeningsPerHour = session.microAwakeningsCount / sleepHours
        val fragmentationPenalty = min(0.20f, awakeningsPerHour * 0.035f)
        factor -= fragmentationPenalty

        // 2. Phạt thức trắng giữa đêm kéo dài (> 20 phút)
        val awakeMinutes = (session.awakeDurationMs / 60_000L).toFloat()
        if (awakeMinutes > 20f) {
            val totalBedMinutes = sleepMinutes + awakeMinutes
            val awakeRatio = awakeMinutes / max(1f, totalBedMinutes)
            factor -= min(0.10f, awakeRatio * 0.35f)
        }

        // 3. Đánh giá kiến trúc giấc ngủ (khi có Sonar/Stage chi tiết)
        if (session.trackingMode == TrackingMode.SONAR) {
            val restorativeMs = session.deepSleepMs + session.remSleepMs
            if (session.sleepDurationMs > 0) {
                val restorativeRatio = restorativeMs.toFloat() / session.sleepDurationMs.toFloat()
                when {
                    restorativeRatio >= 0.40f -> factor += 0.05f
                    restorativeRatio < 0.20f -> factor -= 0.05f
                }
            }
        }

        return factor.coerceIn(0.75f, 1.05f)
    }

    /**
     * Gom nhóm các phiên ngủ (NIGHT + NAP) trong cùng 1 ngày.
     */
    fun aggregateSessionsForDay(
        epochDay: Long,
        sessionsOnDay: List<SleepSessionEntity>
    ): DailySleepAggregate {
        val validSessions = sessionsOnDay.filter { !it.isIncomplete && it.sleepDurationMs > 0L }
        if (validSessions.isEmpty()) {
            return DailySleepAggregate(
                dateEpochDay = epochDay,
                hasData = false,
                nightSleepMinutes = 0,
                napSleepMinutes = 0,
                weightedQualityFactor = 1.0f
            )
        }

        var totalNightMin = 0
        var weightedQualitySum = 0f
        var totalNapMin = 0

        for (session in validSessions) {
            val mins = (session.sleepDurationMs / 60_000L).toInt()
            if (session.sessionType == SessionType.NIGHT) {
                totalNightMin += mins
                val q = computeSessionQualityFactor(session)
                weightedQualitySum += q * mins
            } else {
                if (mins >= MIN_NAP_MINUTES) {
                    totalNapMin += mins
                }
            }
        }

        val avgQuality = if (totalNightMin > 0) {
            (weightedQualitySum / totalNightMin).coerceIn(0.75f, 1.05f)
        } else {
            1.0f
        }

        return DailySleepAggregate(
            dateEpochDay = epochDay,
            hasData = true,
            nightSleepMinutes = totalNightMin,
            napSleepMinutes = totalNapMin,
            weightedQualityFactor = avgQuality
        )
    }

    /**
     * Tính tổng thời gian ngủ hiệu dụng (phút) trong 1 ngày.
     */
    fun calculateEffectiveSleepMinutes(aggregate: DailySleepAggregate): Int {
        if (!aggregate.hasData) return 0
        val nightEffective = (aggregate.nightSleepMinutes * aggregate.weightedQualityFactor).roundToInt()
        val rawNapCredit = (aggregate.napSleepMinutes * NAP_EFFICIENCY_MULTIPLIER).roundToInt()
        val napEffective = min(MAX_NAP_CREDIT_MINUTES, rawNapCredit)
        return nightEffective + napEffective
    }

    /**
     * Hàm cốt lõi: Tính toán Nợ ngủ tích lũy 14 ngày & Dự báo Nhịp sinh học cho targetEpochDay.
     */
    fun analyzeSleepDebtForDay(
        targetEpochDay: Long,
        profile: UserSleepProfileEntity,
        aggregatesByEpochDay: Map<Long, DailySleepAggregate>
    ): SleepDebtAnalysis {
        val activeSleepNeed = if (profile.autoCalibrateEnabled) {
            profile.calibratedSleepNeedMinutes
        } else {
            profile.targetSleepNeedMinutes
        }.coerceIn(MIN_PHYSIOLOGICAL_NEED_MIN, MAX_PHYSIOLOGICAL_NEED_MIN)

        var weightedDebtSum = 0.0
        var targetDayEffective = 0
        var targetDayDelta = 0
        var targetDayNight = 0
        var targetDayNap = 0

        // Quét cửa sổ trượt 14 ngày: k = 0 là targetEpochDay, k = 13 là 13 ngày trước
        for (k in 0 until WINDOW_DAYS) {
            val day = targetEpochDay - k
            val aggregate = aggregatesByEpochDay[day]
            val effectiveMinutes = if (aggregate != null && aggregate.hasData) {
                calculateEffectiveSleepMinutes(aggregate)
            } else {
                // Ngày không có dữ liệu (chưa cài app / quên đo): không phạt oan
                activeSleepNeed
            }

            val rawDelta = activeSleepNeed - effectiveMinutes
            val adjustedDelta = if (rawDelta < 0) {
                // Bất đối xứng khi trả nợ: hồi phục có giới hạn sinh lý
                val recovery = (rawDelta * RECOVERY_EFFICIENCY_MULTIPLIER).roundToInt()
                max(-MAX_DEBT_RECOVERY_PER_DAY_MIN, recovery)
            } else {
                rawDelta
            }

            val weight = exp(-profile.decayLambda.toDouble() * k)
            weightedDebtSum += weight * adjustedDelta

            if (k == 0) {
                targetDayNight = aggregate?.nightSleepMinutes ?: 0
                targetDayNap = aggregate?.napSleepMinutes ?: 0
                targetDayEffective = if (aggregate?.hasData == true) effectiveMinutes else 0
                targetDayDelta = if (aggregate?.hasData == true) adjustedDelta else 0
            }
        }

        val cumulativeDebtMinutes = max(0, weightedDebtSum.roundToInt())
        val statusLevel = classifyDebtLevel(cumulativeDebtMinutes)
        val forecast = forecastCircadianRhythm(
            typicalWakeMinutesOfDay = profile.typicalWakeMinutesOfDay,
            cumulativeDebtMinutes = cumulativeDebtMinutes
        )
        val recommendedNap = when (statusLevel) {
            DebtStatusLevel.HIGH -> 20
            DebtStatusLevel.CRITICAL -> 25
            else -> 0
        }

        return SleepDebtAnalysis(
            dateEpochDay = targetEpochDay,
            activeSleepNeedMinutes = activeSleepNeed,
            nightSleepMinutes = targetDayNight,
            napSleepMinutes = targetDayNap,
            effectiveSleepMinutes = targetDayEffective,
            dailyDeltaMinutes = targetDayDelta,
            cumulativeDebtMinutes = cumulativeDebtMinutes,
            statusLevel = statusLevel,
            circadianForecast = forecast,
            recommendedNapMinutes = recommendedNap
        )
    }

    fun classifyDebtLevel(debtMinutes: Int): DebtStatusLevel = when {
        debtMinutes < 120 -> DebtStatusLevel.OPTIMAL
        debtMinutes < 300 -> DebtStatusLevel.MILD
        debtMinutes < 480 -> DebtStatusLevel.HIGH
        else -> DebtStatusLevel.CRITICAL
    }

    /**
     * Tự động hiệu chỉnh Nhu cầu ngủ cá nhân dựa trên các đêm ngủ đạt điểm năng lượng/đánh giá cao.
     */
    fun autoCalibrateSleepNeed(
        currentNeedMinutes: Int,
        recentSessions: List<SleepSessionEntity>,
        recentMoods: List<MoodEntryEntity>
    ): Int {
        val moodBySessionId = recentMoods.mapNotNull { m -> m.sessionId?.let { it to m } }.toMap()

        val highQualityNights = recentSessions.filter { session ->
            if (session.isIncomplete || session.sessionType != SessionType.NIGHT) return@filter false
            val mins = (session.sleepDurationMs / 60_000L).toInt()
            if (mins !in 330..660) return@filter false // Lọc ngoại lai (< 5.5h hoặc > 11h)

            val ratingOk = (session.sleepRating ?: 0) >= 4
            val energyOk = (moodBySessionId[session.id]?.morningEnergyScore ?: 0) >= 4
            ratingOk || energyOk
        }

        if (highQualityNights.size < 5) return currentNeedMinutes

        val observedAvg = highQualityNights
            .map { (it.sleepDurationMs / 60_000L).toDouble() }
            .average()

        // Exponential Moving Average với bước nhảy tối đa ±15 phút mỗi lần cập nhật
        val delta = ((observedAvg - currentNeedMinutes) * 0.20).roundToInt().coerceIn(-15, 15)
        return (currentNeedMinutes + delta).coerceIn(MIN_PHYSIOLOGICAL_NEED_MIN, MAX_PHYSIOLOGICAL_NEED_MIN)
    }

    /**
     * Dự báo Nhịp sinh học (Process C) theo giờ thức dậy trung bình.
     * Khi nợ ngủ cao, cửa sổ đi ngủ (Melatonin Window) tự động dịch sớm hơn để bù nợ.
     */
    fun forecastCircadianRhythm(
        typicalWakeMinutesOfDay: Int,
        cumulativeDebtMinutes: Int
    ): CircadianForecast {
        fun wrap(min: Int): Int = ((min % MINUTES_IN_DAY) + MINUTES_IN_DAY) % MINUTES_IN_DAY

        val debtAdvanceShift = when {
            cumulativeDebtMinutes >= 480 -> 60
            cumulativeDebtMinutes >= 300 -> 45
            cumulativeDebtMinutes >= 120 -> 20
            else -> 0
        }

        return CircadianForecast(
            morningPeakStartMin = wrap(typicalWakeMinutesOfDay + 120),  // +2.0h
            morningPeakEndMin = wrap(typicalWakeMinutesOfDay + 240),    // +4.0h
            afternoonDipStartMin = wrap(typicalWakeMinutesOfDay + 390), // +6.5h
            afternoonDipEndMin = wrap(typicalWakeMinutesOfDay + 510),   // +8.5h
            eveningPeakStartMin = wrap(typicalWakeMinutesOfDay + 660),  // +11.0h
            eveningPeakEndMin = wrap(typicalWakeMinutesOfDay + 780),    // +13.0h
            melatoninWindowStartMin = wrap(typicalWakeMinutesOfDay + 930 - debtAdvanceShift), // +15.5h
            melatoninWindowEndMin = wrap(typicalWakeMinutesOfDay + 990 - debtAdvanceShift)    // +16.5h
        )
    }

    /**
     * Liên kết Smart Alarm với Nợ ngủ: Nếu đang nợ ngủ cao, bảo vệ tối đa giấc ngủ buổi sáng.
     */
    fun computeDebtAwareSmartWindow(
        baseWindowMinutes: Int,
        debtAwareOverride: Boolean,
        cumulativeDebtMinutes: Int
    ): Int {
        if (!debtAwareOverride || baseWindowMinutes <= 0) return max(0, baseWindowMinutes)
        return when {
            cumulativeDebtMinutes >= 480 -> 0                         // CRITICAL: Cho ngủ kịch trần tới đúng giờ
            cumulativeDebtMinutes >= 300 -> min(baseWindowMinutes, 10) // HIGH: Tối đa 10 phút
            cumulativeDebtMinutes >= 120 -> min(baseWindowMinutes, 20) // MILD: Tối đa 20 phút
            else -> baseWindowMinutes
        }
    }
}
