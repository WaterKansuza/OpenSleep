package org.opensleep.app.service.tracking

import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

data class NativeSonarFrame(
    val motionIntensity: Float,
    val breathingBpm: Float,
    val confidence: Float,
    val gestureImpulse: Float,
    val carrierSnrDb: Float,
    val latestBreathWave: Float
)

@Singleton
class SonarNativeBridge @Inject constructor() {
    companion object {
        init {
            System.loadLibrary("opensleep_sonar")
        }
    }

    private external fun nativeStart(carrierFreqHz: Double): Boolean
    private external fun nativeStop()
    private external fun nativePollMetrics(): FloatArray

    fun startSonar(carrierFreqHz: Double = 18750.0): Boolean = nativeStart(carrierFreqHz)
    fun stopSonar() = nativeStop()

    fun pollMetrics(): NativeSonarFrame {
        val arr = nativePollMetrics()
        return NativeSonarFrame(
            motionIntensity = arr[0],
            breathingBpm = arr[1],
            confidence = arr[2],
            gestureImpulse = arr[3],
            carrierSnrDb = arr[4],
            latestBreathWave = arr[5]
        )
    }

    /**
     * Calibration 2.5 giây: Phát thử sóng 18.75 kHz và đo SNR phản hồi.
     * Trả về Pair(isSupported, measuredSnrDb).
     */
    suspend fun runHardwareCalibrationTest(): Pair<Boolean, Float> {
        if (!startSonar(18750.0)) return false to -99f
        return try {
            var maxSnr = -99f
            repeat(10) {
                delay(250L)
                val frame = pollMetrics()
                if (frame.carrierSnrDb > maxSnr) maxSnr = frame.carrierSnrDb
            }
            // SNR >= 10 dB cho thấy loa & mic thu tốt dải cận siêu âm 18.75 kHz
            (maxSnr >= 10.0f) to maxSnr
        } finally {
            stopSonar()
        }
    }
}
