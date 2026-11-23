package org.opensleep.app.domain.engine

import org.opensleep.app.data.local.db.SessionType
import org.opensleep.app.data.local.db.TrackingMode
import org.opensleep.app.data.local.db.entity.SleepSessionEntity
import org.opensleep.app.domain.model.SleepHealthReport
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.roundToInt

@Singleton
class SleepReportEngine @Inject constructor(
    private val qualityEngine: SleepQualityEngine,
    private val regularityEngine: SleepRegularityEngine
) {
    fun buildReport(
        sessions: List<SleepSessionEntity>,
        demoSessionIds: Set<Long>,
        startEpochDay: Long,
        endEpochDay: Long,
        rangeLabel: String,
        targetSleepMinutes: Int,
        latestEstimatedDebtMinutes: Int
    ): SleepHealthReport {
        val inRange = sessions.filter {
            !it.isIncomplete && it.referenceDateEpochDay in startEpochDay..endEpochDay
        }
        val excludedDemoCount = inRange.count { it.id in demoSessionIds }
        val realSessions = inRange.filter { it.id !in demoSessionIds }

        val nightSessions = realSessions.filter {
            it.sessionType == SessionType.NIGHT && it.sleepDurationMs > 0L
        }
        val napSessions = realSessions.filter {
            it.sessionType == SessionType.NAP && it.sleepDurationMs > 0L
        }

        val regularity = regularityEngine.analyze(
            sessions = realSessions,
            demoSessionIds = demoSessionIds,
            startEpochDay = startEpochDay,
            endEpochDay = endEpochDay
        )

        val totalDays = (endEpochDay - startEpochDay + 1).toInt().coerceAtLeast(1)
        val validNightsCount = regularity.validNightsCount
        val coveragePct = ((validNightsCount.toFloat() / totalDays.toFloat()) * 100f)
            .roundToInt()
            .coerceIn(0, 100)

        val durations = nightSessions.map { (it.sleepDurationMs / 60_000L).toInt() }
        val avgSleep = if (durations.isNotEmpty()) durations.average().roundToInt() else 0
        val medSleep = linearMedian(durations)

        val metricsList = nightSessions.map {
            qualityEngine.computeMetrics(session = it, epochs = emptyList(), isDemoData = false)
        }
        val effs = metricsList.mapNotNull { it.sleepEfficiencyPct }
        val sols = metricsList.mapNotNull { it.solMinutes }
        val wasos = metricsList.mapNotNull { it.wasoMinutes }

        val avgEff = if (effs.isNotEmpty()) effs.average().roundToInt() else null
        val avgSol = if (sols.isNotEmpty()) sols.average().roundToInt() else null
        val avgWaso = if (wasos.isNotEmpty()) wasos.average().roundToInt() else null

        val napCount = napSessions.size
        val avgNapMin = if (napCount > 0) {
            napSessions.map { (it.sleepDurationMs / 60_000L).toInt() }.average().roundToInt()
        } else {
            0
        }

        val passiveCount = nightSessions.count { it.trackingMode == TrackingMode.PASSIVE }
        val sonarCount = nightSessions.count { it.trackingMode == TrackingMode.SONAR }
        val manualCount = nightSessions.count { it.trackingMode == TrackingMode.MANUAL }
        val sourceLabel = if (nightSessions.isEmpty()) {
            "Chưa có dữ liệu"
        } else {
            "Passive (${passiveCount}) · Sonar (${sonarCount}) · Manual (${manualCount})"
        }

        val observations = buildList {
            if (validNightsCount < min(5, totalDays)) {
                add("Mới có ${validNightsCount}/${totalDays} đêm dữ liệu thật — chưa đủ mẫu để khẳng định xu hướng.")
            } else {
                if (avgSleep < targetSleepMinutes - 35) {
                    add("Ngủ trung bình ${fmtHm(avgSleep)}, thấp hơn mục tiêu ${fmtHm(targetSleepMinutes)}.")
                } else {
                    add("Ngủ trung bình ${fmtHm(avgSleep)}, bám sát mục tiêu cá nhân.")
                }
                if (regularity.hasSufficientData) {
                    if (regularity.bedtimeVariabilityMin <= 30) {
                        add("Giờ đi ngủ ổn định (±${regularity.bedtimeVariabilityMin}p quanh ${regularity.typicalBedtimeLabel}).")
                    } else {
                        add("Giờ đi ngủ dao động ±${regularity.bedtimeVariabilityMin}p giữa các ngày.")
                    }
                }
            }
        }

        return SleepHealthReport(
            rangeLabel = rangeLabel,
            startEpochDay = startEpochDay,
            endEpochDay = endEpochDay,
            totalDays = totalDays,
            validNightsCount = validNightsCount,
            coveragePct = coveragePct,
            avgSleepMinutes = avgSleep,
            medianSleepMinutes = medSleep,
            avgEfficiencyPct = avgEff,
            avgSolMinutes = avgSol,
            avgWasoMinutes = avgWaso,
            napCount = napCount,
            avgNapMinutes = avgNapMin,
            dominantSourceLabel = sourceLabel,
            latestEstimatedDebtMinutes = latestEstimatedDebtMinutes,
            regularity = regularity,
            observations = observations,
            excludedDemoCount = excludedDemoCount
        )
    }

    private fun linearMedian(values: List<Int>): Int {
        if (values.isEmpty()) return 0
        val s = values.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 1) s[m] else ((s[m - 1] + s[m]) / 2.0).roundToInt()
    }

    private fun fmtHm(mins: Int): String = "${mins / 60}h ${mins % 60}p"
}