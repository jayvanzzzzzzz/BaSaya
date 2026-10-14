package com.example.basaya.data.audio

import android.content.Context
import android.util.Log
import com.example.basaya.data.model.LecturePage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Lecture sentence audio for lessons that aren't downloaded, kept in cacheDir so a sentence
 * plays the moment it appears. The lesson path waits for the page the lecture opens on
 * (DashboardActivity.openLecture) and the lecture fetches the next page while it is read.
 * Android may clear cacheDir when it needs space; a missing file just downloads again.
 */
object LectureAudioCache {

    private const val DIR = "lecture_audio_cache"

    /**
     * Downloads outlive the screen that asked for them: a timed-out wait leaves them running,
     * so "Subukan muli" or the next page picks up where they got to.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val locks = ConcurrentHashMap<String, Mutex>()

    /** The cached copy of [url], or null when it isn't downloaded yet. */
    fun file(context: Context, url: String): File? = target(context, url).takeIf { it.exists() }

    /**
     * Makes every sentence of [page] ready to play: downloaded lessons already are, the rest
     * are fetched into the cache. Completes with false when any file failed.
     */
    fun preparePage(context: Context, lessonId: String, page: LecturePage): Deferred<Boolean> {
        val app = context.applicationContext
        return scope.async {
            coroutineScope {
                page.audioUrls.orEmpty().mapIndexed { index, url ->
                    async {
                        url.isBlank() ||
                            LectureAudioStore.localFile(app, lessonId, page.pageNumber, index).exists() ||
                            fetch(app, url)
                    }
                }.awaitAll().all { it }
            }
        }
    }

    private suspend fun fetch(context: Context, url: String): Boolean =
        locks.getOrPut(url) { Mutex() }.withLock {
            val target = target(context, url)
            if (target.exists()) return@withLock true
            try {
                target.parentFile?.mkdirs()
                LectureAudioStore.downloadFile(url, target)
                true
            } catch (e: Exception) {
                Log.w("LectureAudioCache", "Could not download $url", e)
                false
            }
        }

    private fun target(context: Context, url: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(File(context.cacheDir, DIR), "$hash.mp3")
    }
}
