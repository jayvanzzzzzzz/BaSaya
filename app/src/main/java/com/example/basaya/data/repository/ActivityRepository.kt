package com.example.basaya.data.repository

import android.content.Context
import android.util.Log
import com.example.basaya.data.database.AppDatabase
import com.example.basaya.data.entity.ActivityEntity
import com.example.basaya.data.model.ActivityPage
import com.example.basaya.data.model.PracticeActivityData
import com.example.basaya.data.progress.PracticeLessons
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

    /**
     * Room-first, Firestore-fallback for DISPLAY purposes only.
     * The fallback does NOT write to Room — only downloadActivity()
     * (called from the Download button flow) is allowed to persist locally.
     */
    suspend fun getActivity(lessonId: String): PracticeActivityData? {
        val isDownloaded = db.lessonDao().isLessonDownloaded(lessonId) ?: false

        if (isDownloaded) {
            // When online, refresh the downloaded copy so answer fixes made by the teacher in
            // basaya-web reach the student. Offline, the saved copy is used as before.
            val fresh = fetchFromFirestore(lessonId)
            if (fresh != null) {
                db.activityDao().insert(fresh.toEntity(lessonId))
                return fresh
            }
            return db.activityDao().getActivity(lessonId)?.toPracticeActivityData()
        }

        return fetchFromFirestore(lessonId)
    }

    /** Fetches + caches to Room. Called from the Download button flow. */
    suspend fun downloadActivity(lessonId: String): Boolean {
        val fetched = fetchFromFirestore(lessonId) ?: return false
        db.activityDao().insert(fetched.toEntity(lessonId))
        return true
    }

    private suspend fun fetchFromFirestore(lessonId: String): PracticeActivityData? {
        return try {
            val snapshot = firestore
                .collection("lessons")
                .document(PracticeLessons.contentId(lessonId))
                .collection("activity")
                .get(Source.SERVER)
                .await()

            if (snapshot.isEmpty) return null

            // basaya-web saves the activity as lessons/{id}/activity/content; older lessons may
            // have another document, so prefer "content" and fall back to the first one.
            val doc = snapshot.documents.firstOrNull { it.id == "content" } ?: snapshot.documents.first()
            val title = doc.getString("title") ?: ""
            val instruction = doc.getString("instruction") ?: ""
            val explanation = doc.getString("explanation") ?: ""

            val pagesRaw = (doc.get("pages") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()

            val pages = pagesRaw.mapIndexed { index, p ->
                val words = (p["words"] as? List<*>).orEmpty().filterIsInstance<String>()

                // Numbers may arrive as Long or Double depending on how they were written.
                val correctIndices = (p["correctIndices"] as? List<*>).orEmpty()
                    .mapNotNull { (it as? Number)?.toInt() }
                    .filter { it in words.indices }
                    .distinct()

                if (correctIndices.isEmpty()) {
                    Log.w(TAG, "Lesson $lessonId activity page ${index + 1} has no correct answers set in basaya-web")
                }

                ActivityPage(
                    words = words,
                    correctIndices = correctIndices,
                    explanation = (p["explanation"] as? String)?.takeIf { it.isNotBlank() }
                )
            }

            PracticeActivityData(
                id = doc.id,
                title = title,
                instruction = instruction,
                explanation = explanation,
                pages = pages
            )
        } catch (e: Exception) {
            null // Offline and not downloaded
        }
    }

    /**
     * Saves without waiting: offline, Firestore keeps the write on the phone and sends it
     * once the phone is back online, so the score screen shows right away.
     */
    fun markActivityFinished(userId: String, lessonId: String, score: Int, total: Int) {
        if (PracticeLessons.isPractice(lessonId)) return // Practice: nothing is saved
        firestore
            .collection("users")
            .document(userId)
            .collection("assignedLessons")
            .document(lessonId)
            .set(
                mapOf(
                    "activityFinished" to true,
                    "activityScore" to score,
                    "activityTotal" to total,
                    "activityCompletedAt" to FieldValue.serverTimestamp()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
            .addOnFailureListener { Log.w(TAG, "Could not save the activity result", it) }
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