package ph.basaya.app.speech

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CompletableDeferred
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Keeps the microphone warm; only an active hold retains audio. */
class VoiceRecorder {
    sealed class Result {
        class Speech(val wav: ByteArray) : Result()
        object NoSpeech : Result()
        object MicUnavailable : Result()
    }

    private class Capture(val onLevel: (Float) -> Unit) {
        val result = CompletableDeferred<Result>()
        val pcm = ByteArrayOutputStream()
        var speechFrames = 0
        var frames = 0
        var level = 0f
        var noiseFloor = 0f
    }
    private class Worker {
        @Volatile var active = true
        @Volatile var recorder: AudioRecord? = null
    }
    private val lock = Any()
    private var worker: Worker? = null
    private var capture: Capture? = null

    /** Permission must already be granted. Hardware initialization happens off the UI thread. */
    fun prepare() = synchronized(lock) {
        if (worker != null) return@synchronized
        val next = Worker()
        worker = next
        Thread({ pump(next) }, "PronunciationMicrophone").start()
    }

    @SuppressLint("MissingPermission")
    private fun pump(owner: Worker) {
        var recorder: AudioRecord? = null
        try {
            val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
            check(minimum > 0)
            val input = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE, CHANNEL, ENCODING, maxOf(minimum, FRAME_SAMPLES * 2 * 4))
            recorder = input
            owner.recorder = input
            check(input.state == AudioRecord.STATE_INITIALIZED)
            if (!owner.active) return
            input.startRecording()
            val frame = ShortArray(FRAME_SAMPLES)
            while (owner.active) {
                val held = synchronized(lock) { if (worker === owner) capture else null }
                val read = input.read(frame, 0, frame.size)
                check(read >= 0)
                if (read == 0) continue
                synchronized(lock) {
                    // Discard idle audio and any read spanning a cancelled/replaced hold.
                    if (held != null && capture === held && worker === owner && held.frames < MAX_FRAMES) {
                        var squares = 0.0
                        for (i in 0 until read) {
                            val sample = frame[i].toInt()
                            held.pcm.write(sample and 255)
                            held.pcm.write((sample shr 8) and 255)
                            squares += sample.toDouble() * sample
                        }
                        val rms = (sqrt(squares / read) / Short.MAX_VALUE).toFloat()
                        if (held.frames < 10) held.noiseFloor = minOf(
                            (held.noiseFloor * held.frames + rms) / (held.frames + 1), MIN_SPEECH_RMS / 4f)
                        if (rms > MIN_SPEECH_RMS / 2f) held.speechFrames++
                        val threshold = maxOf(held.noiseFloor * 3f, MIN_SPEECH_RMS)
                        val level = ((rms - held.noiseFloor) / (threshold * 4f)).coerceIn(0f, 1f)
                        held.level += (level - held.level) * if (level > held.level) 0.6f else 0.2f
                        held.onLevel(held.level)
                        held.frames++
                    }
                }
            }
        } catch (_: Exception) {
            synchronized(lock) {
                if (worker === owner) {
                    capture?.result?.complete(Result.MicUnavailable)
                    capture = null
                }
            }
        } finally {
            try { recorder?.stop() } catch (_: Exception) { }
            recorder?.release()
            synchronized(lock) { if (worker === owner) worker = null }
        }
    }

    suspend fun record(whileHeld: () -> Boolean, onLevel: (Float) -> Unit): Result {
        val current = Capture(onLevel)
        synchronized(lock) {
            if (!whileHeld()) return Result.NoSpeech
            check(capture == null)
            capture = current
            prepare()
        }
        try {
            return current.result.await()
        } finally {
            synchronized(lock) { if (capture === current) capture = null }
            onLevel(0f)
        }
    }

    /** Ends the clip at release, while leaving the hardware ready for the next hold. */
    fun finish() = synchronized(lock) {
        val current = capture ?: return@synchronized
        capture = null
        current.result.complete(if (current.speechFrames < MIN_SPEECH_FRAMES) Result.NoSpeech
            else Result.Speech(wav(current.pcm.toByteArray())))
    }

    fun close() {
        val previous = synchronized(lock) {
            val previous = worker
            worker = null
            previous?.active = false
            capture?.result?.cancel()
            capture = null
            previous
        }
        // Unblock a pending read. The worker owns release(), including initialization races.
        try { previous?.recorder?.stop() } catch (_: Exception) { }
    }

    private fun wav(pcm: ByteArray): ByteArray {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(SAMPLE_RATE); putInt(SAMPLE_RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(pcm.size)
        }
        return header.array() + pcm
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val FRAME_SAMPLES = 320
        const val MIN_SPEECH_RMS = 0.015f
        const val MIN_SPEECH_FRAMES = 4
        const val MAX_FRAMES = 350
    }
}
