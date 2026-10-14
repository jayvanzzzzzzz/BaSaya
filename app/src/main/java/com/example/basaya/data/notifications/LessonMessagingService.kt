package com.example.basaya.data.notifications

import com.example.basaya.data.chat.ClassChat
import com.example.basaya.data.database.AppDatabase
import com.example.basaya.data.entity.ClassNotificationEntity
import com.example.basaya.data.repository.LessonRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

class LessonMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        ClassNotifications.resubscribe(this)
    }

    /**
     * "New lesson" or "deadline extended" data message from notify-api. Saved to the
     * notification list, then the scheduler shows it now, or at the lesson's open date
     * (visibleAt) if that's later. Runs on a background thread, in the foreground and
     * background alike.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["type"] == "chat_message") return showChat(data)
        if (data["type"] != "new_lesson" && data["type"] != "deadline_extended") return
        val id = data["notificationId"] ?: return
        val classId = data["classId"] ?: return
        val visibleAt = data["visibleAt"]?.toLongOrNull() ?: return
        // Ignore messages for a class this phone no longer follows.
        if (classId != ClassNotifications.subscribedClass(this)) return

        val item = ClassNotificationEntity(
            id = id,
            classId = classId,
            title = data["title"].orEmpty(),
            body = data["body"].orEmpty(),
            lessonIds = data["lessonIds"].orEmpty(),
            visibleAt = visibleAt
        )
        val context = this
        runBlocking {
            withTimeoutOrNull(15_000L) {
                AppDatabase.getDatabase(context).classNotificationDao().upsertAll(listOf(item))
                // Fetch the new lesson or the new deadline (and the real time) first, so the
                // lesson and its deadline reminders are up to date.
                FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                    runCatching { LessonRepository(context).syncLessons(uid) }
                }
                NotificationScheduler.run(context, forcePostKey = "feed:$id")
            }
        }
        LessonInbox.notifyChanged()
    }

    /** The teacher wrote in the class chat (notify-chat). Skipped while the chat is on screen. */
    private fun showChat(data: Map<String, String>) {
        val classId = data["classId"] ?: return
        if (classId != ClassNotifications.subscribedClass(this)) return
        if (ClassChat.visibleClassId == classId) return
        ClassNotifications.showChat(
            this,
            classId,
            title = data["title"].orEmpty(),
            body = data["body"].orEmpty(),
            time = data["sentAt"]?.toLongOrNull() ?: System.currentTimeMillis()
        )
    }
}
