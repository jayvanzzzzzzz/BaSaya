package com.example.basaya.data.dictionary

import android.media.AudioAttributes
import android.media.MediaPlayer
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.Normalizer
import kotlin.coroutines.resume

/**
 * A dictionary word read aloud, like the website's js/tts.js. The first person to tap a
 * word's speaker (student, teacher or admin) has the basaya-tts Worker make its audio
 * once with ElevenLabs and save it; every later tap plays the saved file straight from
 * /audio/, with no sign-in check and no ElevenLabs cost. See the website's
 * basaya-tts-worker.md (/dictionary-audio).
 */
class DictionaryAudio {

    private companion object {
        const val WORKER_URL = "https://basaya-tts.jayvanzsingca5.workers.dev"
        const val TIMEOUT_MS = 15_000
    }

    private var player: MediaPlayer? = null

    /**
     * Plays [entry], making its audio first if nobody has tapped it yet. [onDone] runs when
     * it finishes. False when it can't play (offline, the Worker or ElevenLabs is down).
     */
    suspend fun play(entry: Dictionary.Entry, onDone: () -> Unit = {}): Boolean {
        stop()
        val prepared = prepare(savedUrl(entry))
            ?: requestAudio(entry)?.let { prepare(it) }
            ?: return false
        player = prepared
        prepared.setOnCompletionListener { onDone() }
        prepared.start()
        return true
    }

    fun stop() {
        player?.release()
        player = null
    }

    /** Where the Worker saves a word's audio: dictionary/<key>/<16 hex letters of SHA-256>.mp3. */
    private fun savedUrl(entry: Dictionary.Entry): String {
        val spoken = Normalizer.normalize(entry.shown, Normalizer.Form.NFC)
        val hex = MessageDigest.getInstance("SHA-256")
            .digest(spoken.toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { "%02x".format(it) }
        val key = URLEncoder.encode(entry.key, "UTF-8").replace("+", "%20")
        return "$WORKER_URL/audio/dictionary/$key/$hex.mp3"
    }

    /** Asks the Worker to make (or find) the word's audio; its URL, or null on failure. */
    private suspend fun requestAudio(entry: Dictionary.Entry): String? {
        val token = try {
            FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
        } catch (e: Exception) {
            null
        } ?: return null

        return withContext(Dispatchers.IO) {
            try {
                val connection = URL("$WORKER_URL/dictionary-audio").openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = TIMEOUT_MS
                    connection.readTimeout = TIMEOUT_MS
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    connection.outputStream.use {
                        it.write(JSONObject().put("key", entry.key).toString().toByteArray(Charsets.UTF_8))
                    }
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    JSONObject(body).optString("audioUrl").takeIf { it.startsWith("https://") }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /** A player ready to start, or null when the file isn't there (404) or can't play. */
    private suspend fun prepare(url: String): MediaPlayer? = suspendCancellableCoroutine { cont ->
        val player = MediaPlayer()
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        player.setOnPreparedListener { if (cont.isActive) cont.resume(it) else it.release() }
        player.setOnErrorListener { mp, _, _ ->
            mp.release()
            if (cont.isActive) cont.resume(null)
            true
        }
        cont.invokeOnCancellation { player.release() }
        try {
            player.setDataSource(url)
            player.prepareAsync()
        } catch (e: Exception) {
            player.release()
            if (cont.isActive) cont.resume(null)
        }
    }
}
