package ph.basaya.app.ui.quiz

import com.google.gson.Gson
import ph.basaya.app.data.model.Quiz
import java.security.MessageDigest

/** Display state only. Accepted graded answers always come from the server. */
internal data class QuizRecreationState(
    val lessonId: String,
    val owner: String,
    val classId: String,
    val session: String,
    val attemptId: String,
    val contentHash: String,
    val positions: List<Int>,
    val choiceOrders: List<List<Int>>,
    val currentPosition: Int?,
    val selected: Set<Int>,
    val completed: Set<Int>,
    val practiceScore: Int
) {
    data class Restored(
        val positions: List<Int>,
        val choiceOrders: List<List<Int>>,
        val selected: Set<Int>,
        val completed: Set<Int>,
        val practiceScore: Int
    )

    fun restore(quiz: Quiz, lessonId: String, owner: String, classId: String,
                session: String, attemptId: String, serverAnswered: Set<Int>, practice: Boolean): Restored? {
        if (this.lessonId != lessonId || this.owner != owner || this.classId != classId ||
            this.session != session || this.attemptId != attemptId || contentHash != fingerprint(quiz)) return null
        if (positions.size != choiceOrders.size || positions.distinct().size != positions.size ||
            positions.any { it !in quiz.questions.indices } || completed.any { it !in quiz.questions.indices }) return null
        for (i in positions.indices) {
            val choices = quiz.questions[positions[i]].choices.indices.toList()
            if (choiceOrders[i].sorted() != choices) return null
        }
        val accepted = if (practice) completed else serverAnswered
        val remaining = positions.indices.filter { positions[it] !in accepted }
        if (remaining.map { positions[it] }.toSet() != quiz.questions.indices.filter { it !in accepted }.toSet()) return null
        val first = remaining.firstOrNull()
        val selection = if (first != null && positions[first] == currentPosition)
            selected.filter { it in choiceOrders[first].indices }.toSet() else emptySet()
        return Restored(remaining.map { positions[it] }, remaining.map { choiceOrders[it] },
            selection, accepted, if (practice) practiceScore.coerceIn(0, accepted.size) else 0)
    }

    companion object {
        fun fingerprint(quiz: Quiz): String = MessageDigest.getInstance("SHA-256")
            .digest(Gson().toJson(quiz).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
