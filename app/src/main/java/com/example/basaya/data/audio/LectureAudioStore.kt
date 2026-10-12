package com.example.basaya.data.audio

import android.content.Context
import com.example.basaya.data.model.LecturePage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Offline copies of lecture sentence audio hosted on Cloudflare R2.
 * Files live at filesDir/lecture_audio/{lessonId}/p{page}_s{sentence}.mp3 and exist
 * only while the lesson is downloaded.
 */
object LectureAudioStore {

    private const val ROOT_DIR = "lecture_audio"
    private const val TIMEOUT_MS = 15_000

    fun localFile(context: Context, lessonId: String, pageNumber: Int, sentenceIndex: Int): File =
        File(lessonDir(context, lessonId), "p${pageNumber}_s${sentenceIndex}.mp3")

    /** Downloads every sentence that has a cloud URL. Throws if any file fails. */
    suspend fun download(context: Context, lessonId: String, pages: List<LecturePage>) =
        withContext(Dispatchers.IO) {
            val dir = lessonDir(context, lessonId)
            dir.deleteRecursively() // drop audio from an older version of the lecture
            dir.mkdirs()

            pages.forEach { page ->
                page.audioUrls.orEmpty().forEachIndexed { index, url ->
                    if (url.isNotBlank()) {
                        downloadFile(url, localFile(context, lessonId, page.pageNumber, index))
                    }
                }
            }
        }

    fun delete(context: Context, lessonId: String) {
        lessonDir(context, lessonId).deleteRecursively()
    }

    private fun lessonDir(context: Context, lessonId: String) =
        File(File(context.filesDir, ROOT_DIR), lessonId)

    private fun downloadFile(url: String, target: File) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode} for $url")
            }

            // Write to a temp file first so a dropped connection never leaves a truncated mp3.
            val temp = File(target.parentFile, "${target.name}.part")
            connection.inputStream.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            if (!temp.renameTo(target)) {
                temp.delete()
                throw IOException("Could not save ${target.name}")
            }
        } finally {
            connection.disconnect()
        }
    }
}
