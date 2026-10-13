package com.example.basaya.data.notifications

import android.content.Context
import com.example.basaya.data.schedule.TrustedClock
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

/**
 * "Read up to" time per student. Everything that appeared after it is unread.
 * Kept on the phone (works offline) and in users/{uid}.notifReadAt (follows the account).
 */
object NotificationReadState {

    private const val PREFS = "notification_read"

    fun readAt(context: Context, uid: String): Long = prefs(context).getLong(key(uid), 0L)

    /** Takes the account's value from Firestore when it is newer (e.g. read on another phone). */
    fun mergeRemote(context: Context, uid: String, remoteMs: Long?) {
        if (remoteMs != null && remoteMs > readAt(context, uid)) save(context, uid, remoteMs)
    }

    fun markAllRead(context: Context, uid: String) {
        // Trusted time, not the phone's, so a changed date can't hide future notifications.
        val now = maxOf(TrustedClock.bestGuess(context), readAt(context, uid))
        save(context, uid, now)
        // Not awaited: offline, Firestore sends it once the phone is back online.
        FirebaseFirestore.getInstance().collection("users").document(uid)
            .update("notifReadAt", Timestamp(Date(now)))
        LessonInbox.notifyChanged()
    }

    private fun save(context: Context, uid: String, value: Long) {
        prefs(context).edit().putLong(key(uid), value).apply()
    }

    private fun key(uid: String) = "readAt_$uid"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
