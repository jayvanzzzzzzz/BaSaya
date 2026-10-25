package ph.basaya.app.speech

import ph.basaya.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sends a recorded word to the basaya-stt Cloudflare Worker (Whisper, Tagalog).
 * See cloudflare/basaya-stt/README.md for deployment and local.properties setup.
 */
class WhisperSttClient(
    private val workerUrl: String = BuildConfig.STT_WORKER_URL,
    private val appKey: String = BuildConfig.STT_APP_KEY
) {

    sealed class Result {
        class Text(val text: String) : Result()
        /** No connection or the request timed out. */
        object NetworkError : Result()
        /** Worker refused or failed (e.g. free daily limit reached): use the fallback. */
        object Unavailable : Result()
    }

    val isConfigured: Boolean get() = workerUrl.startsWith("https://")

    suspend fun transcribe(wav: ByteArray): Result = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext Result.Unavailable

        val connection = try {
            URL(workerUrl.trimEnd('/') + "/transcribe").openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return@withContext Result.NetworkError
        }

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "audio/wav")
            if (appKey.isNotBlank()) connection.setRequestProperty("X-App-Key", appKey)
            connection.setFixedLengthStreamingMode(wav.size)
            connection.outputStream.use { it.write(wav) }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.Unavailable
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            Result.Text(JSONObject(body).optString("text", "").trim())
        } catch (e: IOException) {
            Result.NetworkError
        } catch (e: Exception) {
            Result.Unavailable
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 15_000
    }
}
