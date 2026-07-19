package com.example.basaya.data.repository

import android.content.Context
import com.example.basaya.model.ActivityPage
import com.example.basaya.model.PracticeActivityData
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class ActivityRepository(private val context: Context) {

    private val firestore = FirebaseFirestore.getInstance()

    suspend fun getActivity(lessonId: String): PracticeActivityData? {
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
        val explanation = doc.getString("explanation") ?: ""   // 👈 read at top level

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
}