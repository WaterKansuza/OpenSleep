package org.opensleep.app.domain.engine

import org.opensleep.app.data.local.db.SessionType
import org.opensleep.app.data.local.db.SoundCategory
import org.opensleep.app.data.local.db.TrackingMode
import org.opensleep.app.data.local.db.entity.MoodEntryEntity
import org.opensleep.app.data.local.db.entity.SleepSessionEntity
import org.opensleep.app.data.local.db.entity.SoundEventEntity
import org.opensleep.app.domain.model.SleepHealthReport
import org.opensleep.app.domain.model.SleepHealthWarning
import org.opensleep.app.domain.model.SleepWarningType
import org.opensleep.app.domain.model.WarningSeverity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class SleepWarningEngine @Inject constructor() {

    fun evaluate(
        report7d: SleepHealthReport,
        recentSessions7d: List<SleepSessionEntity>,
        recentMoods7d: List<MoodEntryEntity>,
        soundEventsBySession: Map<Long, List<SoundEventEntity>>,
        demoSessionIds: Set<Long>,
        targetSleepMinutes: Int,
        dismissedEpochByType: Map<String, Long>,
        todayEpochDay: Long
    ): List<SleepHealthWarning> {
        val validNights = recentSessions7d.filter {
            !it.isIncomplete &&
                it.id !in demoSessionIds &&
                it.sessionType == SessionType.NIGHT &&
                it.sleepDurationMs > 0L
        }
        if (validNights.size < 5) return emptyList()

        val warnings = mutableListOf<SleepHealthWarning>()

        // 1. Short Sleep Duration Pattern
        val shortCount = validNights.count {
            (it.sleepDurationMs / 60_000L).toInt() < targetSleepMinutes - 45
        }
        if (shortCount >= 4) {
            warnings += SleepHealthWarning(
                type = SleepWarningType.SHORT_DURATION,
                severity = WarningSeverity.ATTENTION,
                title = "Thời lượng ngủ dưới mục tiêu",
                message = "Bạn thường ngủ ít hơn mục tiêu cá nhân trong các đêm gần đây.",
                evidenceSummary = "${shortCount}/${validNights.size} đêm < ${fmtHm(targetSleepMinutes - 45)} (TB ${fmtHm(report7d.avgSleepMinutes)})",
                confidencePct = min(92, 65 + validNights.size * 4),
                observedDays = validNights.size,
                isLowConfidence = report7d.coveragePct < 70
            )
        }

        // 2. Irregular Sleep Schedule Pattern
        if (report7d.regularity.hasSufficientData && report7d.regularity.bedtimeVariabilityMin >= 45) {
            warnings += SleepHealthWarning(
                type = SleepWarningType.IRREGULAR_SCHEDULE,
                severity = WarningSeverity.INFO,
                title = "Lịch ngủ thay đổi giữa các ngày",
                message = "Giờ ngủ của bạn đang dao động khá nhiều giữa các ngày trong tuần.",
                evidenceSummary = "Dao động ±${report7d.regularity.bedtimeVariabilityMin}p quanh ${report7d.regularity.typicalBedtimeLabel}",
                confidencePct = min(90, 62 + validNights.size * 4),
                observedDays = validNights.size,
                isLowConfidence = report7d.coveragePct < 70
            )
        }

        // 3. Fragmented Sleep Pattern
        val fragmentedCount = validNights.count {
            it.trackingMode != TrackingMode.MANUAL && (it.awakeDurationMs / 60_000L) >= 30L
        }
        if (fragmentedCount >= 3) {
            warnings += SleepHealthWarning(
                type = SleepWarningType.FRAGMENTED_SLEEP,
                severity = WarningSeverity.ATTENTION,
                title = "Ghi nhận thức giữa đêm lặp lại",
                message = "Ứng dụng ghi nhận nhiều khoảng thức giữa đêm; cảm biến có thể bị ảnh hưởng bởi tiếng động phòng hoặc chuyển động nhẹ.",
                evidenceSummary = "${fragmentedCount}/${validNights.size} đêm có WASO ước tính ≥ 30 phút",
                confidencePct = 68,
                observedDays = validNights.size,
                isLowConfidence = true
            )
        }

        // 4. Low Perceived Restoration
        val validIds = validNights.map { it.id }.toSet()
        val lowScoreFromSession = validNights.count { (it.sleepRating ?: 5) <= 2 }
        val lowScoreFromMood = recentMoods7d.count { it.sessionId in validIds && it.morningEnergyScore <= 2 }
        val lowRestCount = maxOf(lowScoreFromSession, lowScoreFromMood)
        if (lowRestCount >= 3) {
            warnings += SleepHealthWarning(
                type = SleepWarningType.LOW_RESTORATION,
                severity = WarningSeverity.INFO,
                title = "Cảm giác chưa hồi phục buổi sáng",
                message = "Bạn có thể cần chú ý đến cảm giác buồn ngủ ban ngày. Nếu kéo dài, hãy cân nhắc trao đổi với chuyên gia y tế.",
                evidenceSummary = "Ghi nhận ${lowRestCount} sáng cảm thấy mệt (điểm ≤ 2/5)",
                confidencePct = 82,
                observedDays = lowRestCount,
                isLowConfidence = false
            )
        }

        // 5. Frequent Nocturnal Sound Signal (non-diagnostic)
        val snoringNights = validNights.count { session ->
            val events = soundEventsBySession[session.id].orEmpty()
            events.count { it.category == SoundCategory.SNORING } >= 5
        }
        if (snoringNights >= 3) {
            warnings += SleepHealthWarning(
                type = SleepWarningType.NOCTURNAL_SOUND_SIGNAL,
                severity = WarningSeverity.INFO,
                title = "Tín hiệu âm thanh ngáy lặp lại",
                message = "Âm thanh giống tiếng ngáy được ghi nhận lặp lại ban đêm (chỉ là tín hiệu âm thanh ước tính, không phải chẩn đoán y khoa).",
                evidenceSummary = "${snoringNights}/${validNights.size} đêm có ≥ 5 cụm tín hiệu âm thanh",
                confidencePct = 64,
                observedDays = snoringNights,
                isLowConfidence = true
            )
        }

        // Anti-repeat suppression: ẩn 3 ngày sau khi người dùng đóng
        return warnings.filter { warning ->
            val dismissedDay = dismissedEpochByType[warning.type.name]
            dismissedDay == null || (todayEpochDay - dismissedDay) >= 3L
        }
    }

    private fun fmtHm(mins: Int): String = "${mins / 60}h ${mins % 60}p"
}