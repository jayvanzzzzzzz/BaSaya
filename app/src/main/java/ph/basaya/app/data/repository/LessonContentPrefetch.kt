package ph.basaya.app.data.repository

import ph.basaya.app.data.dictionary.Dictionary
import ph.basaya.app.data.model.CrosswordGameLevel
import ph.basaya.app.data.model.Lecture
import ph.basaya.app.data.model.PracticeActivityData
import ph.basaya.app.data.model.PronunciationActivityData
import ph.basaya.app.data.model.Quiz
import java.util.concurrent.ConcurrentHashMap

object LessonContentPrefetch {

    private val lectures = ConcurrentHashMap<String, Lecture>()
    private val games = ConcurrentHashMap<String, List<CrosswordGameLevel>>()
    private val activities = ConcurrentHashMap<String, PracticeActivityData>()
    private val quizzes = ConcurrentHashMap<String, Quiz>()
    private val pronunciations = ConcurrentHashMap<String, PronunciationActivityData>()
    private val pronunciationEntries = ConcurrentHashMap<String, List<Dictionary.Entry?>>()

    fun store(lessonId: String, content: Lecture) {
        lectures[lessonId] = content
    }

    fun takeLecture(lessonId: String): Lecture? = lectures.remove(lessonId)

    fun storeGame(lessonId: String, content: List<CrosswordGameLevel>) {
        games[lessonId] = content
    }

    fun takeGame(lessonId: String): List<CrosswordGameLevel>? = games.remove(lessonId)

    fun storeActivity(lessonId: String, content: PracticeActivityData) {
        activities[lessonId] = content
    }

    fun takeActivity(lessonId: String): PracticeActivityData? = activities.remove(lessonId)

    fun storeQuiz(lessonId: String, content: Quiz) {
        quizzes[lessonId] = content
    }

    fun takeQuiz(lessonId: String): Quiz? = quizzes.remove(lessonId)

    fun storePronunciation(
        lessonId: String,
        content: PronunciationActivityData,
        entries: List<Dictionary.Entry?>
    ) {
        pronunciations[lessonId] = content
        pronunciationEntries[lessonId] = entries
    }

    fun takePronunciation(lessonId: String): PronunciationActivityData? = pronunciations.remove(lessonId)

    fun takePronunciationEntries(lessonId: String): List<Dictionary.Entry?>? =
        pronunciationEntries.remove(lessonId)
}
