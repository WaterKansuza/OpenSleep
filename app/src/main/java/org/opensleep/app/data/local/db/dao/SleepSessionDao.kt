package org.opensleep.app.data.local.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.opensleep.app.data.local.db.SessionType
import org.opensleep.app.data.local.db.entity.*

@Dao
interface SleepSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SleepSessionEntity): Long

    @Update
    suspend fun updateSession(session: SleepSessionEntity)

    @Delete
    suspend fun deleteSession(session: SleepSessionEntity)

    @Query("SELECT * FROM sleep_session WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): SleepSessionEntity?

    @Query("SELECT COUNT(*) FROM sleep_session WHERE startTimeMs = :startTimeMs AND sessionType = :sessionType")
    suspend fun countSessionsByStartAndType(startTimeMs: Long, sessionType: SessionType): Int

    @Query("SELECT * FROM sleep_session ORDER BY startTimeMs DESC")
    fun observeAllSessions(): Flow<List<SleepSessionEntity>>

    @Query("""
        SELECT * FROM sleep_session
        WHERE referenceDateEpochDay BETWEEN :startEpochDay AND :endEpochDay
        ORDER BY startTimeMs ASC
    """)
    suspend fun getSessionsInRange(startEpochDay: Long, endEpochDay: Long): List<SleepSessionEntity>

    @Query("""
        SELECT * FROM sleep_session
        WHERE referenceDateEpochDay BETWEEN :startEpochDay AND :endEpochDay
        ORDER BY startTimeMs ASC
    """)
    fun observeSessionsInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<SleepSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpochs(epochs: List<SleepEpochEntity>)

    @Query("SELECT * FROM sleep_epoch WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    suspend fun getEpochsForSession(sessionId: Long): List<SleepEpochEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSoundEvents(events: List<SoundEventEntity>)

    @Query("SELECT * FROM sound_event WHERE sessionId = :sessionId ORDER BY offsetSeconds ASC")
    suspend fun getSoundEventsForSession(sessionId: Long): List<SoundEventEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNotes(notes: List<SleepNoteEntity>)

    @Query("SELECT * FROM sleep_note ORDER BY displayOrder ASC")
    fun observeAllNotes(): Flow<List<SleepNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun attachNoteToSession(crossRef: SessionNoteCrossRef)

    @Query("""
        SELECT n.* FROM sleep_note n
        INNER JOIN session_note_cross_ref r ON n.id = r.noteId
        WHERE r.sessionId = :sessionId
        ORDER BY n.displayOrder ASC
    """)
    suspend fun getNotesForSession(sessionId: Long): List<SleepNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMood(mood: MoodEntryEntity): Long

    @Query("SELECT * FROM mood_entry WHERE dateEpochDay BETWEEN :startEpochDay AND :endEpochDay")
    suspend fun getMoodsInRange(startEpochDay: Long, endEpochDay: Long): List<MoodEntryEntity>

    @Query("SELECT * FROM mood_entry WHERE sessionId = :sessionId ORDER BY createdAtMs DESC LIMIT 1")
    suspend fun getMoodForSession(sessionId: Long): MoodEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDream(dream: DreamJournalEntity): Long

    @Query("SELECT * FROM dream_journal WHERE sessionId = :sessionId ORDER BY createdAtMs DESC LIMIT 1")
    suspend fun getDreamForSession(sessionId: Long): DreamJournalEntity?
}