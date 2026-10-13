package com.example.basaya.data.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.basaya.data.schedule.TrustedClock
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The scheduler's alarm went off, or the phone restarted (alarms don't survive a restart). */
class NotificationAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Online: confirm the real time first, in case the phone's date was changed.
                FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                    TrustedClock.sync(context, uid, timeoutMs = 5_000L)
                }
                NotificationScheduler.run(context)
            } finally {
                pending.finish()
            }
        }
    }
}
