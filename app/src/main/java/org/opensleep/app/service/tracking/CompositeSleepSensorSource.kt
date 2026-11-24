package org.opensleep.app.service.tracking

import kotlinx.coroutines.flow.firstOrNull
import org.opensleep.app.data.local.db.TrackingMode
import org.opensleep.app.data.local.db.dao.SleepDebtDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Nguồn cảm biến ghép: Tự động chọn Sonar siêu âm (nếu phần cứng hỗ trợ)
 * hoặc Passive (Accelerometer + Audio) làm nguồn dữ liệu theo dõi giấc ngủ.
 *
 * [FIX] Dọn dẹp comment code rác ở cuối file, đồng bộ drainDetectedSoundEvents với delegate.
 */
@Singleton
class CompositeSleepSensorSource @Inject constructor(
    private val passiveSource: PassiveSleepSensorSource,
    private val sonarSource: SonarSleepSensorSource,
    private val sleepDebtDao: SleepDebtDao
) : SleepSensorSource {

    private var activeDelegate: SleepSensorSource = passiveSource

    override val mode: TrackingMode
        get() = activeDelegate.mode

    override suspend fun start() {
        val profile = sleepDebtDao.observeProfile().firstOrNull()
        // Chỉ dùng Sonar nếu: phần cứng được xác nhận hỗ trợ VÀ thư viện native đã nạp được
        activeDelegate = if (profile?.sonarHardwareSupported == true &&
            SonarNativeBridge.isNativeLoaded) {
            sonarSource
        } else {
            passiveSource
        }
        activeDelegate.start()
    }

    override suspend fun stop() {
        activeDelegate.stop()
    }

    override fun collectLatestEpochReading(windowStartMs: Long, windowEndMs: Long): EpochReading {
        return activeDelegate.collectLatestEpochReading(windowStartMs, windowEndMs)
    }

    override fun drainDetectedSoundEvents(): List<DetectedSoundEvent> {
        return activeDelegate.drainDetectedSoundEvents()
    }
}