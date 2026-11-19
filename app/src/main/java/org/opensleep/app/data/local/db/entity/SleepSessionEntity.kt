package org.opensleep.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.opensleep.app.data.local.db.SessionType
import org.opensleep.app.data.local.db.TrackingMode
import org.opensleep.app.data.local.db.WakeMode

@Entity(
    tableName = "sleep_session",
    indices = [Index(value = ["referenceDateEpochDay"])]
)
data class SleepSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val referenceDateEpochDay: Long,    // Ngày thức dậy (LocalDate.toEpochDay) dùng gom nhóm nợ ngủ
    val sessionType: SessionType = SessionType.NIGHT,
    val trackingMode: TrackingMode = TrackingMode.PASSIVE,
    val wakeMode: WakeMode = WakeMode.NATURAL_NO_ALARM,
    val startTimeMs: Long,              // Lúc bấm "Bắt đầu ngủ" (UTC millis)
    val endTimeMs: Long,                // Lúc tắt tracking / dậy hẳn
    val asleepTimeMs: Long,             // Lúc thực sự thiếp đi
    val wakeUpTimeMs: Long,             // Lúc tỉnh giấc cuối cùng
    val zoneOffsetSeconds: Int,         // Lưu múi giờ tại thời điểm ngủ
    val awakeDurationMs: Long = 0L,     // Tổng thời gian thức trằn trọc giữa đêm
    val sleepDurationMs: Long = 0L,     // Thời lượng ngủ thực tế
    val lightSleepMs: Long = 0L,
    val deepSleepMs: Long = 0L,
    val remSleepMs: Long = 0L,
    val microAwakeningsCount: Int = 0,
    val sleepQualityFactor: Float = 1.0f, // [0.75f .. 1.05f]
    val sleepRating: Int? = null,       // 1..5 sao do user đánh giá sáng hôm sau
    val isIncomplete: Boolean = false
)
