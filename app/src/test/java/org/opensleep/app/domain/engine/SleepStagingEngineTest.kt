package org.opensleep.app.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.opensleep.app.data.local.db.SleepStage
import org.opensleep.app.data.local.db.entity.SleepEpochEntity
import org.opensleep.app.service.tracking.EpochReading

class SleepStagingEngineTest {

    private lateinit var stagingEngine: SleepStagingEngine

    @Before
    fun setup() {
        stagingEngine = SleepStagingEngine()
    }

    @Test
    fun `cannot jump directly from AWAKE to DEEP sleep`() {
        val history = listOf(mockEpoch(SleepStage.AWAKE, motion = 0.45f))
        val calmReading = EpochReading(1000L, motionIntensity = 0.01f, soundEnergyDb = -60f, breathingRateBpm = 12f)

        val stage = stagingEngine.classifyRealtimeEpoch(calmReading, history)
        assertEquals(SleepStage.LIGHT, stage)
    }

    @Test
    fun `prolonged stillness with steady slow breathing enters DEEP sleep`() {
        val history = (1..7).map {
            mockEpoch(SleepStage.LIGHT, motion = 0.01f, bpm = 12.2f)
        }
        val steadyReading = EpochReading(1000L, motionIntensity = 0.01f, soundEnergyDb = -65f, breathingRateBpm = 12.0f)

        val stage = stagingEngine.classifyRealtimeEpoch(steadyReading, history)
        assertEquals(SleepStage.DEEP, stage)
    }

    @Test
    fun `stillness with high breathing variability detects REM sleep`() {
        val bpmSeries = listOf(12.5f, 16.0f, 13.0f, 17.5f, 14.0f, 18.0f, 13.5f)
        val history = bpmSeries.dropLast(1).map { bpm ->
            mockEpoch(SleepStage.LIGHT, motion = 0.01f, bpm = bpm)
        }
        val reading = EpochReading(1000L, motionIntensity = 0.01f, soundEnergyDb = -65f, breathingRateBpm = bpmSeries.last())

        val stage = stagingEngine.classifyRealtimeEpoch(reading, history)
        assertEquals(SleepStage.REM, stage)
    }

    @Test
    fun `postProcess smooths isolated single awake spike during sleep`() {
        val raw = listOf(
            mockEpoch(SleepStage.LIGHT, 0.1f),
            mockEpoch(SleepStage.LIGHT, 0.1f),
            mockEpoch(SleepStage.LIGHT, 0.05f),
            mockEpoch(SleepStage.LIGHT, 0.05f),
            mockEpoch(SleepStage.LIGHT, 0.05f),
            mockEpoch(SleepStage.LIGHT, 0.05f),
            mockEpoch(SleepStage.DEEP, 0.01f),
            mockEpoch(SleepStage.AWAKE, 0.40f), // Nhiễu đơn lẻ 30 giây
            mockEpoch(SleepStage.DEEP, 0.01f),
            mockEpoch(SleepStage.DEEP, 0.01f)
        )

        val smoothed = stagingEngine.postProcessNightEpochs(raw)
        assertEquals(SleepStage.LIGHT, smoothed[7].stage)
    }

    private fun mockEpoch(stage: SleepStage, motion: Float, bpm: Float? = null) = SleepEpochEntity(
        sessionId = 1L,
        timestampMs = 0L,
        stage = stage,
        motionIntensity = motion,
        breathingRateBpm = bpm
    )
}
