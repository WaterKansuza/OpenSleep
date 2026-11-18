package org.opensleep.app.core.util

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.opensleep.app.data.local.db.*
import org.opensleep.app.data.local.db.entity.*
import org.opensleep.app.data.repository.SleepDebtRepository
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BackupPayload(
    val schemaVersion: Int = 1,
    val exportedAtMs: Long,
    val ageGroup: AgeGroup,
    val targetSleepNeedMinutes: Int,
    val calibratedSleepNeedMinutes: Int,
    val autoCalibrateEnabled: Boolean,
    val typicalWakeMinutesOfDay: Int,
    val sessions: List<BackupSessionDto>
)

@Serializable
data class BackupSessionDto(
    val referenceDateEpochDay: Long,
    val sessionType: SessionType,
    val trackingMode: TrackingMode,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val asleepTimeMs: Long,
    val wakeUpTimeMs: Long,
    val zoneOffsetSeconds: Int,
    val awakeDurationMs: Long,
    val sleepDurationMs: Long,
    val lightSleepMs: Long,
    val deepSleepMs: Long,
    val remSleepMs: Long,
    val microAwakeningsCount: Int,
    val sleepQualityFactor: Float,
    val sleepRating: Int? = null
)

@Singleton
class LocalBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val sleepDebtRepository: SleepDebtRepository
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun exportToUri(targetUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val profile = database.sleepDebtDao().getProfile() ?: UserSleepProfileEntity()
            val allSessions = database.sleepSessionDao().observeAllSessions().first()
                .filter { !it.isIncomplete }

            val payload = BackupPayload(
                exportedAtMs = System.currentTimeMillis(),
                ageGroup = profile.ageGroup,
                targetSleepNeedMinutes = profile.targetSleepNeedMinutes,
                calibratedSleepNeedMinutes = profile.calibratedSleepNeedMinutes,
                autoCalibrateEnabled = profile.autoCalibrateEnabled,
                typicalWakeMinutesOfDay = profile.typicalWakeMinutesOfDay,
                sessions = allSessions.map { s ->
                    BackupSessionDto(
                        referenceDateEpochDay = s.referenceDateEpochDay,
                        sessionType = s.sessionType,
                        trackingMode = s.trackingMode,
                        startTimeMs = s.startTimeMs,
                        endTimeMs = s.endTimeMs,
                        asleepTimeMs = s.asleepTimeMs,
                        wakeUpTimeMs = s.wakeUpTimeMs,
                        zoneOffsetSeconds = s.zoneOffsetSeconds,
                        awakeDurationMs = s.awakeDurationMs,
                        sleepDurationMs = s.sleepDurationMs,
                        lightSleepMs = s.lightSleepMs,
                        deepSleepMs = s.deepSleepMs,
                        remSleepMs = s.remSleepMs,
                        microAwakeningsCount = s.microAwakeningsCount,
                        sleepQualityFactor = s.sleepQualityFactor,
                        sleepRating = s.sleepRating
                    )
                }
            )

            val serialized = json.encodeToString(payload)
            context.contentResolver.openOutputStream(targetUri)?.use { os ->
                os.write(serialized.toByteArray(Charsets.UTF_8))
            } ?: error("Không thể mở tệp đích để ghi")

            allSessions.size
        }
    }

    suspend fun importFromUri(sourceUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = context.contentResolver.openInputStream(sourceUri)?.bufferedReader()?.use {
                it.readText()
            } ?: error("Không thể đọc tệp sao lưu")

            val payload = json.decodeFromString<BackupPayload>(raw)
            var maxEpochDay = 0L

            database.withTransaction {
                val currentProfile = database.sleepDebtDao().getProfile() ?: UserSleepProfileEntity()
                database.sleepDebtDao().upsertProfile(
                    currentProfile.copy(
                        ageGroup = payload.ageGroup,
                        targetSleepNeedMinutes = payload.targetSleepNeedMinutes,
                        calibratedSleepNeedMinutes = payload.calibratedSleepNeedMinutes,
                        autoCalibrateEnabled = payload.autoCalibrateEnabled,
                        typicalWakeMinutesOfDay = payload.typicalWakeMinutesOfDay
                    )
                )
                val sessionDao = database.sleepSessionDao()
                payload.sessions.forEach { dto ->
                    if (dto.referenceDateEpochDay > maxEpochDay) maxEpochDay = dto.referenceDateEpochDay
                    sessionDao.insertSession(
                        SleepSessionEntity(
                            referenceDateEpochDay = dto.referenceDateEpochDay,
                            sessionType = dto.sessionType,
                            trackingMode = dto.trackingMode,
                            startTimeMs = dto.startTimeMs,
                            endTimeMs = dto.endTimeMs,
                            asleepTimeMs = dto.asleepTimeMs,
                            wakeUpTimeMs = dto.wakeUpTimeMs,
                            zoneOffsetSeconds = dto.zoneOffsetSeconds,
                            awakeDurationMs = dto.awakeDurationMs,
                            sleepDurationMs = dto.sleepDurationMs,
                            lightSleepMs = dto.lightSleepMs,
                            deepSleepMs = dto.deepSleepMs,
                            remSleepMs = dto.remSleepMs,
                            microAwakeningsCount = dto.microAwakeningsCount,
                            sleepQualityFactor = dto.sleepQualityFactor,
                            sleepRating = dto.sleepRating,
                            isIncomplete = false
                        )
                    )
                }
            }
            if (maxEpochDay > 0L) {
                sleepDebtRepository.recalculateAfterSessionChange(maxEpochDay)
            }
            payload.sessions.size
        }
    }
}
