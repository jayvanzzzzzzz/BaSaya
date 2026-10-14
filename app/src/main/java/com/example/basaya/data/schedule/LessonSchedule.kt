package com.example.basaya.data.schedule

import android.content.Context
import com.example.basaya.data.entity.LessonEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The teacher's optional open date and deadline (classes/{classId}/classLessons/{lessonId}),
 * checked against [TrustedClock] so changing the phone's date doesn't unlock anything.
 * Firestore rules check the same dates with server time when progress is saved.
 */
object LessonSchedule {

    enum class Availability {
        OPEN,
        /** Before the open date. */
        NOT_YET_OPEN,
        /** Has an open date, but the phone can't confirm the time (restarted while offline). */
        NEEDS_INTERNET,
        /** After the deadline. */
        CLOSED
    }

    fun availability(context: Context, lesson: LessonEntity): Availability {
        val now = TrustedClock.now(context)
        val known = now ?: TrustedClock.latestKnown(context)

        if (lesson.openAt != null && (known == null || known < lesson.openAt)) {
            return if (now != null) Availability.NOT_YET_OPEN else Availability.NEEDS_INTERNET
        }
        // Without an exact clock, the deadline counts only once it has surely passed;
        // the server still rejects late progress when it syncs.
        if (lesson.dueAt != null && known != null && known > lesson.dueAt) {
            return Availability.CLOSED
        }
        return Availability.OPEN
    }

    /** Every part is done (a game the teacher skipped doesn't count). */
    fun isComplete(lesson: LessonEntity): Boolean =
        lesson.lectureFinished &&
            (lesson.gameFinished || lesson.gameSkipped) &&
            lesson.activityFinished &&
            lesson.quizFinished

    /** Open lessons, and closed ones the student finished (review only). */
    fun canOpen(context: Context, lesson: LessonEntity): Boolean =
        when (availability(context, lesson)) {
            Availability.OPEN -> true
            Availability.CLOSED -> isComplete(lesson)
            else -> false
        }

    /**
     * Home list order: open lessons by nearest deadline, then open ones with no deadline,
     * then not-yet-open (soonest to open first), then closed (most recently closed first).
     * Same order as the web (student/views/home.js, teacher/views/classPage.js).
     */
    fun sortForList(context: Context, lessons: List<LessonEntity>): List<LessonEntity> {
        val ranks = lessons.associate {
            it.id to when (availability(context, it)) {
                Availability.OPEN -> 0
                Availability.CLOSED -> 2
                else -> 1
            }
        }
        return lessons.sortedWith(
            compareBy<LessonEntity> { ranks.getValue(it.id) }
                .thenBy {
                    when (ranks.getValue(it.id)) {
                        0 -> it.dueAt ?: Long.MAX_VALUE
                        1 -> it.openAt ?: Long.MAX_VALUE
                        else -> -(it.dueAt ?: 0L)
                    }
                }
                .thenBy { it.order }
        )
    }

    fun format(millis: Long?): String =
        if (millis == null) "" else formatter().format(Date(millis))

    private fun formatter() = SimpleDateFormat("MMM d, h:mm a", Locale.forLanguageTag("fil-PH"))
}
