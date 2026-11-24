package org.opensleep.app.domain.engine

import org.opensleep.app.data.local.db.DebtStatusLevel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Bộ Điều phối Giảm Nợ Ngủ — thiết kế riêng cho người bận rộn học tập / làm việc.
 *
 * Triết lý: Không ép người dùng ngủ sớm 2 tiếng vào ngày thường (gây ảnh hưởng đến
 * công việc/học tập buổi tối). Thay vào đó, áp dụng "vi phân trả nợ":
 *  - Ngủ sớm hơn một lượng nhỏ khả thi (15–25 phút) mỗi đêm.
 *  - Tận dụng Power Nap 20 phút vào vùng trũng sinh học buổi trưa.
 *  - Bảo vệ nhịp sinh học cuối tuần: giới hạn ngủ bù tối đa 60–90 phút.
 */
@Singleton
class SleepDebtRecoveryPlanner @Inject constructor() {

    /**
     * Kết quả kế hoạch trả nợ ngủ cho đêm nay.
     *
     * @param extraBedtimeMinutes Số phút nên đi ngủ sớm hơn giờ cơ bản (15 / 25 / 0).
     * @param napRecommendationMin Thời lượng Power Nap buổi trưa được đề xuất (20 / 25 / 0).
     * @param napWindowStartMin Giờ bắt đầu khung thời gian Power Nap tối ưu (phút trong ngày).
     * @param napWindowEndMin Giờ kết thúc khung thời gian Power Nap tối ưu (phút trong ngày).
     * @param weekendSleepCapMin Giới hạn ngủ bù cuối tuần (phút). 0 nếu không áp dụng.
     * @param summaryText Dòng mô tả ngắn gọn hiển thị trên UI màn hình Hôm nay.
     * @param recoveryStrategy Chiến lược đang áp dụng để phân loại trong UI/badge.
     */
    data class RecoveryPlan(
        val extraBedtimeMinutes: Int,
        val napRecommendationMin: Int,
        val napWindowStartMin: Int,
        val napWindowEndMin: Int,
        val weekendSleepCapMin: Int,
        val summaryText: String,
        val recoveryStrategy: RecoveryStrategy
    )

    enum class RecoveryStrategy {
        /** Nợ ngủ thấp hoặc không có — duy trì nhịp bình thường. */
        MAINTAIN,
        /** Nợ nhẹ — ngủ sớm thêm 15p + Power Nap tùy chọn. */
        MICRO_REPAY,
        /** Nợ cao — ngủ sớm thêm 25p + Power Nap 25p bắt buộc + bảo vệ cuối tuần. */
        ACTIVE_REPAY,
        /** Nợ cực kỳ cao — cần can thiệp khẩn cấp, hệ thống chỉ gợi ý nghỉ dưỡng. */
        CRITICAL_REST
    }

    /**
     * Tính toán kế hoạch trả nợ ngủ cho buổi tối hôm nay.
     *
     * @param debtStatusLevel Mức nợ ngủ hiện tại từ SleepDebtEngine.
     * @param cumulativeDebtMinutes Tổng số phút nợ ngủ tích lũy (14 ngày trọng số).
     * @param typicalWakeMinutesOfDay Giờ thức dậy trung bình của người dùng (phút trong ngày),
     *   dùng để tính vùng trũng sinh học (Afternoon Dip) buổi trưa.
     * @param isRestDay Ngày mai có phải ngày nghỉ không (cuối tuần/ngày lễ).
     */
    fun computePlan(
        debtStatusLevel: DebtStatusLevel,
        cumulativeDebtMinutes: Int,
        typicalWakeMinutesOfDay: Int,
        isRestDay: Boolean
    ): RecoveryPlan {
        // Vùng trũng sinh học (Afternoon Dip): Trung bình wakeTime + 6.5h → +8.5h
        // Khung giờ Power Nap tốt nhất thường là 12:30 – 14:00
        val napWindowStart = wrapMinutes(typicalWakeMinutesOfDay + 390) // +6.5h
        val napWindowEnd = wrapMinutes(typicalWakeMinutesOfDay + 480)   // +8.0h (không quá muộn)

        return when (debtStatusLevel) {
            DebtStatusLevel.OPTIMAL -> RecoveryPlan(
                extraBedtimeMinutes = 0,
                napRecommendationMin = 0,
                napWindowStartMin = napWindowStart,
                napWindowEndMin = napWindowEnd,
                weekendSleepCapMin = 0,
                summaryText = "Nợ ngủ ổn định — duy trì nhịp giấc ngủ hiện tại.",
                recoveryStrategy = RecoveryStrategy.MAINTAIN
            )

            DebtStatusLevel.MILD -> {
                // Nợ 2–5h: Ngủ sớm 15 phút, gợi ý Power Nap 20p tùy chọn
                val extraMin = 15
                val weekendCap = if (isRestDay) 60 else 0
                RecoveryPlan(
                    extraBedtimeMinutes = extraMin,
                    napRecommendationMin = 20,
                    napWindowStartMin = napWindowStart,
                    napWindowEndMin = napWindowEnd,
                    weekendSleepCapMin = weekendCap,
                    summaryText = buildString {
                        append("Nợ ngủ nhẹ · Đề xuất ngủ sớm hơn 15 phút tối nay")
                        if (cumulativeDebtMinutes >= 150) {
                            append(" + Power Nap 20p buổi trưa")
                        }
                        append(".")
                    },
                    recoveryStrategy = RecoveryStrategy.MICRO_REPAY
                )
            }

            DebtStatusLevel.HIGH -> {
                // Nợ 5–8h: Ngủ sớm 25 phút + Power Nap 25p + giới hạn ngủ bù cuối tuần 90p
                val extraMin = min(25, cumulativeDebtMinutes / 20)
                val weekendCap = if (isRestDay) 90 else 0
                RecoveryPlan(
                    extraBedtimeMinutes = extraMin,
                    napRecommendationMin = 25,
                    napWindowStartMin = napWindowStart,
                    napWindowEndMin = napWindowEnd,
                    weekendSleepCapMin = weekendCap,
                    summaryText = "Nợ ngủ cao · Nên ngủ sớm thêm ${extraMin}p + Power Nap 25p" +
                        (if (isRestDay) " · Nghỉ cuối tuần không ngủ bù quá 90p." else "."),
                    recoveryStrategy = RecoveryStrategy.ACTIVE_REPAY
                )
            }

            DebtStatusLevel.CRITICAL -> {
                // Nợ > 8h: Mức cực nghiêm trọng, cần giải pháp khẩn cấp
                RecoveryPlan(
                    extraBedtimeMinutes = 30,
                    napRecommendationMin = 25,
                    napWindowStartMin = napWindowStart,
                    napWindowEndMin = napWindowEnd,
                    weekendSleepCapMin = if (isRestDay) 90 else 0,
                    summaryText = "⚠ Nợ ngủ nghiêm trọng · Ưu tiên ngủ sớm ngay tối nay. " +
                        "Hiệu suất nhận thức đang suy giảm rõ rệt.",
                    recoveryStrategy = RecoveryStrategy.CRITICAL_REST
                )
            }
        }
    }

    private fun wrapMinutes(min: Int): Int = ((min % 1440) + 1440) % 1440
}
