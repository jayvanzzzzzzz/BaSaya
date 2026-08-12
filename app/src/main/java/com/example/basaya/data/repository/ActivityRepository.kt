package com.example.basaya.data.repository

import android.content.Context
import com.example.basaya.data.database.AppDatabase
import com.example.basaya.data.entity.ActivityEntity
import com.example.basaya.data.model.ActivityPage
import com.example.basaya.data.model.PracticeActivityData
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.tasks.await

class ActivityRepository(private val context: Context) {

    private val firestore = FirebaseFirestore.getInstance()
    private val gson = Gson()

    private val db by lazy { AppDatabase.getDatabase(context) }

    /**
     * Room-first read. If nothing is cached yet, fetches from Firestore
     * for DISPLAY ONLY — does not write to Room. Caching only happens
     * via downloadActivity(), called from the Download button flow.
     */
    suspend fun getActivity(lessonId: String): PracticeActivityData? {
        val cached = db.activityDao().getActivity(lessonId)

        if (cached != null) {
            return cached.toPracticeActivityData()
        }

        return fetchFromFirestore(lessonId)
    }

    /**
     * Explicit download: fetches from Firestore and persists to Room.
     * Only called from the lesson Download button (LessonRepository.downloadLesson).
     */
    suspend fun downloadActivity(lessonId: String): Boolean {
        val fetched = fetchFromFirestore(lessonId) ?: return false
        db.activityDao().insert(fetched.toEntity(lessonId))
        return true
    }

    private suspend fun fetchFromFirestore(lessonId: String): PracticeActivityData? {
        val snapshot = firestore
            .collection("lessons")
            .document(lessonId)
            .collection("activity")
            .get()
            .await()

        if (snapshot.isEmpty) return null

        val doc = snapshot.documents.first()
        val title = doc.getString("title") ?: ""
        val instruction = doc.getString("instruction") ?: ""
        val explanation = doc.getString("explanation") ?: ""

        @Suppress("UNCHECKED_CAST")
        val pagesRaw = doc.get("pages") as? List<Map<String, Any>> ?: emptyList()

        val pages = pagesRaw.map { p ->
            @Suppress("UNCHECKED_CAST")
            val words = p["words"] as? List<String> ?: emptyList()

            @Suppress("UNCHECKED_CAST")
            val correctIndices = (p["correctIndices"] as? List<Long>)?.map { it.toInt() } ?: emptyList()

            ActivityPage(
                words = words,
                correctIndices = correctIndices
            )
        }

        return PracticeActivityData(
            id = doc.id,
            title = title,
            instruction = instruction,
            explanation = explanation,
            pages = pages
        )
    }

    suspend fun markActivityFinished(userId: String, lessonId: String, score: Int, total: Int) {
        firestore
            .collection("users")
            .document(userId)
            .collection("assignedLessons")
            .document(lessonId)
            .update(
                mapOf(
                    "activityFinished" to true,
                    "activityScore" to score,
                    "activityTotal" to total,
                    "activityCompletedAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
    }

    // --- Mapping helpers ---

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