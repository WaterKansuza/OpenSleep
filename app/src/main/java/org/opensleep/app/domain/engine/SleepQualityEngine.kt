package org.opensleep.app.domain.engine

import org.opensleep.app.data.local.db.TrackingMode
import org.opensleep.app.data.local.db.entity.SleepEpochEntity
import org.opensleep.app.data.local.db.entity.SleepSessionEntity
import org.opensleep.app.domain.model.DataProvenance
import org.opensleep.app.domain.model.SleepQualityMetrics
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Singleton
class SleepQualityEngine @Inject constructor() {

    fun computeMetrics(
        session: SleepSessionEntity,
        epochs: List<SleepEpochEntity>,
        isDemoData: Boolean
    ): SleepQualityMetrics {
        val tstMin = max(0, (session.sleepDurationMs / 60_000L).toInt())

        if (session.trackingMode == TrackingMode.MANUAL) {
            return SleepQualityMetrics(
                tstMinutes = tstMin,
                tibMinutes = tstMin,
                solMinutes = null,
                wasoMinutes = null,
                sleepEfficiencyPct = null,
                awakeningsCount = session.microAwakeningsCount,
                coveragePct = 100,
                provenance = DataProvenance.MANUAL,
                isDemoData = isDemoData
            )
        }

        val rawTibMin = ((session.endTimeMs - session.startTimeMs) / 60_000L).toInt()
        val tibMin = max(tstMin, rawTibMin)

        val solMin = if (session.asleepTimeMs >= session.startTimeMs) {
            ((session.asleepTimeMs - session.startTimeMs) / 60_000L).toInt()
        } else {
            null
        }

        val wasoMin = max(0, (session.awakeDurationMs / 60_000L).toInt())
        val efficiencyPct = if (tibMin > 0) {
            min(100, ((tstMin.toFloat() / tibMin.toFloat()) * 100f).roundToInt())
        } else {
            null
        }

        val expectedEpochs = max(1, tibMin * 2)
        val coverage = if (epochs.isNotEmpty()) {
            min(100, ((epochs.size.toFloat() / expectedEpochs.toFloat()) * 100f).roundToInt())
        } else {
            85
        }

        val provenance = when (session.trackingMode) {
            TrackingMode.SONAR -> DataProvenance.MEASURED_SONAR
            TrackingMode.PASSIVE -> DataProvenance.MEASURED_PASSIVE
            TrackingMode.MANUAL -> DataProvenance.MANUAL
        }

        return SleepQualityMetrics(
            tstMinutes = tstMin,
            tibMinutes = tibMin,
            solMinutes = solMin,
            wasoMinutes = wasoMin,
            sleepEfficiencyPct = efficiencyPct,
            awakeningsCount = session.microAwakeningsCount,
            coveragePct = coverage,
            provenance = provenance,
            isDemoData = isDemoData
        )
    }
}