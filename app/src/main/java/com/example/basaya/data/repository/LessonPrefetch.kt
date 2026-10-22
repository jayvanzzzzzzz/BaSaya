package com.example.basaya.data.repository

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/**
 * The lesson sync the splash screen starts. It runs outside the splash's lifecycle, so it keeps
 * going if the splash stops waiting; Home then picks it up instead of starting a second sync.
 */
object LessonPrefetch {

    private const val TAG = "LessonPrefetch"

    // A finished sync older than this is stale; Home syncs again on its own.
    private const val FRESH_FOR_MS = 30_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var userId: String? = null
    private var sync: Deferred<Unit>? = null
    private var finishedAt = 0L

    /** Starts the sync; while one is still running (e.g. the splash was rotated) it is reused. */
    fun start(context: Context, userId: String): Deferred<Unit> {
        sync?.takeIf { it.isActive && this.userId == userId }?.let { return it }
        val repo = LessonRepository(context.applicationContext)
        return scope.async<Unit> {
            try {
                repo.syncLessons(userId)
            } catch (e: Exception) {
                Log.e(TAG, "Lesson sync from the splash screen failed", e)
            } finally {
                finishedAt = SystemClock.elapsedRealtime()
            }
        }.also {
            this.userId = userId
            sync = it
        }
    }

    /** Hands over the splash's sync once, if it is for this user and still running or fresh. */
    fun take(userId: String): Deferred<Unit>? {
        val pending = sync?.takeIf { this.userId == userId } ?: return null
        sync = null
        this.userId = null
        val fresh = pending.isActive || SystemClock.elapsedRealtime() - finishedAt < FRESH_FOR_MS
        return pending.takeIf { fresh }
    }
}
