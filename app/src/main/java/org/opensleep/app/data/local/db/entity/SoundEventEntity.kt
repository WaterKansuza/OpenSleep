package org.opensleep.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.opensleep.app.data.local.db.SoundCategory

@Entity(
    tableName = "sound_event",
    foreignKeys = [
        ForeignKey(
            entity = SleepSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class SoundEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long,
    val offsetSeconds: Int,
    val durationSeconds: Int,
    val category: SoundCategory,
    val confidence: Float,
    val decibelPeak: Float,
    val localAudioPath: String? = null // null nếu user tắt lưu file ghi âm
)
