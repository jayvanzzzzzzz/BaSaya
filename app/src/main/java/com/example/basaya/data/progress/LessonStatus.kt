package com.example.basaya.data.progress

import com.example.basaya.data.entity.LessonEntity

/**
 * Tapos na / Nasa progreso / Hindi pa nasisimulan for a lesson card and the Home status chips
 * (basaya-web's statusOf in student/views/home.js). A part the teacher skipped never counts, and
 * a part left halfway already makes the lesson Nasa progreso.
 */
enum class LessonStatus {
    DONE, IN_PROGRESS, NOT_STARTED;

    companion object {
        fun of(lesson: LessonEntity): LessonStatus {
            val game = lesson.gameFinished && !lesson.gameSkipped
            val activity = lesson.activityFinished && !lesson.activitySkipped
            val complete = lesson.lectureFinished &&
                (lesson.gameFinished || lesson.gameSkipped) &&
                (lesson.activityFinished || lesson.activitySkipped) &&
                lesson.quizFinished
            return when {
                complete -> DONE
                lesson.lectureFinished || game || activity || lesson.quizFinished || lesson.started -> IN_PROGRESS
                else -> NOT_STARTED
            }
        }
    }
}
