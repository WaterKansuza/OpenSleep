package org.opensleep.app.data.local.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.opensleep.app.data.local.db.entity.DailySleepDebtSnapshotEntity
import org.opensleep.app.data.local.db.entity.UserSleepProfileEntity

@Dao
interface SleepDebtDao {
    @Query("SELECT * FROM user_sleep_profile WHERE id = 1")
    fun observeProfile(): Flow<UserSleepProfileEntity?>

    @Query("SELECT * FROM user_sleep_profile WHERE id = 1")
    suspend fun getProfile(): UserSleepProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: UserSleepProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshot(snapshot: DailySleepDebtSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshots(snapshots: List<DailySleepDebtSnapshotEntity>)

    @Query("SELECT * FROM daily_sleep_debt_snapshot ORDER BY dateEpochDay DESC LIMIT 1")
    fun observeLatestDebtSnapshot(): Flow<DailySleepDebtSnapshotEntity?>

    @Query("""
        SELECT * FROM daily_sleep_debt_snapshot
        WHERE dateEpochDay BETWEEN :startEpochDay AND :endEpochDay
        ORDER BY dateEpochDay ASC
    """)
    fun observeSnapshotsInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<DailySleepDebtSnapshotEntity>>

    @Query("""
        SELECT * FROM daily_sleep_debt_snapshot
        WHERE dateEpochDay BETWEEN :startEpochDay AND :endEpochDay
        ORDER BY dateEpochDay ASC
    """)
    suspend fun getSnapshotsInRange(startEpochDay: Long, endEpochDay: Long): List<DailySleepDebtSnapshotEntity>
}
