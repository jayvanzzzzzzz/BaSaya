package ph.basaya.app.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ph.basaya.app.R
import ph.basaya.app.data.notifications.LessonInbox.Kind
import ph.basaya.app.ui.MainActivity
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Phone notifications. "New lesson" items come from the teacher's web app (notify-api on
 * Vercel → FCM topic "class_<classId>"); deadline reminders are made on the phone.
 */
object ClassNotifications {

    const val CHANNEL_NEW_LESSONS = "new_lessons"
    const val CHANNEL_DEADLINES = "lesson_deadlines"
    const val CHANNEL_CHAT = "class_chat"
    private const val PREFS = "class_notifications"
    private const val KEY_CLASS = "subscribedClassId"

    private fun topic(classId: String) = "class_$classId"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_NEW_LESSONS,
                context.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = context.getString(R.string.notif_channel_desc) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DEADLINES,
                context.getString(R.string.notif_channel_deadline_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = context.getString(R.string.notif_channel_deadline_desc) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CHAT,
                context.getString(R.string.ch_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = context.getString(R.string.ch_channel_desc) }
        )
    }

    /** The class this phone gets notifications for, or null. */
    fun subscribedClass(context: Context): String? = prefs(context).getString(KEY_CLASS, null)

    /**
     * Keeps this phone subscribed to [classId] only; null unsubscribes (left class / logged out).
     * FCM queues topic changes made offline and applies them once online.
     */
    fun syncTopic(context: Context, classId: String?) {
        val current = subscribedClass(context)
        val wanted = classId?.takeIf { it.isNotBlank() }
        if (current == wanted) return

        val messaging = FirebaseMessaging.getInstance()
        current?.let { messaging.unsubscribeFromTopic(topic(it)) }
        wanted?.let { messaging.subscribeToTopic(topic(it)) }
        prefs(context).edit().putString(KEY_CLASS, wanted).apply()
    }

    /** After FCM issues a new token, subscribe it to the class again. */
    fun resubscribe(context: Context) {
        subscribedClass(context)?.let {
            FirebaseMessaging.getInstance().subscribeToTopic(topic(it))
        }
    }

    /** Pops up [item]; tapping it opens Home and highlights its lesson(s). */
    fun show(context: Context, item: LessonInbox.Item) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val notificationId = item.key.hashCode()
        val openLesson = PendingIntent.getActivity(
            context,
            notificationId,
            MainActivity.highlightIntent(context, item.lessonIds),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val channel = if (item.kind == Kind.NEW_LESSON) CHANNEL_NEW_LESSONS else CHANNEL_DEADLINES
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_book)
            .setColor(ContextCompat.getColor(context, R.color.tb_end))
            .setContentTitle(item.title)
            .setContentText(item.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(
                if (item.kind == Kind.NEW_LESSON) NotificationCompat.CATEGORY_MESSAGE
                else NotificationCompat.CATEGORY_REMINDER
            )
            .setWhen(item.time)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(openLesson)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    /**
     * A teacher's chat message; tapping it opens the Chat tab. One notification per class,
     * so a new message replaces the previous one instead of piling up.
     */
    fun showChat(context: Context, classId: String, title: String, body: String, time: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val notificationId = "chat:$classId".hashCode()
        val openChat = PendingIntent.getActivity(
            context,
            notificationId,
            MainActivity.chatIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_CHAT)
            .setSmallIcon(R.drawable.ic_chat)
            .setColor(ContextCompat.getColor(context, R.color.nav_tab_chat))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setWhen(time)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(openChat)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
