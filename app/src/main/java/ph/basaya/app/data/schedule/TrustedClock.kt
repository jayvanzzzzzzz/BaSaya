package ph.basaya.app.data.schedule

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.provider.Settings
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A clock the student can't move by changing the phone's date and time.
 *
 * - Online, [sync] asks Firestore for the server time and remembers it together with
 *   SystemClock.elapsedRealtime() (time since the phone booted, which the date/time
 *   settings don't touch).
 * - Offline, [now] = last server time + time elapsed since then.
 * - After a restart the boot timer starts over, so [now] is null until the next sync.
 *   [latestKnown] still remembers the latest server-confirmed time (time never goes back).
 */
object TrustedClock {

    private const val PREFS = "trusted_clock"
    private const val KEY_SERVER = "anchorServerMs"
    private const val KEY_ELAPSED = "anchorElapsedMs"
    private const val KEY_BOOT = "anchorBootCount"
    private const val KEY_LATEST = "latestKnownMs"
    private const val SYNC_TIMEOUT_MS = 8_000L

    /** Reads the server time through users/{uid}.clockCheckAt. Returns false when offline. */
    suspend fun sync(context: Context, uid: String, timeoutMs: Long = SYNC_TIMEOUT_MS): Boolean {
        if (!isOnline(context)) return false
        val userRef = FirebaseFirestore.getInstance().collection("users").document(uid)
        return try {
            withTimeoutOrNull(timeoutMs) {
                userRef.update("clockCheckAt", FieldValue.serverTimestamp()).await()
                val ackElapsed = SystemClock.elapsedRealtime()
                val serverMs = userRef.get(Source.SERVER).await()
                    .getTimestamp("clockCheckAt")?.toDate()?.time
                    ?: return@withTimeoutOrNull false
                anchor(context, serverMs, ackElapsed)
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /** The real current time in millis, or null when it can't be trusted (never synced / rebooted). */
    fun now(context: Context): Long? {
        val prefs = prefs(context)
        if (!prefs.contains(KEY_SERVER)) return null

        val elapsedNow = SystemClock.elapsedRealtime()
        val anchorElapsed = prefs.getLong(KEY_ELAPSED, 0L)
        val boot = bootCount(context)
        val sameBoot = boot < 0 || boot == prefs.getInt(KEY_BOOT, -1)
        if (!sameBoot || elapsedNow < anchorElapsed) return null

        val now = prefs.getLong(KEY_SERVER, 0L) + (elapsedNow - anchorElapsed)
        if (now - prefs.getLong(KEY_LATEST, 0L) > 60_000L) {
            prefs.edit().putLong(KEY_LATEST, now).apply()
        }
        return now
    }

    /** The latest time the clock ever confirmed. The real time is at least this. */
    fun latestKnown(context: Context): Long? =
        prefs(context).getLong(KEY_LATEST, 0L).takeIf { it > 0L }

    /** Best guess for showing times: trusted time, else the latest confirmed one, else the phone's. */
    fun bestGuess(context: Context): Long =
        now(context) ?: latestKnown(context) ?: System.currentTimeMillis()

    private fun anchor(context: Context, serverMs: Long, elapsedMs: Long) {
        val prefs = prefs(context)
        prefs.edit()
            .putLong(KEY_SERVER, serverMs)
            .putLong(KEY_ELAPSED, elapsedMs)
            .putInt(KEY_BOOT, bootCount(context))
            .putLong(KEY_LATEST, maxOf(serverMs, prefs.getLong(KEY_LATEST, 0L)))
            .apply()
    }

    /** Increases on every restart; -1 if the phone doesn't report it. */
    private fun bootCount(context: Context): Int =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

    private fun isOnline(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
