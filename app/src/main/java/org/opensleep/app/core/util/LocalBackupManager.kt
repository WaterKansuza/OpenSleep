package org.opensleep.app.core.util

import android.content.Context
import android.net.Uri
import android.util.Base64
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
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BackupEpochDto(
    val timestampMs: Long,
    val stage: SleepStage,
    val motionIntensity: Float,
    val breathingRateBpm: Float? = null,
    val confidence: Float = 1.0f
)

@Serializable
data class BackupSoundEventDto(
    val offsetSeconds: Int,
    val durationSeconds: Int,
    val category: SoundCategory,
    val confidence: Float,
    val decibelPeak: Float
)

@Serializable
data class BackupMoodDto(
    val dateEpochDay: Long,
    val morningEnergyScore: Int,
    val moodLabel: String,
    val createdAtMs: Long
)

@Serializable
data class BackupDreamDto(
    val dateEpochDay: Long,
    val content: String,
    val tagsBitmask: Int = 0,
    val createdAtMs: Long
)

@Serializable
data class BackupPayload(
    val schemaVersion: Int = 3,
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
    val wakeMode: WakeMode = WakeMode.NATURAL_NO_ALARM,
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
    val sleepRating: Int? = null,
    val epochs: List<BackupEpochDto> = emptyList(),
    val soundEvents: List<BackupSoundEventDto> = emptyList(),
    val mood: BackupMoodDto? = null,
    val dream: BackupDreamDto? = null
)

@Serializable
data class EncryptedBackupEnvelope(
    val format: String = ENCRYPTED_FORMAT_ID,
    val iterations: Int = PBKDF2_ITERATIONS,
    val saltBase64: String,
    val ivBase64: String,
    val cipherBase64: String
) {
    companion object {
        const val ENCRYPTED_FORMAT_ID = "OPEN_SLEEP_ENC_V1"
        const val PBKDF2_ITERATIONS = 120_000
    }
}

@Singleton
class LocalBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val sleepDebtRepository: SleepDebtRepository
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun exportToUri(targetUri: Uri, passphrase: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val profile = database.sleepDebtDao().getProfile() ?: UserSleepProfileEntity()
            val sessionDao = database.sleepSessionDao()
            val allSessions = sessionDao.observeAllSessions().first()
                .filter { !it.isIncomplete }

            val sessionDtos = allSessions.map { s ->
                val epochs = sessionDao.getEpochsForSession(s.id).map { ep ->
                    BackupEpochDto(ep.timestampMs, ep.stage, ep.motionIntensity, ep.breathingRateBpm, ep.confidence)
                }
                val sounds = sessionDao.getSoundEventsForSession(s.id).map { ev ->
                    BackupSoundEventDto(ev.offsetSeconds, ev.durationSeconds, ev.category, ev.confidence, ev.decibelPeak)
                }
                val mood = sessionDao.getMoodForSession(s.id)?.let { m ->
                    BackupMoodDto(m.dateEpochDay, m.morningEnergyScore, m.moodLabel, m.createdAtMs)
                }
                val dream = sessionDao.getDreamForSession(s.id)?.let { d ->
                    BackupDreamDto(d.dateEpochDay, d.content, d.tagsBitmask, d.createdAtMs)
                }
                BackupSessionDto(
                    referenceDateEpochDay = s.referenceDateEpochDay,
                    sessionType = s.sessionType,
                    trackingMode = s.trackingMode,
                    wakeMode = s.wakeMode,
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
                    sleepRating = s.sleepRating,
                    epochs = epochs,
                    soundEvents = sounds,
                    mood = mood,
                    dream = dream
                )
            }

            val payload = BackupPayload(
                exportedAtMs = System.currentTimeMillis(),
                ageGroup = profile.ageGroup,
                targetSleepNeedMinutes = profile.targetSleepNeedMinutes,
                calibratedSleepNeedMinutes = profile.calibratedSleepNeedMinutes,
                autoCalibrateEnabled = profile.autoCalibrateEnabled,
                typicalWakeMinutesOfDay = profile.typicalWakeMinutesOfDay,
                sessions = sessionDtos
            )

            val plainJson = json.encodeToString(payload)
            val outputText = if (!passphrase.isNullOrBlank()) {
                val envelope = encryptPayload(plainJson, passphrase)
                json.encodeToString(envelope)
            } else {
                plainJson
            }

            context.contentResolver.openOutputStream(targetUri)?.use { os ->
                os.write(outputText.toByteArray(Charsets.UTF_8))
            } ?: error("Không thể mở tệp đích để ghi")

            allSessions.size
        }
    }

    suspend fun isEncryptedBackup(sourceUri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val raw = context.contentResolver.openInputStream(sourceUri)?.bufferedReader()?.use {
                it.readText()
            } ?: return@runCatching false
            raw.contains(EncryptedBackupEnvelope.ENCRYPTED_FORMAT_ID)
        }.getOrDefault(false)
    }

    suspend fun importFromUri(sourceUri: Uri, passphrase: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = context.contentResolver.openInputStream(sourceUri)?.bufferedReader()?.use {
                it.readText()
            } ?: error("Không thể đọc tệp sao lưu")

            val plainJson = if (raw.contains(EncryptedBackupEnvelope.ENCRYPTED_FORMAT_ID)) {
                val envelope = json.decodeFromString<EncryptedBackupEnvelope>(raw)
                val pw = passphrase?.takeIf { it.isNotBlank() }
                    ?: error("Tệp sao lưu đã mã hóa — vui lòng nhập mật khẩu để mở.")
                decryptPayload(envelope, pw)
            } else {
                raw
            }

            val payload = json.decodeFromString<BackupPayload>(plainJson)
            var maxEpochDay = 0L
            var importedCount = 0

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
                    if (sessionDao.countSessionsByStartAndType(dto.startTimeMs, dto.sessionType) == 0) {
                        val newSessionId = sessionDao.insertSession(
                            SleepSessionEntity(
                                referenceDateEpochDay = dto.referenceDateEpochDay,
                                sessionType = dto.sessionType,
                                trackingMode = dto.trackingMode,
                                wakeMode = dto.wakeMode,
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
                        if (dto.epochs.isNotEmpty()) {
                            sessionDao.insertEpochs(
                                dto.epochs.map { ep ->
                                    SleepEpochEntity(
                                        sessionId = newSessionId,
                                        timestampMs = ep.timestampMs,
                                        stage = ep.stage,
                                        motionIntensity = ep.motionIntensity,
                                        breathingRateBpm = ep.breathingRateBpm,
                                        confidence = ep.confidence
                                    )
                                }
                            )
                        }
                        if (dto.soundEvents.isNotEmpty()) {
                            sessionDao.insertSoundEvents(
                                dto.soundEvents.map { ev ->
                                    SoundEventEntity(
                                        sessionId = newSessionId,
                                        offsetSeconds = ev.offsetSeconds,
                                        durationSeconds = ev.durationSeconds,
                                        category = ev.category,
                                        confidence = ev.confidence,
                                        decibelPeak = ev.decibelPeak
                                    )
                                }
                            )
                        }
                        dto.mood?.let { m ->
                            sessionDao.insertMood(
                                MoodEntryEntity(
                                    sessionId = newSessionId,
                                    dateEpochDay = m.dateEpochDay,
                                    morningEnergyScore = m.morningEnergyScore,
                                    moodLabel = m.moodLabel,
                                    createdAtMs = m.createdAtMs
                                )
                            )
                        }
                        dto.dream?.let { d ->
                            sessionDao.insertDream(
                                DreamJournalEntity(
                                    sessionId = newSessionId,
                                    dateEpochDay = d.dateEpochDay,
                                    content = d.content,
                                    tagsBitmask = d.tagsBitmask,
                                    createdAtMs = d.createdAtMs
                                )
                            )
                        }
                        importedCount++
                    }
                }
            }
            if (maxEpochDay > 0L) {
                sleepDebtRepository.recalculateAfterSessionChange(maxEpochDay)
            }
            importedCount
        }
    }

    private fun encryptPayload(plainJson: String, passphrase: String): EncryptedBackupEnvelope {
        val random = SecureRandom()
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val key = deriveAesKey(passphrase, salt, EncryptedBackupEnvelope.PBKDF2_ITERATIONS)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val cipherBytes = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))
        return EncryptedBackupEnvelope(
            saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            cipherBase64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
        )
    }

    private fun decryptPayload(envelope: EncryptedBackupEnvelope, passphrase: String): String {
        return try {
            val salt = Base64.decode(envelope.saltBase64, Base64.NO_WRAP)
            val iv = Base64.decode(envelope.ivBase64, Base64.NO_WRAP)
            val cipherBytes = Base64.decode(envelope.cipherBase64, Base64.NO_WRAP)
            val key = deriveAesKey(passphrase, salt, envelope.iterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (_: Throwable) {
            error("Mật khẩu không đúng hoặc tệp sao lưu bị hỏng.")
        }
    }

    private fun deriveAesKey(passphrase: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }
}