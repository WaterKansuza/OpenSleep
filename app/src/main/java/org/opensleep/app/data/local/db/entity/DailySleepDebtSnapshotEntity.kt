package org.opensleep.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.opensleep.app.data.local.db.DebtStatusLevel

@Entity(tableName = "daily_sleep_debt_snapshot")
data class DailySleepDebtSnapshotEntity(
    @PrimaryKey val dateEpochDay: Long, // LocalDate.toEpochDay() - tránh lỗi lệch múi giờ
    val sleepNeedMinutes: Int,
    val nightSleepMinutes: Int,
    val napSleepMinutes: Int,
    val effectiveSleepMinutes: Int,     // Sau khi nhân hệ số chất lượng Q_factor & Nap 0.85
    val dailyDeltaMinutes: Int,         // Dương = nợ thêm, Âm = trả nợ
    val cumulativeDebtMinutes: Int,     // Tổng nợ tích lũy 14 ngày có trọng số
    val debtStatusLevel: DebtStatusLevel,
    val updatedAtMs: Long
)
