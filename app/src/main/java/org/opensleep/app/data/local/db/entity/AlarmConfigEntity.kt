package org.opensleep.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarm_config")
data class AlarmConfigEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val label: String = "Báo thức",
    val hour: Int,
    val minute: Int,
    val daysOfWeekBitmask: Int = 0,   // 0 = 1 lần; bit 0..6 = T2..CN
    val isEnabled: Boolean = true,
    val smartWindowMinutes: Int = 30, // Cửa sổ đánh thức nhẹ (0 = tắt)
    val debtAwareOverride: Boolean = true, // Tự thu hẹp cửa sổ khi Nợ ngủ cao
    val ringtoneUri: String? = null,
    val gentleFadeSeconds: Int = 60,
    val snoozeGestureEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true
)
