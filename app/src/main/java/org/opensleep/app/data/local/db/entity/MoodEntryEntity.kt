package org.opensleep.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mood_entry",
    foreignKeys = [
        ForeignKey(
            entity = SleepSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["sessionId"]), Index(value = ["dateEpochDay"])]
)
data class MoodEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long?,
    val dateEpochDay: Long,
    val morningEnergyScore: Int, // 1 (Rất mệt) -> 5 (Cực kỳ tỉnh táo)
    val moodLabel: String,
    val createdAtMs: Long
)
