package ph.basaya.app.data.repository

import android.content.Context
import android.util.Log
import ph.basaya.app.data.database.AppDatabase
import ph.basaya.app.data.entity.QuizEntity
import ph.basaya.app.data.model.Quiz
import ph.basaya.app.data.model.QuizQuestion
import ph.basaya.app.data.progress.PracticeLessons
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.tasks.await

class QuizRepository(private val context: Context) {

    private val db by lazy { AppDatabase.getDatabase(context) }
    private val firestore = FirebaseFirestore.getInstance()
    private val gson = Gson()

    /**
     * Room-first, Firestore-fallback for DISPLAY purposes only.
     * The fallback does NOT write to Room — only downloadQuiz()
     * (called from the Download button flow) is allowed to persist locally.
     */
    suspend fun getQuiz(lessonId: String): Quiz? {
        LessonContentPrefetch.takeQuiz(lessonId)?.let { return it }

        val isDownloaded = db.lessonDao().isLessonDownloaded(lessonId) ?: false

        if (isDownloaded) {
            val saved = db.quizDao().getQuiz(lessonId)?.toQuiz()
            if (saved != null) {
                DownloadedLessonRefresh.launch("quiz:$lessonId") {
                    fetchFromFirestore(lessonId)?.let { db.quizDao().insert(it.toEntity(lessonId)) }
                }
                return saved
            }
        }

        return fetchFromFirestore(lessonId)
    }

    /** Fetches + caches to Room. Called from the Download button flow. */
    suspend fun downloadQuiz(lessonId: String): Boolean {
        val fetched = fetchFromFirestore(lessonId) ?: return false
        db.quizDao().insert(fetched.toEntity(lessonId))
        return true
    }

    private suspend fun fetchFromFirestore(lessonId: String): Quiz? {
        return try {
            val snapshot = firestore
                .collection("lessons")
                .document(PracticeLessons.contentId(lessonId))
                .collection("quiz")
                .get(Source.SERVER)
                .await()

            if (snapshot.isEmpty) return null

            val doc = snapshot.documents.first()
            val title = doc.getString("title") ?: ""

            @Suppress("UNCHECKED_CAST")
            val questionsRaw = doc.get("questions") as? List<Map<String, Any>> ?: emptyList()

            val questions = questionsRaw.map { q ->
                @Suppress("UNCHECKED_CAST")
                val choices = q["choices"] as? List<String> ?: emptyList()

                // Numbers may arrive as Long or Double depending on how they were written.
                val answers = (q["answers"] as? List<*>).orEmpty()
                    .mapNotNull { (it as? Number)?.toInt() }
                    .filter { it in choices.indices }
                    .distinct()

                QuizQuestion(
                    question = q["question"] as? String ?: "",
                    choices = choices,
                    answer = (q["answer"] as? Number)?.toInt() ?: 0,
                    answers = answers.takeIf { it.isNotEmpty() },
                    multiple = q["multiple"] as? Boolean
                )
            }

            // id = lessonId (not doc.id) so it matches the Room primary key
            // used by getQuiz()/downloadQuiz() consistently.
            Quiz(id = lessonId, title = title, questions = questions)
        } catch (e: Exception) {
            null // Offline and not downloaded
        }
    }

    /**
     * Saves without waiting: offline, Firestore keeps the write on the phone and sends it
     * once the phone is back online, so the score screen shows right away.
     */
    fun saveQuizResult(userId: String, lessonId: String, score: Int, total: Int) {
        if (PracticeLessons.isPractice(lessonId)) return // Practice: nothing is saved
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
            .addOnFailureListener { Log.w("QuizRepository", "Could not save the quiz result", it) }
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