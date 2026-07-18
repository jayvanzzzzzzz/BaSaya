package com.example.basaya.data.repository

import android.content.Context
import com.example.basaya.model.Quiz
import com.example.basaya.model.QuizQuestion
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await

class QuizRepository(private val context: Context) {

    private val firestore = FirebaseFirestore.getInstance()

    suspend fun getQuiz(lessonId: String): Quiz? {
        val snapshot = firestore
            .collection("lessons")
            .document(lessonId)
            .collection("quiz")
            .get()
            .await()

        if (snapshot.isEmpty) return null

        val doc = snapshot.documents.first()
        val title = doc.getString("title") ?: ""

        @Suppress("UNCHECKED_CAST")
        val questionsRaw = doc.get("questions") as? List<Map<String, Any>> ?: emptyList()

        val questions = questionsRaw.map { q ->
            @Suppress("UNCHECKED_CAST")
            val choices = q["choices"] as? List<String> ?: emptyList()

            QuizQuestion(
                question = q["question"] as? String ?: "",
                choices = choices,
                answer = (q["answer"] as? Long)?.toInt() ?: 0
            )
        }

        return Quiz(id = doc.id, title = title, questions = questions)
    }

    suspend fun saveQuizResult(userId: String, lessonId: String, score: Int, total: Int) {
        firestore
            .collection("users")
            .document(userId)
            .collection("assignedLessons")
            .document(lessonId)
            .update(
                mapOf(
                    "quizFinished" to true,
                    "quizScore" to score,
                    "quizTotal" to total,
                    "quizCompletedAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
    }
}