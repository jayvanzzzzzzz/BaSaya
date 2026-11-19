package ph.basaya.app.data.repository

import ph.basaya.app.data.progress.ProgressSession

import android.content.Context
import android.util.Log
import ph.basaya.app.data.database.AppDatabase
import ph.basaya.app.data.entity.ActivityEntity
import ph.basaya.app.data.model.ActivityPage
import ph.basaya.app.data.model.PracticeActivityData
import ph.basaya.app.data.progress.PracticeLessons
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.tasks.await

class ActivityRepository(private val context: Context) {

    private companion object {
        const val TAG = "ActivityRepository"
    }

    private val db by lazy { AppDatabase.getDatabase(context) }
    private val firestore = FirebaseFirestore.getInstance()
    private val gson = Gson()

    /** Read from Room first, then Firestore for display only; only explicit downloads save lesson content to Room. */
    suspend fun getActivity(lessonId: String): PracticeActivityData? {
        if (!PracticeLessons.refresh(context, lessonId)) return fetchFromFirestore(lessonId)
        LessonContentPrefetch.takeActivity(lessonId)?.let { return it }

        val isDownloaded = db.lessonDao().isLessonDownloaded(lessonId) ?: false

        if (isDownloaded) {
            val saved = db.activityDao().getActivity(lessonId)?.toPracticeActivityData()
            if (saved != null) {
                DownloadedLessonRefresh.launch(context, lessonId, "activity:$lessonId") {
                    fetchFromFirestore(lessonId)?.let { db.activityDao().insert(it.toEntity(lessonId)) }
                }
                return saved
            }
        }

        return fetchFromFirestore(lessonId)
    }

    /** Fetches + caches to Room. Called from the Download button flow. */
    suspend fun downloadActivity(lessonId: String): Boolean {
        val fetched = fetchFromFirestore(lessonId) ?: return false
        if (fetched.pages.isEmpty()) return false
        db.activityDao().insert(fetched.toEntity(lessonId))
        return true
    }

    private suspend fun fetchFromFirestore(lessonId: String): PracticeActivityData? {
        val result = ph.basaya.app.data.assessment.AssessmentSession.open(context, lessonId).request("content").getAsJsonObject("activity")
        return PracticeActivityData(id = lessonId, title = result.get("title").asString, instruction = result.get("instruction").asString,
            pages = result.getAsJsonArray("pages").map { item ->
                val page = item.asJsonObject
                ActivityPage(words = page.getAsJsonArray("words").map { it.asString }, pickCount = page.get("pickCount").asInt)
            })
    }

    // Mapping helpers

    private fun ActivityEntity.toPracticeActivityData(): PracticeActivityData {
        val type = object : TypeToken<List<ActivityPage>>() {}.type
        val pages: List<ActivityPage> = gson.fromJson(pagesJson, type)
        return PracticeActivityData(
            id = lessonId,
            title = title,
            instruction = instruction,
            explanation = explanation,
            pages = pages
        )
    }

    private fun PracticeActivityData.toEntity(lessonId: String): ActivityEntity {
        return ActivityEntity(
            lessonId = lessonId,
            title = title,
            instruction = instruction,
            explanation = explanation,
            pagesJson = gson.toJson(pages)
        )
    }
}
