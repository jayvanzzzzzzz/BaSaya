package ph.basaya.app.data.repository

import ph.basaya.app.data.progress.ProgressSession

import android.content.Context
import android.util.Log
import ph.basaya.app.data.audio.PronunciationAudioStore
import ph.basaya.app.data.database.AppDatabase
import ph.basaya.app.data.entity.PronunciationEntity
import ph.basaya.app.data.model.PronunciationActivityData
import ph.basaya.app.data.progress.PracticeLessons
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.tasks.await

class PronunciationRepository(private val context: Context) {

    private val db by lazy { AppDatabase.getDatabase(context) }
    private val firestore = FirebaseFirestore.getInstance()
    private val gson = Gson()

    /** Read from Room first, then Firestore for display only; only explicit downloads save lesson content to Room. */
    suspend fun getActivity(lessonId: String): PronunciationActivityData? {
        LessonContentPrefetch.takePronunciation(lessonId)?.let { return it }

        val isDownloaded = db.lessonDao().isLessonDownloaded(lessonId) ?: false

        if (isDownloaded) {
            val saved = db.pronunciationDao().getActivity(lessonId)?.toPronunciationActivityData()
            if (saved != null) {
                DownloadedLessonRefresh.launch("pronunciation:$lessonId") {
                    val fresh = fetchFromFirestore(lessonId) ?: return@launch
                    if (fresh.audioRefs != saved.audioRefs) {
                        try {
                            PronunciationAudioStore.download(context, lessonId, fresh.audioRefs)
                        } catch (e: Exception) {
                            Log.w("PronunciationRepo", "Could not update the word audio for $lessonId", e)
                            return@launch
                        }
                    }
                    db.pronunciationDao().insert(fresh.toEntity(lessonId))
                }
                return saved
            }
        }

        return fetchFromFirestore(lessonId)
    }

    /** Fetches + caches to Room. Called from the Download button flow. */
    suspend fun downloadPronunciation(
        lessonId: String,
        onAudioFile: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Boolean {
        val fetched = fetchFromFirestore(lessonId) ?: return false
        if (fetched.words.isEmpty()) return false
        PronunciationAudioStore.download(context, lessonId, fetched.audioRefs, onAudioFile)
        db.pronunciationDao().insert(fetched.toEntity(lessonId))
        return true
    }

    private suspend fun fetchFromFirestore(lessonId: String): PronunciationActivityData? {
        return try {
            val snapshot = firestore
                .collection("lessons")
                .document(PracticeLessons.contentId(lessonId))
                .collection("pronunciation")
                .get(Source.SERVER)
                .await()

            if (snapshot.isEmpty) return null

            val doc = snapshot.documents.first()
            val title = doc.getString("title") ?: ""
            val instruction = doc.getString("instruction") ?: ""

            @Suppress("UNCHECKED_CAST")
            val words = doc.get("words") as? List<String> ?: emptyList()

            @Suppress("UNCHECKED_CAST")
            val legacyRefs = doc.get("audioRefs") as? List<String> ?: emptyList()
            val cloudAudio = cloudAudioEntries(doc.get("cloudAudio"), doc.get("pages"))

            // Prefer the Cloudflare R2 URL for each word; keep the old ref (raw name/URL) otherwise.
            val audioRefs = words.mapIndexed { index, word ->
                cloudUrlFor(word, index, cloudAudio) ?: legacyRefs.getOrNull(index).orEmpty()
            }

            PronunciationActivityData(
                id = lessonId,
                title = title,
                instruction = instruction,
                words = words,
                audioRefs = audioRefs
            )
        } catch (e: Exception) {
            null // Offline and not downloaded
        }
    }

    // Cloudflare audio

    /**
     * basaya-web writes `cloudAudio: [{ audioUrl, storageKey, text } | null]`, either on the
     * document itself or per page (`pages[].cloudAudio`, like lectures). Null entries are
     * kept so list positions still line up with the words.
     */
    private fun cloudAudioEntries(docCloudAudio: Any?, pages: Any?): List<Map<*, *>?> {
        (docCloudAudio as? List<*>)?.let { list -> return list.map { it as? Map<*, *> } }
        return (pages as? List<*>).orEmpty().flatMap { page ->
            ((page as? Map<*, *>)?.get("cloudAudio") as? List<*>).orEmpty().map { it as? Map<*, *> }
        }
    }

    /** Match by the entry's text first (robust to reordering), then by position. */
    private fun cloudUrlFor(word: String, index: Int, entries: List<Map<*, *>?>): String? {
        val byText = entries.firstOrNull { entry ->
            (entry?.get("text") as? String)?.trim()?.equals(word.trim(), ignoreCase = true) == true
        }
        val entry = byText ?: entries.getOrNull(index)
        return (entry?.get("audioUrl") as? String)?.takeIf { it.startsWith("https://") }
    }

    // Mapping helpers

    private fun PronunciationEntity.toPronunciationActivityData(): PronunciationActivityData {
        val listType = object : TypeToken<List<String>>() {}.type
        return PronunciationActivityData(
            id = lessonId,
            title = title,
            instruction = instruction,
            words = gson.fromJson(wordsJson, listType),
            audioRefs = gson.fromJson(audioRefsJson, listType)
        )
    }

    private fun PronunciationActivityData.toEntity(lessonId: String): PronunciationEntity {
        return PronunciationEntity(
            lessonId = lessonId,
            title = title,
            instruction = instruction,
            wordsJson = gson.toJson(words),
            audioRefsJson = gson.toJson(audioRefs)
        )
    }
}
