package ph.basaya.app.data.repository

import ph.basaya.app.data.progress.ProgressSession

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

    /** Read from Room first, then Firestore for display only; only explicit downloads save lesson content to Room. */
    suspend fun getQuiz(lessonId: String): Quiz? {
        if (!PracticeLessons.refresh(context, lessonId)) return fetchFromFirestore(lessonId)
        LessonContentPrefetch.takeQuiz(lessonId)?.let { return it }

        val isDownloaded = db.lessonDao().isLessonDownloaded(lessonId) ?: false

        if (isDownloaded) {
            val saved = db.quizDao().getQuiz(lessonId)?.toQuiz()
            if (saved != null) {
                DownloadedLessonRefresh.launch(context, lessonId, "quiz:$lessonId") {
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
        if (fetched.questions.isEmpty()) return false
        db.quizDao().insert(fetched.toEntity(lessonId))
        return true
    }

    private suspend fun fetchFromFirestore(lessonId: String): Quiz? {
        val result = ph.basaya.app.data.assessment.AssessmentSession.open(context, lessonId).request("content").getAsJsonObject("quiz")
        return Quiz(id = lessonId, title = result.get("title").asString, questions = result.getAsJsonArray("questions").map { item ->
            val q = item.asJsonObject
            QuizQuestion(question = q.get("question").asString, choices = q.getAsJsonArray("choices").map { it.asString },
                multiple = q.get("multiple").asBoolean, requiresServerValidation = true)
        })
    }

    // Mapping helpers

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
