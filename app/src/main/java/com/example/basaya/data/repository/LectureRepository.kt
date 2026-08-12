package com.example.basaya.data.repository

import android.content.Context
import com.example.basaya.data.database.AppDatabase
import com.example.basaya.data.entity.LectureEntity
import com.example.basaya.data.model.Lecture
import com.example.basaya.data.model.LecturePage
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
     * The fallback does NOT write to Room — only downloadLecture()/syncLecture()
     * (called from the Download button flow) are allowed to persist locally.
     */
    suspend fun getLecture(lessonId: String): Lecture? {
        val isDownloaded = db.lessonDao().isLessonDownloaded(lessonId) ?: false

        if (isDownloaded) {
            db.lectureDao().getLecture(lessonId)?.let { cached ->
                val pagesType = object : TypeToken<List<LecturePage>>() {}.type
                return Lecture(
                    id = lessonId,
                    title = cached.title,
                    pages = gson.fromJson(cached.pagesJson, pagesType)
                )
            }
            return null
        }

        return fetchFromFirestore(lessonId)
    }

    /** Fetches + caches to Room. Called from the Download button flow. */
    suspend fun downloadLecture(lessonId: String): Lecture? {
        return fetchAndCache(lessonId)
    }

    /** Kept for any existing manual "refresh this lecture" call sites. */
    suspend fun syncLecture(lessonId: String): Lecture? {
        return fetchAndCache(lessonId)
    }

    suspend fun checkCompletion(lessonId: String): Boolean {
        val snapshot = firestore
            .collection("lessons")
            .document(lessonId)
            .collection("lectures")
            .get()
            .await()

        if (snapshot.isEmpty) return false

        val doc = snapshot.documents.first()
        return doc.getBoolean("finished") ?: false
    }

    private suspend fun fetchAndCache(lessonId: String): Lecture? {
        val lecture = fetchFromFirestore(lessonId) ?: return null

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
                .document(lessonId)
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
                    audioResNames = pageMap["audioResNames"] as? List<String> ?: emptyList()
                )
            }

            Lecture(id = lessonId, title = title, pages = pages)
        } catch (e: Exception) {
            null // Offline and not downloaded
        }
    }

    suspend fun markLectureFinished(userId: String, lessonId: String) {
        firestore
            .collection("users")
            .document(userId)
            .collection("assignedLessons")
            .document(lessonId)
            .set(mapOf("lectureFinished" to true), SetOptions.merge())
            .await()
    }

    suspend fun checkCompletion(userId: String, lessonId: String): Boolean {
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