package ph.basaya.app.data.repository

import android.content.Context
import android.util.Log
import ph.basaya.app.data.audio.LectureAudioStore
import ph.basaya.app.data.database.AppDatabase
import ph.basaya.app.data.entity.LectureEntity
import ph.basaya.app.data.model.Lecture
import ph.basaya.app.data.model.LecturePage
import ph.basaya.app.data.progress.PracticeLessons
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.Source

class LectureRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val firestore = FirebaseFirestore.getInstance()
    private val gson = Gson()

    /**
     * Room-first, Firestore-fallback for DISPLAY purposes only.
     * The fallback does NOT write to Room — only a downloaded lesson's copy is kept locally.
     */
    suspend fun getLecture(lessonId: String): Lecture? {
        val isDownloaded = db.lessonDao().isLessonDownloaded(lessonId) ?: false

        if (isDownloaded) {
            val saved = savedLecture(lessonId)
            // When online, refresh the downloaded copy so edits made in basaya-web reach the
            // student (Gawain and quiz do the same). Offline, the saved copy is used as before.
            val fresh = fetchFromFirestore(lessonId) ?: return saved
            return if (refreshSaved(lessonId, fresh, saved)) fresh else saved
        }

        return fetchFromFirestore(lessonId)
    }

    private suspend fun savedLecture(lessonId: String): Lecture? {
        val cached = db.lectureDao().getLecture(lessonId) ?: return null
        val pagesType = object : TypeToken<List<LecturePage>>() {}.type
        return Lecture(
            id = lessonId,
            title = cached.title,
            pages = gson.fromJson(cached.pagesJson, pagesType)
        )
    }

    /**
     * Saves the new version of a downloaded lecture. Its audio is downloaded again only when
     * a sentence's audio changed; if that fails, the saved copy stays as it was (false).
     */
    private suspend fun refreshSaved(lessonId: String, fresh: Lecture, saved: Lecture?): Boolean {
        val audioOf = { lecture: Lecture? -> lecture?.pages?.map { it.pageNumber to it.audioUrls.orEmpty() } }
        if (audioOf(fresh) != audioOf(saved)) {
            try {
                LectureAudioStore.download(context, lessonId, fresh.pages)
            } catch (e: Exception) {
                Log.w("LectureRepository", "Could not update the lecture audio for $lessonId", e)
                return false
            }
        }
        db.lectureDao().insert(
            LectureEntity(
                lessonId = lessonId,
                title = fresh.title,
                pagesJson = gson.toJson(fresh.pages)
            )
        )
        return true
    }

    /** Fetches + caches to Room. Called from the Download button flow. */
    suspend fun downloadLecture(
        lessonId: String,
        onAudioFile: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Lecture? {
        return fetchAndCache(lessonId, onAudioFile)
    }

    /** Kept for any existing manual "refresh this lecture" call sites. */
    suspend fun syncLecture(lessonId: String): Lecture? {
        return fetchAndCache(lessonId)
    }

    suspend fun checkCompletion(lessonId: String): Boolean {
        val snapshot = firestore
            .collection("lessons")
            .document(PracticeLessons.contentId(lessonId))
            .collection("lectures")
            .get()
            .await()

        if (snapshot.isEmpty) return false

        val doc = snapshot.documents.first()
        return doc.getBoolean("finished") ?: false
    }

    private suspend fun fetchAndCache(
        lessonId: String,
        onAudioFile: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Lecture? {
        val lecture = fetchFromFirestore(lessonId) ?: return null

        // Throws on failure so the lesson download is reported as failed, not half-saved.
        LectureAudioStore.download(context, lessonId, lecture.pages, onAudioFile)

        db.lectureDao().insert(
            LectureEntity(
                lessonId = lessonId,
                title = lecture.title,
                pagesJson = gson.toJson(lecture.pages)
            )
        )

        return lecture
    }

    private suspend fun fetchFromFirestore(lessonId: String): Lecture? {
        return try {
            val snapshot = firestore
                .collection("lessons")
                .document(PracticeLessons.contentId(lessonId))
                .collection("lectures")
                .get(Source.SERVER)
                .await()

            if (snapshot.isEmpty) return null

            val doc = snapshot.documents.first()
            val title = doc.getString("title") ?: ""

            @Suppress("UNCHECKED_CAST")
            val pagesRaw = doc.get("pages") as? List<Map<String, Any>> ?: emptyList()

            val pages = pagesRaw.map { pageMap ->
                LecturePage(
                    pageNumber = (pageMap["pageNumber"] as? Long)?.toInt() ?: 0,
                    sentences = pageMap["sentences"] as? List<String> ?: emptyList(),
                    audioResNames = pageMap["audioResNames"] as? List<String> ?: emptyList(),
                    // cloudAudio: [{ audioUrl, storageKey, text, wordStarts? } | null] written by basaya-web
                    audioUrls = (pageMap["cloudAudio"] as? List<*>).orEmpty().map { entry ->
                        ((entry as? Map<*, *>)?.get("audioUrl") as? String)
                            ?.takeIf { it.startsWith("https://") }
                            .orEmpty()
                    },
                    wordStarts = (pageMap["cloudAudio"] as? List<*>).orEmpty().map { entry ->
                        val starts = ((entry as? Map<*, *>)?.get("wordStarts") as? List<*>).orEmpty()
                        starts.mapNotNull { (it as? Number)?.toDouble() }.takeIf { it.size == starts.size }.orEmpty()
                    }
                )
            }

            Lecture(id = lessonId, title = title, pages = pages)
        } catch (e: Exception) {
            null // Offline and not downloaded
        }
    }

    /**
     * Saves without waiting: offline, Firestore keeps the write on the phone and sends it
     * once the phone is back online, so the lecture can close right away.
     */
    fun markLectureFinished(userId: String, lessonId: String) {
        if (PracticeLessons.isPractice(lessonId)) return // Practice: nothing is saved
        firestore
            .collection("users")
            .document(userId)
            .collection("assignedLessons")
            .document(lessonId)
            .set(mapOf("lectureFinished" to true), SetOptions.merge())
            .addOnFailureListener { Log.w("LectureRepository", "Could not save lectureFinished", it) }
    }

    suspend fun checkCompletion(userId: String, lessonId: String): Boolean {
        // A Practice lesson saves nothing, so its quiz never waits for the lecture.
        if (PracticeLessons.isPractice(lessonId)) return true
        val doc = firestore
            .collection("users")
            .document(userId)
            .collection("assignedLessons")
            .document(lessonId)
            .get()
            .await()

        return doc.getBoolean("lectureFinished") ?: false
    }
}