package org.opensleep.app.service.tracking

import android.util.Log
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
        private const val TAG = "SonarNativeBridge"

        /**
         * [FIX] Nạp thư viện native an toàn — bắt UnsatisfiedLinkError thay vì crash app
         * ngay khi khởi động trên thiết bị không hỗ trợ ABI hoặc thiếu file .so.
         */
        val isNativeLoaded: Boolean = try {
            System.loadLibrary("opensleep_sonar")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Thư viện Sonar native không khả dụng trên thiết bị này, " +
                "sẽ dùng chế độ Passive tracking.", e)
            false
        } catch (e: Throwable) {
            Log.e(TAG, "Lỗi không xác định khi nạp thư viện Sonar native.", e)
            false
        }
    }

    private external fun nativeStart(carrierFreqHz: Double): Boolean
    private external fun nativeStop()
    private external fun nativePollMetrics(): FloatArray

    fun startSonar(carrierFreqHz: Double = 18750.0): Boolean {
        if (!isNativeLoaded) return false
        return try {
            nativeStart(carrierFreqHz)
        } catch (e: Throwable) {
            Log.e(TAG, "Lỗi khi gọi nativeStart()", e)
            false
        }
    }

    fun stopSonar() {
        if (!isNativeLoaded) return
        try { nativeStop() } catch (e: Throwable) {
            Log.e(TAG, "Lỗi khi gọi nativeStop()", e)
        }
    }

    fun pollMetrics(): NativeSonarFrame {
        if (!isNativeLoaded) {
            return NativeSonarFrame(0f, 0f, 0f, 0f, -99f, 0f)
        }
        return try {
            val arr = nativePollMetrics()
            NativeSonarFrame(
                motionIntensity = arr.getOrElse(0) { 0f },
                breathingBpm = arr.getOrElse(1) { 0f },
                confidence = arr.getOrElse(2) { 0f },
                gestureImpulse = arr.getOrElse(3) { 0f },
                carrierSnrDb = arr.getOrElse(4) { -99f },
                latestBreathWave = arr.getOrElse(5) { 0f }
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Lỗi khi gọi nativePollMetrics()", e)
            NativeSonarFrame(0f, 0f, 0f, 0f, -99f, 0f)
        }
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
