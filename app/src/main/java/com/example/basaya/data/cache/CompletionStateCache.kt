package com.example.basaya.data.cache

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
        prefs.edit()
            .putBoolean("${lessonId}_lectureFinished", state.lectureFinished)
            .putBoolean("${lessonId}_gameFinished", state.gameFinished)
            .putLong("${lessonId}_gameScore", state.gameScore)
            .putLong("${lessonId}_gameTotal", state.gameTotal)
            .putBoolean("${lessonId}_activityFinished", state.activityFinished)
            .putLong("${lessonId}_activityScore", state.activityScore ?: -1)
            .putLong("${lessonId}_activityTotal", state.activityTotal ?: -1)
            .putBoolean("${lessonId}_quizFinished", state.quizFinished)
            .putLong("${lessonId}_quizScore", state.quizScore ?: -1)
            .putLong("${lessonId}_quizTotal", state.quizTotal ?: -1)
            .putBoolean("${lessonId}_pronunciationFinished", state.pronunciationFinished)
            .putLong("${lessonId}_pronunciationScore", state.pronunciationScore ?: -1)
            .putLong("${lessonId}_pronunciationTotal", state.pronunciationTotal ?: -1)
            .apply()
    }

    fun get(lessonId: String): State? {
        if (!prefs.contains("${lessonId}_lectureFinished")) return null

        val activityScore = prefs.getLong("${lessonId}_activityScore", -1).takeIf { it >= 0 }
        val activityTotal = prefs.getLong("${lessonId}_activityTotal", -1).takeIf { it >= 0 }
        val quizScore = prefs.getLong("${lessonId}_quizScore", -1).takeIf { it >= 0 }
        val quizTotal = prefs.getLong("${lessonId}_quizTotal", -1).takeIf { it >= 0 }
        val pronunciationScore = prefs.getLong("${lessonId}_pronunciationScore", -1).takeIf { it >= 0 }
        val pronunciationTotal = prefs.getLong("${lessonId}_pronunciationTotal", -1).takeIf { it >= 0 }

        return State(
            lectureFinished = prefs.getBoolean("${lessonId}_lectureFinished", false),
            gameFinished = prefs.getBoolean("${lessonId}_gameFinished", false),
            gameScore = prefs.getLong("${lessonId}_gameScore", 0),
            gameTotal = prefs.getLong("${lessonId}_gameTotal", 0),
            activityFinished = prefs.getBoolean("${lessonId}_activityFinished", false),
            activityScore = activityScore,
            activityTotal = activityTotal,
            quizFinished = prefs.getBoolean("${lessonId}_quizFinished", false),
            quizScore = quizScore,
            quizTotal = quizTotal,
            pronunciationFinished = prefs.getBoolean("${lessonId}_pronunciationFinished", false),
            pronunciationScore = pronunciationScore,
            pronunciationTotal = pronunciationTotal
        )
    }
}