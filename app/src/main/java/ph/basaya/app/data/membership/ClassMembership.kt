package ph.basaya.app.data.membership

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import ph.basaya.app.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Sends an authenticated request; enrollment is exclusively a teacher approval. */
object ClassMembership {
    private val client = OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).build()
    suspend fun requestJoin(code: String): String {
        val user = FirebaseAuth.getInstance().currentUser ?: throw IOException("Mag-log in muli.")
        val token = user.getIdToken(false).await().token ?: throw IOException("Mag-log in muli.")
        val request = Request.Builder()
            .url(BuildConfig.MEMBERSHIP_API_URL.trimEnd('/') + "/api/join-class")
            .header("Authorization", "Bearer " + token)
            .post(JSONObject().put("code", code).toString().toRequestBody("application/json".toMediaType()))
            .build()
        val call = client.newCall(request)
        val response = suspendCancellableCoroutine<Response> { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response) { _, value, _ -> value.close() }
                }
            })
        }
        return response.use {
            val body = withContext(Dispatchers.IO) { it.body?.string().orEmpty() }
            val json = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
            if (!it.isSuccessful) throw IOException(json.optString("error").ifBlank { "Hindi maipadala ang kahilingan. Subukan muli." })
            if (json.optString("status") != "pending") throw IOException("Hindi makumpirma ang kahilingan. Tingnan muli ang katayuan.")
            json.optString("className", "Iyong klase")
        }
    }
}
