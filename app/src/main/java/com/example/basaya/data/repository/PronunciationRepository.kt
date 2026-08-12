package com.example.basaya.data.repository

import android.content.Context
import com.example.basaya.data.model.PronunciationActivityData
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class PronunciationRepository(private val context: Context) {

    private val firestore = FirebaseFirestore.getInstance()

    suspend fun getActivity(lessonId: String): PronunciationActivityData? {
        val snapshot = firestore
            .collection("lessons")
            .document(lessonId)
            .collection("pronunciation")
            .get()
            .await()

        if (snapshot.isEmpty) return null

        val doc = snapshot.documents.first()
        val title = doc.getString("title") ?: ""
        val instruction = doc.getString("instruction") ?: ""

        @Suppress("UNCHECKED_CAST")
        val words = doc.get("words") as? List<String> ?: emptyList()

        @Suppress("UNCHECKED_CAST")
        val audioRefs = doc.get("audioRefs") as? List<String> ?: emptyList()

        return PronunciationActivityData(
            id = doc.id,
            title = title,
            instruction = instruction,
            words = words,
            audioRefs = audioRefs
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
                    "pronunciationFinished" to true,
                    "pronunciationScore" to score,
                    "pronunciationTotal" to total,
                    "pronunciationCompletedAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
    }
}