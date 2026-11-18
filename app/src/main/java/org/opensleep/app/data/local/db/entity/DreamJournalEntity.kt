package org.opensleep.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dream_journal",
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
data class DreamJournalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long?,
    val dateEpochDay: Long,
    val content: String,
    val tagsBitmask: Int = 0, // Bitmask: Lucid, Nightmare, Vivid, Recurring...
    val createdAtMs: Long
)
