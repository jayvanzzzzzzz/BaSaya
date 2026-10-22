package com.example.basaya.data.notifications

import android.content.Context
import com.example.basaya.R
import com.example.basaya.data.database.AppDatabase
import com.example.basaya.data.entity.LessonEntity
import com.example.basaya.data.schedule.LessonSchedule
import com.example.basaya.data.schedule.TrustedClock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Everything in the notification list, built from Room so it works offline:
 * - "new lesson" items from classes/{classId}/notifications (synced into Room), shown once
 *   the lesson opens (visibleAt);
 * - deadline reminders made on the phone from each lesson's dueAt: 1 day before, 1 hour
 *   before, and when it closes.
 */
object LessonInbox {

    enum class Kind { NEW_LESSON, DEADLINE_DAY, DEADLINE_HOUR, CLOSED }

    data class Item(
        val key: String,
        val kind: Kind,
        val title: String,
        val body: String,
        /** When it appears (server time, epoch millis). */
        val time: Long,
        val lessonIds: List<String>
    )

    private const val HOUR = 60 * 60 * 1000L
    private const val DAY = 24 * HOUR

    private val changesFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when the list or the unread count may have changed. */
    val changes: SharedFlow<Unit> = changesFlow

    fun notifyChanged() {
        changesFlow.tryEmit(Unit)
    }

    /** Every item, past and future, newest first. */
    suspend fun allEvents(context: Context, userId: String): List<Item> {
        val db = AppDatabase.getDatabase(context)
        val lessons = db.lessonDao().getLessonsForUser(userId)
        val classId = lessons.firstOrNull()?.classId ?: return emptyList()
        val lessonIds = lessons.map { it.id }.toSet()

        // Only lessons still in the class.
        val newLessons = db.classNotificationDao().getForClass(classId).mapNotNull { n ->
            val ids = n.lessonIdList().filter { it in lessonIds }
            if (ids.isEmpty()) null
            else Item("feed:${n.id}", Kind.NEW_LESSON, n.title, n.body, n.visibleAt, ids)
        }
        val deadlines = lessons.flatMap { deadlineEvents(context, it) }
        return (newLessons + deadlines).sortedByDescending { it.time }
    }

    /** Items that have appeared by now, newest first. */
    suspend fun visible(context: Context, userId: String): List<Item> {
        val now = TrustedClock.bestGuess(context)
        return allEvents(context, userId).filter { it.time <= now }
    }

    suspend fun unreadCount(context: Context, userId: String): Int {
        val readAt = NotificationReadState.readAt(context, userId)
        return visible(context, userId).count { it.time > readAt }
    }

    private fun deadlineEvents(context: Context, lesson: LessonEntity): List<Item> {
        val due = lesson.dueAt ?: return emptyList()
        // A Practice lesson has nothing to finish, so no "due soon" reminders.
        if (lesson.practice || LessonSchedule.isComplete(lesson)) return emptyList()

        val deadline = LessonSchedule.format(due)
        val reminderBody = context.getString(R.string.notif_due_body, lesson.title, deadline)
        val ids = listOf(lesson.id)
        // No reminders from before the lesson was added or opened.
        val earliest = maxOf(lesson.assignedAt ?: 0L, lesson.openAt ?: 0L)

        return listOf(
            Item(
                "due-day:${lesson.id}:$due", Kind.DEADLINE_DAY,
                context.getString(R.string.notif_due_day_title), reminderBody, due - DAY, ids
            ),
            Item(
                "due-hour:${lesson.id}:$due", Kind.DEADLINE_HOUR,
                context.getString(R.string.notif_due_hour_title), reminderBody, due - HOUR, ids
            ),
            Item(
                "closed:${lesson.id}:$due", Kind.CLOSED,
                context.getString(R.string.notif_closed_title),
                context.getString(R.string.notif_closed_body, lesson.title, deadline), due, ids
            )
        ).filter { it.time >= earliest }
    }
}
