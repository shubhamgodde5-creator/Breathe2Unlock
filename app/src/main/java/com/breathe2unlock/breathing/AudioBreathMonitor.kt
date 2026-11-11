package com.breathe2unlock.breathing

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.sqrt

/** Captures short microphone buffers only while the visible breathing session is active. */
class AudioBreathMonitor(
    private val onRms: (Float) -> Unit,
    private val onError: (String) -> Unit
) {
    @Volatile private var running = false
    private var recorder: AudioRecord? = null
    private var worker: Thread? = null

    fun start() {
        if (running) return
        val sampleRate = 16_000
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            onError("This device could not initialize microphone capture.")
            return
        }

        val audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer * 2, 4_096)
            )
        } catch (e: Exception) {
            onError(e.message ?: "Microphone initialization failed.")
            return
        }

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            onError("The microphone is unavailable. Check its permission and try again.")
            return
        }

        recorder = audioRecord
        running = true
        worker = Thread {
            val buffer = ShortArray(3_200) // approximately 200 ms at 16 kHz
            try {
                audioRecord.startRecording()
                while (running) {
                    val read = audioRecord.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    if (read > 0) {
                        var sumSquares = 0.0
                        for (i in 0 until read) {
                            val sample = buffer[i].toDouble() / 32_768.0
                            sumSquares += sample * sample
                        }
                        val rms = sqrt(sumSquares / read).toFloat()
                        onRms(rms)
                    } else if (read < 0) {
                        onError("Microphone read failed (code $read).")
                        break
                    }
                }
            } catch (e: SecurityException) {
                onError("Microphone permission was revoked during the session.")
            } catch (e: Exception) {
                onError(e.message ?: "Microphone capture stopped unexpectedly.")
            } finally {
                running = false
                try { audioRecord.stop() } catch (_: Exception) { }
                audioRecord.release()
                if (recorder === audioRecord) recorder = null
            }
        }.apply {
            name = "Breathe2Unlock-Audio"
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        try { recorder?.stop() } catch (_: Exception) { }
        try { worker?.join(700) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        worker = null
        recorder = null
    }
}
