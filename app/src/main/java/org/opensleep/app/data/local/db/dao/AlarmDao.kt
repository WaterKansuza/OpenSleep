package org.opensleep.app.data.local.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.opensleep.app.data.local.db.entity.AlarmConfigEntity

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarm_config ORDER BY hour ASC, minute ASC")
    fun observeAlarms(): Flow<List<AlarmConfigEntity>>

    @Query("SELECT * FROM alarm_config WHERE isEnabled = 1")
    suspend fun getEnabledAlarms(): List<AlarmConfigEntity>

    @Query("SELECT * FROM alarm_config WHERE id = :alarmId")
    suspend fun getAlarmById(alarmId: Long): AlarmConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlarm(alarm: AlarmConfigEntity): Long

    @Delete
    suspend fun deleteAlarm(alarm: AlarmConfigEntity)
}
