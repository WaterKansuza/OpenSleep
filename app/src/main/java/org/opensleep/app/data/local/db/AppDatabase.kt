package org.opensleep.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.opensleep.app.data.local.db.dao.AlarmDao
import org.opensleep.app.data.local.db.dao.SleepDebtDao
import org.opensleep.app.data.local.db.dao.SleepSessionDao
import org.opensleep.app.data.local.db.entity.*

@Database(
    entities = [
        UserSleepProfileEntity::class,
        DailySleepDebtSnapshotEntity::class,
        SleepSessionEntity::class,
        SleepEpochEntity::class,
        SleepNoteEntity::class,
        SessionNoteCrossRef::class,
        MoodEntryEntity::class,
        DreamJournalEntity::class,
        SoundEventEntity::class,
        AlarmConfigEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(SleepConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sleepSessionDao(): SleepSessionDao
    abstract fun sleepDebtDao(): SleepDebtDao
    abstract fun alarmDao(): AlarmDao
}
