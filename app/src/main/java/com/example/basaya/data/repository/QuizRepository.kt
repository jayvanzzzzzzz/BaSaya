package com.example.basaya.data.repository

import android.content.Context
import com.example.basaya.data.database.AppDatabase
import com.example.basaya.data.entity.QuizEntity
import com.example.basaya.data.model.Quiz
import com.example.basaya.data.model.QuizQuestion
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.tasks.await

class QuizRepository(private val context: Context) {

    private val firestore = FirebaseFirestore.getInstance()
    private val gson = Gson()

    private val db by lazy { AppDatabase.getDatabase(context) }

    /**
     * Room-first read. If nothing is cached yet, fetches from Firestore
     * for DISPLAY ONLY — does not write to Room. Caching only happens
     * via downloadQuiz(), called from the Download button flow.
     */
    suspend fun getQuiz(lessonId: String): Quiz? {
        val cached = db.quizDao().getQuiz(lessonId)

        if (cached != null) {
            return cached.toQuiz()
        }

        return fetchFromFirestore(lessonId)
    }

    /**
     * Explicit download: fetches from Firestore and persists to Room.
     * Only called from the lesson Download button (LessonRepository.downloadLesson).
     */
    suspend fun downloadQuiz(lessonId: String): Boolean {
        val fetched = fetchFromFirestore(lessonId) ?: return false
        db.quizDao().insert(fetched.toEntity(lessonId))
        return true
    }

    private suspend fun fetchFromFirestore(lessonId: String): Quiz? {
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

        // id = lessonId (not doc.id) so it matches the Room primary key
        // used by getQuiz()/downloadQuiz() consistently.
        return Quiz(id = lessonId, title = title, questions = questions)
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

    // --- Mapping helpers ---

    private fun QuizEntity.toQuiz(): Quiz {
        val type = object : TypeToken<List<QuizQuestion>>() {}.type
        val questions: List<QuizQuestion> = gson.fromJson(questionsJson, type)
        return Quiz(id = lessonId, title = title, questions = questions)
    }

    private fun Quiz.toEntity(lessonId: String): QuizEntity {
        return QuizEntity(
            lessonId = lessonId,
            title = title,
            questionsJson = gson.toJson(questions)
        )
    }
}