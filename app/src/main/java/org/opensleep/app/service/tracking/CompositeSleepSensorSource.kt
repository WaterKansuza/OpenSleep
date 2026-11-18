package org.opensleep.app.service.tracking

import kotlinx.coroutines.flow.firstOrNull
import org.opensleep.app.data.local.db.TrackingMode
import org.opensleep.app.data.local.db.dao.SleepDebtDao
import javax.inject.Inject
import javax.inject.Singleton

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
        activeDelegate = if (profile?.sonarHardwareSupported == true) sonarSource else passiveSource
        activeDelegate.start()
    }

    override suspend fun stop() {
        activeDelegate.stop()
    }

    override fun collectLatestEpochReading(windowStartMs: Long, windowEndMs: Long): EpochReading {
        return activeDelegate.collectLatestEpochReading(windowStartMs, windowEndMs)
    }
}
