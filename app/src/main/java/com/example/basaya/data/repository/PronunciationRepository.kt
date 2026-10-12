package com.example.basaya.data.repository

import android.content.Context
import com.example.basaya.data.audio.PronunciationAudioStore
import com.example.basaya.data.database.AppDatabase
import com.example.basaya.data.entity.PronunciationEntity
import com.example.basaya.data.model.PronunciationActivityData
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

    /**
     * Room-first, Firestore-fallback for DISPLAY purposes only.
     * The fallback does NOT write to Room — only downloadPronunciation()
     * (called from the Download button flow) is allowed to persist locally.
     */
    suspend fun getActivity(lessonId: String): PronunciationActivityData? {
        val isDownloaded = db.lessonDao().isLessonDownloaded(lessonId) ?: false

        if (isDownloaded) {
            return db.pronunciationDao().getActivity(lessonId)?.toPronunciationActivityData()
        }

        return fetchFromFirestore(lessonId)
    }

    /** Fetches + caches to Room. Called from the Download button flow. */
    suspend fun downloadPronunciation(lessonId: String): Boolean {
        val fetched = fetchFromFirestore(lessonId) ?: return false
        db.pronunciationDao().insert(fetched.toEntity(lessonId))
        PronunciationAudioStore.download(context, lessonId, fetched.audioRefs)
        return true
    }

    private suspend fun fetchFromFirestore(lessonId: String): PronunciationActivityData? {
        return try {
            val snapshot = firestore
                .collection("lessons")
                .document(lessonId)
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

    suspend fun markActivityFinished(userId: String, lessonId: String, score: Int, total: Int) {
        firestore
            .collection("users")
            .document(userId)
            .collection("assignedLessons")
            .document(lessonId)
            .update(
                mapOf(
                    "pronunciationFinished" to true,
                    "pronunciationScore" to score,
                    "pronunciationTotal" to total,
                    "pronunciationCompletedAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
    }

    // --- Cloudflare audio ---

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

    // --- Mapping helpers ---

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