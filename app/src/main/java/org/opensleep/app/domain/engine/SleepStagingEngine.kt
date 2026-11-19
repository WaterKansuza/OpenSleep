package org.opensleep.app.domain.engine

import org.opensleep.app.data.local.db.SleepStage
import org.opensleep.app.data.local.db.entity.SleepEpochEntity
import org.opensleep.app.service.tracking.EpochReading
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class SleepStagingEngine @Inject constructor() {

    companion object {
        private const val AWAKE_INSTANT_THRESHOLD = 0.42f
        private const val AWAKE_WEIGHTED_ENTER = 0.34f
        private const val AWAKE_WEIGHTED_STAY = 0.24f
        private const val LIGHT_MOTION_THRESHOLD = 0.09f
        private const val DEEP_STILLNESS_EPOCHS_REQUIRED = 6
    }

    fun classifyRealtimeEpoch(
        current: EpochReading,
        recentHistory: List<SleepEpochEntity>,
        totalElapsedEpochs: Int = recentHistory.size
    ): SleepStage {
        val window = (recentHistory.takeLast(5).map { it.motionIntensity } + current.motionIntensity)
        val weightedMotion = computeWeightedWindowMotion(window)
        val lastStage = recentHistory.lastOrNull()?.stage

        val awakeThreshold = if (lastStage == SleepStage.AWAKE) AWAKE_WEIGHTED_STAY else AWAKE_WEIGHTED_ENTER
        if (current.motionIntensity >= AWAKE_INSTANT_THRESHOLD || weightedMotion >= awakeThreshold) {
            return SleepStage.AWAKE
        }

        if (weightedMotion >= LIGHT_MOTION_THRESHOLD) {
            return SleepStage.LIGHT
        }

        val recentCalmCount = recentHistory.takeLast(DEEP_STILLNESS_EPOCHS_REQUIRED)
            .count { it.motionIntensity < LIGHT_MOTION_THRESHOLD }

        if (lastStage == null || lastStage == SleepStage.AWAKE || recentCalmCount < DEEP_STILLNESS_EPOCHS_REQUIRED) {
            return SleepStage.LIGHT
        }

        val recentBreaths = (recentHistory.takeLast(5).mapNotNull { it.breathingRateBpm } +
            listOfNotNull(current.breathingRateBpm))
        if (recentBreaths.size >= 4) {
            val meanBpm = recentBreaths.average().toFloat()
            val stdBpm = computeStdDev(recentBreaths, meanBpm)
            if (stdBpm > 1.25f || meanBpm > 16.2f) {
                return SleepStage.REM
            }
            if (meanBpm in 9.0f..15.8f && stdBpm <= 1.05f) {
                return SleepStage.DEEP
            }
            return SleepStage.LIGHT
        }

        // Passive Mode fallback: ước lượng REM ở pha cuối chu kỳ 90p sau 65p ngủ
        val cyclePos = (totalElapsedEpochs % 180) / 180f
        if (totalElapsedEpochs >= 130 && cyclePos in 0.70f..0.92f && weightedMotion in 0.025f..0.085f) {
            return SleepStage.REM
        }

        return if (weightedMotion < 0.04f && recentCalmCount >= DEEP_STILLNESS_EPOCHS_REQUIRED) {
            SleepStage.DEEP
        } else {
            SleepStage.LIGHT
        }
    }

    fun postProcessNightEpochs(rawEpochs: List<SleepEpochEntity>): List<SleepEpochEntity> {
        if (rawEpochs.size < 6) return rawEpochs
        val result = rawEpochs.toMutableList()

        for (i in 0 until minOf(6, result.size)) {
            if (result[i].stage == SleepStage.DEEP || result[i].stage == SleepStage.REM) {
                result[i] = result[i].copy(stage = SleepStage.LIGHT)
            }
        }

        for (i in 1 until result.size - 1) {
            val prev = result[i - 1].stage
            val curr = result[i].stage
            val next = result[i + 1].stage
            if (curr == SleepStage.AWAKE && prev != SleepStage.AWAKE && next != SleepStage.AWAKE) {
                if (result[i].motionIntensity < 0.55f) {
                    result[i] = result[i].copy(stage = SleepStage.LIGHT)
                }
            }
        }

        for (i in 1 until result.size - 1) {
            val prev = result[i - 1].stage
            val curr = result[i].stage
            val next = result[i + 1].stage
            if (curr == SleepStage.DEEP && prev != SleepStage.DEEP && next != SleepStage.DEEP) {
                result[i] = result[i].copy(stage = SleepStage.LIGHT)
            }
        }
        return result
    }

    private fun computeWeightedWindowMotion(window: List<Float>): Float {
        if (window.isEmpty()) return 0f
        var weightedSum = 0f
        var weightTotal = 0f
        window.forEachIndexed { idx, value ->
            val w = (idx + 1).toFloat()
            weightedSum += value * w
            weightTotal += w
        }
        return weightedSum / weightTotal
    }

    private fun computeStdDev(values: List<Float>, mean: Float): Float {
        if (values.size < 2) return 0f
        var varianceSum = 0.0
        for (v in values) {
            val diff = v - mean
            varianceSum += diff * diff
        }
        return sqrt(varianceSum / values.size).toFloat()
    }
}