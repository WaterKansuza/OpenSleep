package org.opensleep.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.opensleep.app.data.local.db.SleepStage

@Entity(
    tableName = "sleep_epoch",
    foreignKeys = [
        ForeignKey(
            entity = SleepSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId", "timestampMs"])]
)
data class SleepEpochEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long,
    val timestampMs: Long,
    val stage: SleepStage,
    val motionIntensity: Float,     // Độ lớn chuyển động [0..1+]
    val breathingRateBpm: Float?,   // Nhịp thở/phút (chỉ có khi dùng Sonar)
    val confidence: Float = 1.0f
)
