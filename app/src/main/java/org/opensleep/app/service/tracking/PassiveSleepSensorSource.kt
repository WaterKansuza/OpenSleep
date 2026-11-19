package org.opensleep.app.service.tracking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import org.opensleep.app.data.local.db.TrackingMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

@Singleton
class PassiveSleepSensorSource @Inject constructor(
    @ApplicationContext private val context: Context
) : SleepSensorSource, SensorEventListener {

    override val mode: TrackingMode = TrackingMode.PASSIVE

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var audioJob: Job? = null
    private val audioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val gravity = FloatArray(3)
    private var isGravityInitialized = false

    private val lock = Any()
    private var motionSum = 0.0
    private var motionPeak = 0f
    private var motionSampleCount = 0

    private var soundEnergyPeakDb = -90f
    private var soundActiveFrames = 0
    private var soundTotalFrames = 0

    // Hiệu chuẩn 30s đầu để tránh kẹt ở -55 dB khi phòng có quạt/điều hòa
    private val calibrationSamplesDb = mutableListOf<Float>()
    private var isNoiseCalibrated = false
    private var adaptiveNoiseFloorDb = -48f

    companion object {
        private const val SAMPLE_RATE_HZ = 16_000
        private const val GRAVITY_ALPHA = 0.85f
        private const val MOTION_SCALE = 1.8f
        private const val CALIBRATION_FRAMES_TARGET = 468 // ~30s @ 16kHz / 1024 mẫu
        private const val SOUND_TRANSIENT_MARGIN_DB = 12f
        private const val MAX_SOUND_CONTRIBUTION = 0.15f
    }

    override suspend fun start() {
        resetAccumulators()
        synchronized(lock) {
            calibrationSamplesDb.clear()
            isNoiseCalibrated = false
            adaptiveNoiseFloorDb = -48f
        }
        isGravityInitialized = false

        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        if (hasMicPermission()) {
            startPassiveAudioMonitor()
        }
    }

    override suspend fun stop() {
        sensorManager.unregisterListener(this)
        audioJob?.cancel()
        audioJob = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        if (!isGravityInitialized) {
            gravity[0] = x
            gravity[1] = y
            gravity[2] = z
            isGravityInitialized = true
            return
        }

        gravity[0] = GRAVITY_ALPHA * gravity[0] + (1f - GRAVITY_ALPHA) * x
        gravity[1] = GRAVITY_ALPHA * gravity[1] + (1f - GRAVITY_ALPHA) * y
        gravity[2] = GRAVITY_ALPHA * gravity[2] + (1f - GRAVITY_ALPHA) * z

        val lx = x - gravity[0]
        val ly = y - gravity[1]
        val lz = z - gravity[2]
        val linearMagnitude = sqrt(lx * lx + ly * ly + lz * lz)
        val cleanMotion = if (linearMagnitude < 0.03f) 0f else linearMagnitude

        synchronized(lock) {
            motionSum += cleanMotion
            if (cleanMotion > motionPeak) motionPeak = cleanMotion
            motionSampleCount++
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun startPassiveAudioMonitor() {
        audioJob?.cancel()
        audioJob = audioScope.launch {
            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuffer <= 0) return@launch

            val frameSize = 1024
            val bufferSize = max(minBuffer, frameSize * 4)
            val recorder = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE_HZ,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
            } catch (_: SecurityException) {
                return@launch
            }

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                return@launch
            }

            val pcmBuffer = ShortArray(frameSize)
            try {
                recorder.startRecording()
                while (isActive) {
                    val readCount = recorder.read(pcmBuffer, 0, frameSize)
                    if (readCount > 0) {
                        val db = computeRmsDb(pcmBuffer, readCount)
                        synchronized(lock) {
                            if (!isNoiseCalibrated) {
                                calibrationSamplesDb.add(db)
                                if (calibrationSamplesDb.size >= CALIBRATION_FRAMES_TARGET) {
                                    val sorted = calibrationSamplesDb.sorted()
                                    val p75Idx = ((sorted.size - 1) * 0.75f).toInt()
                                    adaptiveNoiseFloorDb = sorted[p75Idx]
                                    isNoiseCalibrated = true
                                    calibrationSamplesDb.clear()
                                }
                            } else {
                                if (db <= adaptiveNoiseFloorDb + 8f) {
                                    val alpha = if (db < adaptiveNoiseFloorDb) 0.08f else 0.03f
                                    adaptiveNoiseFloorDb = (1f - alpha) * adaptiveNoiseFloorDb + alpha * db
                                }
                                if (db > adaptiveNoiseFloorDb + SOUND_TRANSIENT_MARGIN_DB) {
                                    soundActiveFrames++
                                }
                            }
                            if (db > soundEnergyPeakDb) soundEnergyPeakDb = db
                            soundTotalFrames++
                        }
                    }
                }
            } finally {
                runCatching {
                    recorder.stop()
                    recorder.release()
                }
            }
        }
    }

    override fun collectLatestEpochReading(windowStartMs: Long, windowEndMs: Long): EpochReading {
        val avgMotion: Float
        val peakMotion: Float
        val peakDb: Float
        val soundActiveRatio: Float
        val calibrated: Boolean
        synchronized(lock) {
            avgMotion = if (motionSampleCount > 0) (motionSum / motionSampleCount).toFloat() else 0f
            peakMotion = motionPeak
            peakDb = soundEnergyPeakDb
            calibrated = isNoiseCalibrated
            soundActiveRatio = if (calibrated && soundTotalFrames > 0) {
                soundActiveFrames.toFloat() / soundTotalFrames.toFloat()
            } else 0f
            resetAccumulators()
        }

        val accelScore = (avgMotion * 0.6f + peakMotion * 0.4f) / MOTION_SCALE
        val soundContribution = (soundActiveRatio * 0.35f).coerceIn(0f, MAX_SOUND_CONTRIBUTION)
        val combinedMotion = (accelScore + soundContribution).coerceIn(0f, 1.5f)

        return EpochReading(
            timestampMs = windowEndMs,
            motionIntensity = combinedMotion,
            soundEnergyDb = peakDb,
            breathingRateBpm = null,
            confidence = if (accelerometer != null) 0.88f else 0.70f
        )
    }

    private fun resetAccumulators() {
        synchronized(lock) {
            motionSum = 0.0
            motionPeak = 0f
            motionSampleCount = 0
            soundEnergyPeakDb = -90f
            soundActiveFrames = 0
            soundTotalFrames = 0
        }
    }

    private fun computeRmsDb(samples: ShortArray, count: Int): Float {
        var sumSquares = 0.0
        for (i in 0 until count) {
            val norm = samples[i] / 32768.0
            sumSquares += norm * norm
        }
        val rms = sqrt(sumSquares / count).coerceAtLeast(1e-6)
        return (20.0 * log10(rms)).toFloat()
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
}