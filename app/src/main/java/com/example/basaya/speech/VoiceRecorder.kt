package com.example.basaya.speech

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt

/**
 * Records one spoken word as 16 kHz mono 16-bit WAV, stopping on its own once the
 * child stops talking. Reports a smoothed 0..1 voice level while recording so the
 * UI can react to the child's voice.
 */
class VoiceRecorder {

    sealed class Result {
        class Speech(val wav: ByteArray) : Result()
        object NoSpeech : Result()
        object MicUnavailable : Result()
    }

    /** Caller must hold RECORD_AUDIO. [onLevel] is called from a background thread. */
    @SuppressLint("MissingPermission")
    suspend fun record(onLevel: (Float) -> Unit): Result = withContext(Dispatchers.IO) {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuffer <= 0) return@withContext Result.MicUnavailable

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE, CHANNEL, ENCODING,
                maxOf(minBuffer, FRAME_SAMPLES * 2 * 4)
            )
        } catch (e: Exception) {
            return@withContext Result.MicUnavailable
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return@withContext Result.MicUnavailable
        }

        val pcm = ByteArrayOutputStream()
        val frame = ShortArray(FRAME_SAMPLES)
        val frameBytes = ByteBuffer.allocate(FRAME_SAMPLES * 2).order(ByteOrder.LITTLE_ENDIAN)

        var noiseFloor = 0f
        var calibrationFrames = 0
        var speechStartFrame = -1
        var lastSpeechFrame = -1
        var frameIndex = 0
        var smoothedLevel = 0f

        try {
            recorder.startRecording()
            while (true) {
                coroutineContext.ensureActive()
                val read = recorder.read(frame, 0, FRAME_SAMPLES)
                if (read <= 0) continue

                var sumSquares = 0.0
                frameBytes.clear()
                for (i in 0 until read) {
                    val sample = frame[i]
                    sumSquares += sample * sample.toDouble()
                    frameBytes.putShort(sample)
                }
                pcm.write(frameBytes.array(), 0, read * 2)
                val rms = (sqrt(sumSquares / read) / Short.MAX_VALUE).toFloat()

                // Learn the room's background noise from the first moments.
                if (calibrationFrames < CALIBRATION_FRAMES) {
                    noiseFloor = (noiseFloor * calibrationFrames + rms) / (calibrationFrames + 1)
                    calibrationFrames++
                }
                val threshold = maxOf(noiseFloor * 3f, MIN_SPEECH_RMS)
                val isSpeech = rms > threshold

                if (isSpeech) {
                    if (speechStartFrame < 0) speechStartFrame = frameIndex
                    lastSpeechFrame = frameIndex
                }

                val level = ((rms - noiseFloor) / (threshold * 4f)).coerceIn(0f, 1f)
                smoothedLevel += (level - smoothedLevel) * if (level > smoothedLevel) 0.6f else 0.2f
                onLevel(smoothedLevel)

                frameIndex++
                val heardEnough = speechStartFrame >= 0 &&
                    frameIndex - lastSpeechFrame > END_SILENCE_FRAMES &&
                    lastSpeechFrame - speechStartFrame >= MIN_SPEECH_FRAMES
                val waitedTooLong = speechStartFrame < 0 && frameIndex > NO_SPEECH_TIMEOUT_FRAMES
                if (heardEnough || waitedTooLong || frameIndex > MAX_FRAMES) break
            }
        } finally {
            try { recorder.stop() } catch (_: Exception) { }
            recorder.release()
            onLevel(0f)
        }

        if (speechStartFrame < 0) return@withContext Result.NoSpeech

        // Keep a little audio before the first sound so the start of the word isn't clipped.
        val startByte = maxOf(0, speechStartFrame - PRE_ROLL_FRAMES) * FRAME_SAMPLES * 2
        val all = pcm.toByteArray()
        Result.Speech(wav(all.copyOfRange(startByte.coerceAtMost(all.size), all.size)))
    }

    private fun wav(pcm: ByteArray): ByteArray {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(36 + pcm.size)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1) // PCM
            putShort(1) // mono
            putInt(SAMPLE_RATE)
            putInt(SAMPLE_RATE * 2)
            putShort(2)
            putShort(16)
            put("data".toByteArray())
            putInt(pcm.size)
        }
        return header.array() + pcm
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val FRAME_SAMPLES = 320 // 20 ms

        const val CALIBRATION_FRAMES = 10      // 200 ms
        const val MIN_SPEECH_RMS = 0.015f
        const val MIN_SPEECH_FRAMES = 4        // 80 ms, ignores clicks and bumps
        const val END_SILENCE_FRAMES = 40      // 800 ms of quiet ends the word
        const val PRE_ROLL_FRAMES = 15         // 300 ms
        const val NO_SPEECH_TIMEOUT_FRAMES = 300 // 6 s
        const val MAX_FRAMES = 350             // 7 s hard limit
    }
}
