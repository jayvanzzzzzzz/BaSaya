package com.example.basaya.data.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Offline copies of pronunciation word audio hosted on Cloudflare R2.
 * Files live at filesDir/pronunciation_audio/{lessonId}/w{wordIndex}.mp3 and exist
 * only while the lesson is downloaded.
 */
object PronunciationAudioStore {

    private const val ROOT_DIR = "pronunciation_audio"

    fun localFile(context: Context, lessonId: String, wordIndex: Int): File =
        File(lessonDir(context, lessonId), "w$wordIndex.mp3")

    /** Downloads every word that has a cloud URL. Throws if any file fails. */
    suspend fun download(context: Context, lessonId: String, audioRefs: List<String>) =
        withContext(Dispatchers.IO) {
            val dir = lessonDir(context, lessonId)
            dir.deleteRecursively() // drop audio from an older version of the activity
            dir.mkdirs()

            audioRefs.forEachIndexed { index, url ->
                if (url.startsWith("https://")) {
                    LectureAudioStore.downloadFile(url, localFile(context, lessonId, index))
                }
            }
        }

    fun delete(context: Context, lessonId: String) {
        lessonDir(context, lessonId).deleteRecursively()
    }

    private fun lessonDir(context: Context, lessonId: String) =
        File(File(context.filesDir, ROOT_DIR), lessonId)
}
