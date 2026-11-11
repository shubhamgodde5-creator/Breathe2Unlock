package com.breathe2unlock.breathing

import java.util.ArrayDeque
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Experimental heuristic fusion detector.
 * Camera pose movement is the primary phase signal; microphone energy must provide
 * recent corroborating variation. This is a prototype, not a clinically validated
 * respiratory-rate or identity-verification system.
 */
class BreathCycleFusionEngine(
    private val onCycleCountChanged: (Int) -> Unit
) {
    private val cameraWindow = ArrayDeque<Float>()
    private val audioWindow = ArrayDeque<Float>()

    private var sawExpansionPeak = false
    private var peakAtMs = 0L
    private var lastAcceptedCycleAtMs = 0L
    private var lastAudioEvidenceAtMs = 0L
    private var count = 0

    @Synchronized
    fun addCameraMetric(value: Float, nowMs: Long = android.os.SystemClock.elapsedRealtime()) {
        if (!value.isFinite()) return
        val z = addAndZScore(cameraWindow, value, 70)

        // Require microphone variation within the recent breath window.
        val audioConfirmed = nowMs - lastAudioEvidenceAtMs <= 4_500L

        if (z >= 0.70f) {
            if (!sawExpansionPeak) {
                sawExpansionPeak = true
                peakAtMs = nowMs
            }
        } else if (sawExpansionPeak && z <= -0.70f) {
            val peakToTrough = nowMs - peakAtMs
            val cycleSpacing = if (lastAcceptedCycleAtMs == 0L) Long.MAX_VALUE
                else nowMs - lastAcceptedCycleAtMs

            if (peakToTrough in 1_200L..8_000L &&
                cycleSpacing >= 2_500L &&
                audioConfirmed
            ) {
                count += 1
                lastAcceptedCycleAtMs = nowMs
                onCycleCountChanged(count)
            }
            sawExpansionPeak = false
        }
    }

    @Synchronized
    fun addAudioRms(rms: Float, nowMs: Long = android.os.SystemClock.elapsedRealtime()) {
        if (!rms.isFinite() || rms < 0f) return
        val logEnergy = ln(rms + 0.00001f)
        val z = addAndZScore(audioWindow, logEnergy, 70)
        if (kotlin.math.abs(z) >= 0.80f) {
            lastAudioEvidenceAtMs = nowMs
        }
    }

    @Synchronized
    fun currentCount(): Int = count

    @Synchronized
    fun reset() {
        cameraWindow.clear()
        audioWindow.clear()
        sawExpansionPeak = false
        peakAtMs = 0L
        lastAcceptedCycleAtMs = 0L
        lastAudioEvidenceAtMs = 0L
        count = 0
        onCycleCountChanged(0)
    }

    private fun addAndZScore(window: ArrayDeque<Float>, value: Float, maxSize: Int): Float {
        // Use the previous samples as the baseline so the current point does not
        // immediately pull its own z-score back toward zero.
        if (window.size < 12) {
            window.addLast(value)
            if (window.size > maxSize) window.removeFirst()
            return 0f
        }
        val mean = window.average().toFloat()
        val variance = window.map { (it - mean) * (it - mean) }.average().toFloat()
        val std = sqrt(variance).coerceAtLeast(0.000001f)
        val z = ((value - mean) / std).coerceIn(-4f, 4f)
        window.addLast(value)
        if (window.size > maxSize) window.removeFirst()
        return z
    }
}
