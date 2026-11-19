package ph.basaya.app.data.repository

import ph.basaya.app.data.progress.ProgressSession

import android.content.Context
import android.util.Log
import ph.basaya.app.data.audio.PronunciationAudioStore
import ph.basaya.app.data.database.AppDatabase
import ph.basaya.app.data.entity.PronunciationEntity
import ph.basaya.app.data.model.PronunciationActivityData
import ph.basaya.app.data.model.PronunciationReadingPage
import ph.basaya.app.data.model.PronunciationReadingWord
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
                DownloadedLessonRefresh.launch(context, lessonId, "pronunciation:$lessonId") {
                    val fresh = fetchFromFirestore(lessonId) ?: return@launch
                    if (fresh.audioRefs != saved.audioRefs) {
                        try {
                            PronunciationAudioStore.download(context, lessonId, fresh.audioRefs)
                        } catch (e: Exception) {
                            Log.w("PronunciationRepo", "Could not update the word audio for $lessonId", e)
                            return@launch
                        }
                    }
                    saveDownloaded(lessonId, fresh)
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
        if (fetched.words.isEmpty() && fetched.pages.isEmpty()) return false
        PronunciationAudioStore.download(context, lessonId, fetched.audioRefs, onAudioFile)
        saveDownloaded(lessonId, fetched)
        return true
    }

    private suspend fun saveDownloaded(lessonId: String, content: PronunciationActivityData) {
        // Publish page metadata only after its matching audio has finished downloading.
        check(context.getSharedPreferences("pronunciation_reading_pages", Context.MODE_PRIVATE)
            .edit().putString(lessonId, gson.toJson(content.pages)).commit()) { "Could not save reading pages" }
        db.pronunciationDao().insert(content.toEntity(lessonId))
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

            val readingPages = readingPages(doc.get("pages"), words, audioRefs)
            PronunciationActivityData(
                id = lessonId,
                title = title,
                instruction = instruction,
                words = words,
                audioRefs = audioRefs + readingPages.map { it.audioUrl }.filter { it.isNotBlank() },
                pages = readingPages
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


    private fun readingPages(raw: Any?, words: List<String>, refs: List<String>): List<PronunciationReadingPage> {
        fun key(value: String) = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFC).lowercase(java.util.Locale.ROOT).replace('’', '\'')
        val token = Regex("[\\p{L}\\p{M}\\p{N}]+(?:[-'’][\\p{L}\\p{M}\\p{N}]+)*")
        return (raw as? List<*>).orEmpty().mapNotNull { item ->
            val page = item as? Map<*, *> ?: return@mapNotNull null
            val sentence = (page["sentence"] as? String)?.trim().orEmpty()
            if (sentence.isBlank()) return@mapNotNull null
            val selected = ((page["readyWords"] as? List<*>)?.filterIsInstance<String>() ?: words).map(::key).toSet()
            val ready = token.findAll(sentence).map { it.value }.distinctBy(::key).filter { key(it) in selected }.map { word ->
                val at = words.indexOfFirst { key(it) == key(word) }
                PronunciationReadingWord(word, refs.getOrNull(at).orEmpty().takeIf { it.startsWith("https://") }.orEmpty())
            }.toList()
            val audio = (page["cloudAudio"] as? Map<*, *>)?.get("audioUrl") as? String ?: ""
            PronunciationReadingPage(sentence, audio.takeIf { it.startsWith("https://") }.orEmpty(), ready)
        }
    }

    // Mapping helpers

    private fun PronunciationEntity.toPronunciationActivityData(): PronunciationActivityData {
        val listType = object : TypeToken<List<String>>() {}.type
        return PronunciationActivityData(
            id = lessonId,
            title = title,
            instruction = instruction,
            words = gson.fromJson(wordsJson, listType),
            audioRefs = gson.fromJson(audioRefsJson, listType),
            pages = runCatching { gson.fromJson<List<PronunciationReadingPage>>(context.getSharedPreferences("pronunciation_reading_pages", Context.MODE_PRIVATE).getString(lessonId, "[]"), object : TypeToken<List<PronunciationReadingPage>>() {}.type) }.getOrDefault(emptyList())
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
