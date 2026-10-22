package com.example.basaya.data.dictionary

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import kotlin.coroutines.resume

/**
 * A dictionary word read aloud, like the website's js/tts.js. The first person to tap a
 * word's speaker (student, teacher or admin) has the basaya-tts Worker make its audio
 * once with ElevenLabs and save it; every later tap plays the saved file straight from
 * /audio/, with no sign-in check and no ElevenLabs cost. See the website's
 * basaya-tts-worker.md (/dictionary-audio).
 */
class DictionaryAudio(context: Context) {

    private companion object {
        const val TAG = "DictionaryAudio"
        const val WORKER_URL = "https://basaya-tts.jayvanzsingca5.workers.dev"
        const val TIMEOUT_MS = 15_000
    }

    private var player: MediaPlayer? = null

    /** Words already fetched, kept on the phone so a second tap plays at once. */
    private val cacheDir = File(context.cacheDir, "dictionary-audio")

    /** The phone's own voice, for when the Worker can't give us a recording. */
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsDone: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            val result = tts?.setLanguage(Locale("fil", "PH"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale("tl", "PH"))
            }
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = finishTts()
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = finishTts()
            })
            ttsReady = true
        }
    }

    /**
     * Plays [entry], making its audio first if nobody has tapped it yet, or reading it with
     * the phone's voice when that fails. [onDone] runs when it finishes. False when nothing
     * can play (no recording and no TextToSpeech on the phone).
     */
    suspend fun play(entry: Dictionary.Entry, onDone: () -> Unit = {}): Boolean {
        stop()
        val file = cachedFile(entry).takeIf { it.exists() }
            ?: download(savedUrl(entry), cachedFile(entry))
            ?: requestAudio(entry)?.let { download(it, cachedFile(entry)) }
        val prepared = file?.let { prepare(it.absolutePath) }
            ?: return speakWithTts(entry, onDone)
        player = prepared
        prepared.setOnCompletionListener { onDone() }
        prepared.start()
        return true
    }

    fun stop() {
        player?.release()
        player = null
        ttsDone = null
        if (ttsReady) tts?.stop()
    }

    /**
     * Fetches [entry]'s saved audio ahead of a tap, so the speaker plays at once. Only
     * fetches a recording someone already made; making one waits for a real tap.
     */
    suspend fun preload(entry: Dictionary.Entry) {
        val file = cachedFile(entry)
        if (!file.exists()) download(savedUrl(entry), file)
    }

    fun release() {
        stop()
        tts?.shutdown()
        tts = null
    }

    private fun speakWithTts(entry: Dictionary.Entry, onDone: () -> Unit): Boolean {
        val engine = tts?.takeIf { ttsReady } ?: return false
        ttsDone = onDone
        return engine.speak(entry.word, TextToSpeech.QUEUE_FLUSH, null, entry.key) == TextToSpeech.SUCCESS
    }

    /** TextToSpeech calls back on its own thread; [onDone] touches views. */
    private fun finishTts() {
        val done = ttsDone ?: return
        ttsDone = null
        Handler(Looper.getMainLooper()).post(done)
    }

    /** Where the Worker saves a word's audio: dictionary/<key>/<16 hex letters of SHA-256>.mp3. */
    private fun savedUrl(entry: Dictionary.Entry): String {
        val key = URLEncoder.encode(entry.key, "UTF-8").replace("+", "%20")
        return "$WORKER_URL/audio/dictionary/$key/${spokenHash(entry)}.mp3"
    }

    /** Named by the spoken text too, so a teacher's accent fix fetches the new audio. */
    private fun cachedFile(entry: Dictionary.Entry): File =
        File(cacheDir, "${entry.key.hashCode().toUInt()}-${spokenHash(entry)}.mp3")

    private fun spokenHash(entry: Dictionary.Entry): String {
        val spoken = Normalizer.normalize(entry.shown, Normalizer.Form.NFC)
        return MessageDigest.getInstance("SHA-256")
            .digest(spoken.toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * Saves [url] to [file]: one plain GET, much quicker than MediaPlayer streaming it (or
     * finding a 404). The file, or null when it isn't there or the download fails.
     */
    private suspend fun download(url: String, file: File): File? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                file.parentFile?.mkdirs()
                // Write beside it, then rename, so a cut-off download never looks finished.
                val part = File("${file.path}.${System.nanoTime()}.part")
                part.outputStream().use { out -> connection.inputStream.use { it.copyTo(out) } }
                if (part.renameTo(file) || file.exists()) file else null.also { part.delete() }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Download failed: $url", e)
            null
        }
    }

    /** Asks the Worker to make (or find) the word's audio; its URL, or null on failure. */
    private suspend fun requestAudio(entry: Dictionary.Entry): String? {
        val token = try {
            FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
        } catch (e: Exception) {
            Log.w(TAG, "No sign-in token", e)
            null
        } ?: return null.also { Log.w(TAG, "Not signed in; can't ask the Worker for ${entry.key}") }

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
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                        Log.w(TAG, "Worker said ${connection.responseCode} for ${entry.key}: $error")
                        return@withContext null
                    }
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    JSONObject(body).optString("audioUrl").takeIf { it.startsWith("https://") }
                        ?: null.also { Log.w(TAG, "Worker gave no audioUrl for ${entry.key}: $body") }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Worker request failed for ${entry.key}", e)
                null
            }
        }
    }

    /** A player ready to start, or null when the file can't play. */
    private suspend fun prepare(path: String): MediaPlayer? = suspendCancellableCoroutine { cont ->
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
            player.setDataSource(path)
            player.prepareAsync()
        } catch (e: Exception) {
            player.release()
            if (cont.isActive) cont.resume(null)
        }
    }
}
