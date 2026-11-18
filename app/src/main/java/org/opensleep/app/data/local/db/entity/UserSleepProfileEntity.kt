package org.opensleep.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.opensleep.app.data.local.db.AgeGroup

@Entity(tableName = "user_sleep_profile")
data class UserSleepProfileEntity(
    @PrimaryKey val id: Int = 1, // Singleton row
    val ageGroup: AgeGroup = AgeGroup.ADULT,
    val targetSleepNeedMinutes: Int = 480,      // User chọn ban đầu (mặc định 8h)
    val calibratedSleepNeedMinutes: Int = 480,  // Hệ thống tự học sau 14 đêm
    val autoCalibrateEnabled: Boolean = true,
    val typicalWakeMinutesOfDay: Int = 420,     // Mặc định 07:00 (7 * 60) cho Process C
    val decayLambda: Float = 0.085f,            // Hệ số suy giảm nợ ngủ 14 ngày
    val sonarHardwareSupported: Boolean? = null // null = chưa test calibration
)
