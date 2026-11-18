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
        private const val AWAKE_MOTION_THRESHOLD = 0.32f
        private const val LIGHT_MOTION_THRESHOLD = 0.08f
        private const val DEEP_STILLNESS_EPOCHS_REQUIRED = 6 // ~3 phút bất động liên tục mới vào DEEP
    }

    /**
     * Phân loại theo thời gian thực (Realtime) dựa trên cửa sổ lịch sử gần nhất.
     * Dùng cho UI hiển thị và quyết định kích hoạt Smart Wake.
     */
    fun classifyRealtimeEpoch(
        current: EpochReading,
        recentHistory: List<SleepEpochEntity>
    ): SleepStage {
        val window = (recentHistory.takeLast(5).map { it.motionIntensity } + current.motionIntensity)
        val weightedMotion = computeWeightedWindowMotion(window)

        // 1. Chuyển động mạnh -> AWAKE
        if (current.motionIntensity >= AWAKE_MOTION_THRESHOLD || weightedMotion >= 0.28f) {
            return SleepStage.AWAKE
        }

        // 2. Chuyển động trung bình/trở mình -> LIGHT
        if (weightedMotion >= LIGHT_MOTION_THRESHOLD) {
            return SleepStage.LIGHT
        }

        // 3. Cơ thể hầu như bất động (< 0.08): Kiểm tra lịch sử gần đây có ổn định chưa
        val lastStage = recentHistory.lastOrNull()?.stage
        val recentCalmCount = recentHistory.takeLast(DEEP_STILLNESS_EPOCHS_REQUIRED)
            .count { it.motionIntensity < LIGHT_MOTION_THRESHOLD }

        // Quy tắc sinh lý: Không nhảy thẳng từ AWAKE vào DEEP
        if (lastStage == null || lastStage == SleepStage.AWAKE || recentCalmCount < DEEP_STILLNESS_EPOCHS_REQUIRED) {
            return SleepStage.LIGHT
        }

        // 4. Nếu có dữ liệu Nhịp thở từ Sonar -> Tách REM và DEEP chính xác
        val recentBreaths = (recentHistory.takeLast(5).mapNotNull { it.breathingRateBpm } +
            listOfNotNull(current.breathingRateBpm))

        if (recentBreaths.size >= 4) {
            val meanBpm = recentBreaths.average().toFloat()
            val stdBpm = computeStdDev(recentBreaths, meanBpm)

            // REM: Bất động cơ thể nhưng nhịp thở biến thiên cao (mơ)
            if (stdBpm > 1.4f || meanBpm > 16.5f) {
                return SleepStage.REM
            }
            // DEEP: Thở chậm và cực kỳ đều
            if (meanBpm in 9.0f..15.5f && stdBpm <= 1.0f) {
                return SleepStage.DEEP
            }
            return SleepStage.LIGHT
        }

        // 5. Passive Mode (không có nhịp thở): Dựa vào độ tĩnh tuyệt đối kéo dài
        return if (weightedMotion < 0.03f && recentCalmCount >= DEEP_STILLNESS_EPOCHS_REQUIRED) {
            SleepStage.DEEP
        } else {
            SleepStage.LIGHT
        }
    }

    /**
     * Hậu xử lý toàn bộ chuỗi Epoch khi thức dậy (Webster Smoothing Rules)
     * để vẽ biểu đồ Hypnogram mượt mà và đếm chính xác giấc ngủ sâu.
     */
    fun postProcessNightEpochs(rawEpochs: List<SleepEpochEntity>): List<SleepEpochEntity> {
        if (rawEpochs.size < 6) return rawEpochs
        val result = rawEpochs.toMutableList()

        // Luật 1: 6 epoch đầu tiên (3 phút đầu) tối đa chỉ là LIGHT hoặc AWAKE (Sleep Onset Latency)
        for (i in 0 until minOf(6, result.size)) {
            if (result[i].stage == SleepStage.DEEP || result[i].stage == SleepStage.REM) {
                result[i] = result[i].copy(stage = SleepStage.LIGHT)
            }
        }

        // Luật 2: Làm mượt gai nhiễu AWAKE đơn lẻ (1 epoch <= 30s) kẹp giữa hai pha ngủ
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

        // Luật 3: Không cho phép DEEP đơn lẻ 1 epoch đứng giữa 2 pha LIGHT/AWAKE
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
            val w = (idx + 1).toFloat() // Trọng số cao hơn cho các epoch gần nhất
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
