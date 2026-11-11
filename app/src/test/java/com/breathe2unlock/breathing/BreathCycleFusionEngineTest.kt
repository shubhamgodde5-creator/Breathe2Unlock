package com.breathe2unlock.breathing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class BreathCycleFusionEngineTest {

    @Test
    fun countsCandidateCyclesWhenCameraAndMicrophoneSignalsVaryTogether() {
        var latestCount = 0
        val engine = BreathCycleFusionEngine { latestCount = it }

        // Synthetic 15 breaths/minute signal. This tests only the state machine,
        // not real-world camera or microphone accuracy.
        for (i in 0..500) {
            val seconds = i * 0.2
            val phase = 2.0 * PI * seconds / 4.0
            val cameraMetric = 1.0 + 0.02 * sin(phase)
            val microphoneRms = 0.02 + 0.015 * (0.5 + 0.5 * sin(phase + 0.4))
            val timestamp = (seconds * 1_000).toLong()
            engine.addAudioRms(microphoneRms.toFloat(), timestamp)
            engine.addCameraMetric(cameraMetric.toFloat(), timestamp)
        }

        assertTrue("Expected several candidate cycles from a clean synthetic signal", latestCount >= 20)
    }

    @Test
    fun doesNotCountCameraOscillationWithoutMicrophoneEvidence() {
        var latestCount = 0
        val engine = BreathCycleFusionEngine { latestCount = it }

        for (i in 0..500) {
            val seconds = i * 0.2
            val phase = 2.0 * PI * seconds / 4.0
            val cameraMetric = 1.0 + 0.02 * sin(phase)
            val timestamp = (seconds * 1_000).toLong()
            engine.addCameraMetric(cameraMetric.toFloat(), timestamp)
        }

        assertEquals(0, latestCount)
    }
}
