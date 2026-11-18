package org.opensleep.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.opensleep.app.data.local.db.NoteImpactType

@Entity(tableName = "sleep_note")
data class SleepNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val iconKey: String,
    val impactType: NoteImpactType = NoteImpactType.NEUTRAL,
    val isDefault: Boolean = false,
    val displayOrder: Int = 0
)
