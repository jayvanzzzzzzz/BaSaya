package com.example.basaya.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await

class UserRepository {

    private val db = FirebaseFirestore.getInstance()

    // TODO: DEBUG ONLY — replace with a real assignment method before production
    suspend fun assignRandomLesson(userId: String) {
        val lessonsSnapshot = db.collection("lessons").get().await()
        val lessonIds = lessonsSnapshot.documents.map { it.id }

        if (lessonIds.isEmpty()) return

        val randomLessonId = lessonIds.random()

        val ref = db.collection("users")
            .document(userId)
            .collection("assignedLessons")
            .document(randomLessonId)

        val data = hashMapOf(
            "lessonId" to randomLessonId,
            "assignedAt" to FieldValue.serverTimestamp(),
            "unlocked" to true
        )

        ref.set(data).await()
    }
}