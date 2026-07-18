package com.example.basaya.data.repository

import android.content.Context
import com.example.basaya.data.database.AppDatabase
import com.example.basaya.data.entity.LectureEntity
import com.example.basaya.model.Lecture
import com.example.basaya.model.LecturePage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.tasks.await

class LectureRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val firestore = FirebaseFirestore.getInstance()
    private val gson = Gson()

    suspend fun getLecture(lessonId: String): Lecture? {
        db.lectureDao().getLecture(lessonId)?.let { cached ->
            val pagesType = object : TypeToken<List<LecturePage>>() {}.type
            return Lecture(
                id = lessonId,
                title = cached.title,
                pages = gson.fromJson(cached.pagesJson, pagesType)
            )
        }
        return fetchAndCache(lessonId)
    }

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
        val snapshot = firestore
            .collection("lessons")
            .document(lessonId)
            .collection("lectures")   // 👈 matches your actual collection name
            .get()
            .await()

        if (snapshot.isEmpty) return null

        val doc = snapshot.documents.first()
        val title = doc.getString("title") ?: ""

        @Suppress("UNCHECKED_CAST")
        val pagesRaw = doc.get("pages") as? List<Map<String, Any>> ?: emptyList()

        val pages = pagesRaw.map { pageMap ->
            val pageNumber = (pageMap["pageNumber"] as? Long)?.toInt() ?: 0

            @Suppress("UNCHECKED_CAST")
            val sentences = pageMap["sentences"] as? List<String> ?: emptyList()

            @Suppress("UNCHECKED_CAST")
            val audioResNames = pageMap["audioResNames"] as? List<String> ?: emptyList()

            LecturePage(
                pageNumber = pageNumber,
                sentences = sentences,
                audioResNames = audioResNames
            )
        }

        val lecture = Lecture(id = lessonId, title = title, pages = pages)

        db.lectureDao().insert(
            LectureEntity(
                lessonId = lessonId,
                title = title,
                pagesJson = gson.toJson(pages)
            )
        )

        return lecture
    }

    suspend fun markLectureFinished(userId: String, lessonId: String) {
        firestore
            .collection("users")
            .document(userId)
            .collection("assignedLessons")
            .document(lessonId)
            .update("lectureFinished", true)
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