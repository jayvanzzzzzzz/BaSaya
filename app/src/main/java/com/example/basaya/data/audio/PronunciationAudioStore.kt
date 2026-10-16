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

    /**
     * Downloads every word that has a cloud URL, replacing the lesson's saved audio.
     * Throws if any file fails, and then the audio saved before stays as it was.
     */
    suspend fun download(context: Context, lessonId: String, audioRefs: List<String>) =
        withContext(Dispatchers.IO) {
            LectureAudioStore.replaceDir(lessonDir(context, lessonId)) { staging ->
                audioRefs.forEachIndexed { index, url ->
                    if (url.startsWith("https://")) {
                        LectureAudioStore.downloadFile(url, File(staging, "w$index.mp3"))
                    }
                }
            }
        }

    fun delete(context: Context, lessonId: String) {
        lessonDir(context, lessonId).deleteRecursively()
    }

    private fun lessonDir(context: Context, lessonId: String) =
        File(File(context.filesDir, ROOT_DIR), lessonId)
}
