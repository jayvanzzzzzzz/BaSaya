package com.example.basaya.data.cache

import android.content.Context

class ContentCountsCache(context: Context) {

    private val prefs = context.getSharedPreferences("content_counts_cache", Context.MODE_PRIVATE)

    data class Counts(
        val lecturePages: Int,
        val gameLevels: Int,
        val activityPages: Int,
        val quizQuestions: Int,
        val pronunciationWordCount: Int
    )

    fun save(lessonId: String, counts: Counts) {
        prefs.edit()
            .putInt("${lessonId}_lecturePages", counts.lecturePages)
            .putInt("${lessonId}_gameLevels", counts.gameLevels)
            .putInt("${lessonId}_activityPages", counts.activityPages)
            .putInt("${lessonId}_quizQuestions", counts.quizQuestions)
            .putInt("${lessonId}_pronunciationWordCount", counts.pronunciationWordCount)
            .apply()
    }

    fun get(lessonId: String): Counts? {
        if (!prefs.contains("${lessonId}_lecturePages")) return null

        return Counts(
            lecturePages = prefs.getInt("${lessonId}_lecturePages", 0),
            gameLevels = prefs.getInt("${lessonId}_gameLevels", 0),
            activityPages = prefs.getInt("${lessonId}_activityPages", 0),
            quizQuestions = prefs.getInt("${lessonId}_quizQuestions", 0),
            pronunciationWordCount = prefs.getInt("${lessonId}_pronunciationWordCount", 0)
        )
    }
}