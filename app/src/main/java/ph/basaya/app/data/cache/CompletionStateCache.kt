package ph.basaya.app.data.cache

import ph.basaya.app.data.progress.ProgressSession

import android.content.Context

class CompletionStateCache(context: Context) {

    private val prefs = context.getSharedPreferences("completion_state_cache", Context.MODE_PRIVATE)

    data class State(
        val lectureFinished: Boolean,
        val gameFinished: Boolean,
        val gameScore: Long,
        val gameTotal: Long,
        val activityFinished: Boolean,
        val activityScore: Long?,
        val activityTotal: Long?,
        val quizFinished: Boolean,
        val quizScore: Long?,
        val quizTotal: Long?,
        val pronunciationFinished: Boolean,
        val pronunciationScore: Long?,
        val pronunciationTotal: Long?
    )

    fun save(lessonId: String, state: State) {
        val scopedLesson = ProgressSession.gameKey(lessonId)
        prefs.edit()
            .putBoolean("${scopedLesson}_lectureFinished", state.lectureFinished)
            .putBoolean("${scopedLesson}_gameFinished", state.gameFinished)
            .putLong("${scopedLesson}_gameScore", state.gameScore)
            .putLong("${scopedLesson}_gameTotal", state.gameTotal)
            .putBoolean("${scopedLesson}_activityFinished", state.activityFinished)
            .putLong("${scopedLesson}_activityScore", state.activityScore ?: -1)
            .putLong("${scopedLesson}_activityTotal", state.activityTotal ?: -1)
            .putBoolean("${scopedLesson}_quizFinished", state.quizFinished)
            .putLong("${scopedLesson}_quizScore", state.quizScore ?: -1)
            .putLong("${scopedLesson}_quizTotal", state.quizTotal ?: -1)
            .putBoolean("${scopedLesson}_pronunciationFinished", state.pronunciationFinished)
            .putLong("${scopedLesson}_pronunciationScore", state.pronunciationScore ?: -1)
            .putLong("${scopedLesson}_pronunciationTotal", state.pronunciationTotal ?: -1)
            .apply()
    }

    fun get(lessonId: String): State? {
        val scopedLesson = ProgressSession.gameKey(lessonId)
        if (!prefs.contains("${scopedLesson}_lectureFinished")) return null

        val activityScore = prefs.getLong("${scopedLesson}_activityScore", -1).takeIf { it >= 0 }
        val activityTotal = prefs.getLong("${scopedLesson}_activityTotal", -1).takeIf { it >= 0 }
        val quizScore = prefs.getLong("${scopedLesson}_quizScore", -1).takeIf { it >= 0 }
        val quizTotal = prefs.getLong("${scopedLesson}_quizTotal", -1).takeIf { it >= 0 }
        val pronunciationScore = prefs.getLong("${scopedLesson}_pronunciationScore", -1).takeIf { it >= 0 }
        val pronunciationTotal = prefs.getLong("${scopedLesson}_pronunciationTotal", -1).takeIf { it >= 0 }

        return State(
            lectureFinished = prefs.getBoolean("${scopedLesson}_lectureFinished", false),
            gameFinished = prefs.getBoolean("${scopedLesson}_gameFinished", false),
            gameScore = prefs.getLong("${scopedLesson}_gameScore", 0),
            gameTotal = prefs.getLong("${scopedLesson}_gameTotal", 0),
            activityFinished = prefs.getBoolean("${scopedLesson}_activityFinished", false),
            activityScore = activityScore,
            activityTotal = activityTotal,
            quizFinished = prefs.getBoolean("${scopedLesson}_quizFinished", false),
            quizScore = quizScore,
            quizTotal = quizTotal,
            pronunciationFinished = prefs.getBoolean("${scopedLesson}_pronunciationFinished", false),
            pronunciationScore = pronunciationScore,
            pronunciationTotal = pronunciationTotal
        )
    }
}