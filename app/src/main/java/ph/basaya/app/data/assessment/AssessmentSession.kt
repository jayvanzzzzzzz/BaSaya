package ph.basaya.app.data.assessment

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import ph.basaya.app.BuildConfig
import ph.basaya.app.data.progress.PracticeLessons
import ph.basaya.app.data.progress.ProgressSession
import java.util.concurrent.TimeUnit

fun JsonObject.number(name: String, fallback: Int = 0) = get(name)?.takeUnless { it.isJsonNull }?.asInt ?: fallback
fun JsonObject.flag(name: String) = get(name)?.takeUnless { it.isJsonNull }?.asBoolean == true
fun JsonObject.text(name: String) = get(name)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
fun fields(vararg values: Pair<String, Any?>): JsonObject = Gson().toJsonTree(mapOf(*values)).asJsonObject

class AssessmentSession private constructor(
    context: Context, val lessonId: String, val uid: String,
    private val classId: String, private val session: String, val practice: Boolean
) {
    companion object {
        private val http = OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).build()
        suspend fun open(context: Context, lessonId: String): AssessmentSession {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: throw AssessmentFailure(401, "Mag-log in muli.")
            val profile = FirebaseFirestore.getInstance().collection("users").document(uid).get(Source.SERVER).await()
            if (FirebaseAuth.getInstance().currentUser?.uid != uid) throw AssessmentFailure(401, "Nagbago ang account.")
            ProgressSession.bind(uid, profile)
            val practice = PracticeLessons.refresh(context, lessonId)
            return AssessmentSession(context, lessonId, uid, profile.getString("classId").orEmpty(),
                profile.getString("progressSession").orEmpty(), practice).also { result ->
                if (!practice) for (part in listOf("lecture", "quiz", "activity", "game", "pronunciation")) result.dispatcher.replay(result.key(part))
            }
        }
    }

    private val preferences = context.applicationContext.getSharedPreferences("assessment_submissions_v1", Context.MODE_PRIVATE)
    private fun key(part: String) = "$uid:$classId:$session:$lessonId:$part"
    private fun ownsSession() = FirebaseAuth.getInstance().currentUser?.uid == uid &&
        ProgressSession.stamp(uid, emptyMap())["classId"] == classId &&
        ProgressSession.stamp(uid, emptyMap())["progressSession"] == session
    private val dispatcher = SubmissionDispatcher(object : SubmissionStorage {
        override fun read(key: String) = preferences.getString(key, null)
        override fun write(key: String, value: String) {
            if (!preferences.edit().putString(key, value).commit()) throw AssessmentFailure(0, "Hindi ma-save sa telepono. Magbakante ng storage at subukan muli.")
        }
        override fun remove(key: String) {
            if (!preferences.edit().remove(key).commit()) throw AssessmentFailure(0, "Hindi makumpirma ang lokal na tala. Subukan muli.")
        }
    }, ::ownsSession, ::send)

    private suspend fun send(body: JsonObject): JsonObject {
        if (!ownsSession()) throw AssessmentFailure(401, "Nagbago ang account o klase.")
        val user = FirebaseAuth.getInstance().currentUser ?: throw AssessmentFailure(401, "Mag-log in muli.")
        val token = try { user.getIdToken(false).await().token }
        catch (e: com.google.firebase.auth.FirebaseAuthException) { throw AssessmentFailure(401, "Mag-log in muli.") }
            ?: throw AssessmentFailure(401, "Mag-log in muli.")
        if (!ownsSession()) throw AssessmentFailure(401, "Nagbago ang account o klase.")
        return withContext(Dispatchers.IO) {
            val request = Request.Builder().url(BuildConfig.MEMBERSHIP_API_URL.trimEnd('/') + "/api/assessment")
                .header("Authorization", "Bearer $token")
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            http.newCall(request).execute().use { response ->
                val json = runCatching { JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject }.getOrNull()
                if (!response.isSuccessful) throw AssessmentFailure(response.code, json?.text("error").orEmpty()
                    .ifBlank { "Hindi makumpirma ang sagot. Subukan muli." })
                val accepted = json ?: throw AssessmentFailure(0, "Hindi makumpirma ang tugon ng server. Subukan muli.")
                if (body.text("operation") == "finish" && (!accepted.has("finished") ||
                    (body.text("part") != "lecture" && (!accepted.has("score") || !accepted.has("total")))))
                    throw AssessmentFailure(0, "Hindi pa nakumpirma ang resulta ng server. Subukan muli.")
                accepted
            }
        }
    }

    suspend fun request(operation: String, part: String = "", extra: JsonObject = JsonObject()): JsonObject {
        val body = fields("operation" to operation, "classId" to classId, "progressSession" to session,
            "lessonId" to PracticeLessons.contentId(lessonId))
        if (part.isNotEmpty()) body.addProperty("part", part)
        if (practice) {
            body.addProperty("practice", true)
            body.addProperty("entryId", lessonId)
        }
        extra.entrySet().forEach { body.add(it.key, it.value) }
        return if (!practice && operation in listOf("answer", "speech", "resume", "finish")) dispatcher.submit(key(part), body) else send(body)
    }

    suspend fun progress(): JsonObject {
        if (practice) return JsonObject()
        val doc = FirebaseFirestore.getInstance().collection("users").document(uid)
            .collection("assignedLessons").document(PracticeLessons.contentId(lessonId)).get(Source.SERVER).await()
        if (!ownsSession()) throw AssessmentFailure(401, "Nagbago ang account o klase.")
        if (doc.metadata.hasPendingWrites()) throw AssessmentFailure(0, "Hinihintay ang kumpirmadong progreso ng server.")
        return Gson().toJsonTree(doc.data ?: emptyMap<String, Any>()).asJsonObject.apply {
            val resume = getAsJsonObject("resume")
            val state = getAsJsonObject("assessmentState")
            for (part in listOf("lecture", "quiz", "activity", "game", "pronunciation"))
                if (state?.has(part) != true) resume?.remove(part)
        }
    }

    suspend fun finish(part: String, extra: JsonObject = JsonObject()): JsonObject {
        val result = request("finish", part, extra)
        if (!result.flag("finished") && part != "game") throw AssessmentFailure(0, "Hindi pa nakumpirma ang pagkumpleto. Subukan muli.")
        return result
    }
}
