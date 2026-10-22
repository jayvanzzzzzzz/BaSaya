package com.example.basaya.data.progress

import android.content.Context
import com.example.basaya.data.database.AppDatabase
import com.google.firebase.firestore.DocumentSnapshot

/**
 * Lessons the teacher added as Practice in basaya-web
 * (classes/{classId}/classLessons/{linkId}.mode == "practice"; no mode means Assessment).
 *
 * A Practice lesson can be done again and again and saves nothing: no finished flags, scores,
 * skill results or resume places, and the teacher's reports leave it out. The Firestore rules
 * reject those writes too. LessonRepository fills this from the synced lessons; Room keeps the
 * flag (LessonEntity.practice) so it is known offline.
 *
 * A lesson can be in the class twice: as an Assessment (entry id = lesson id) and as Practice
 * (entry id = "{lessonId}__practice"). Everything on the phone (Room, downloads, intents) uses
 * the entry id; only reads of lessons/{id} in Firestore need [contentId].
 */
object PracticeLessons {

    private const val SUFFIX = "__practice"

    @Volatile
    private var ids: Set<String> = emptySet()

    fun set(lessonIds: Collection<String>) {
        ids = lessonIds.toSet()
    }

    /** Older Practice links were saved under the lesson id itself, so the synced set counts too. */
    fun isPractice(lessonId: String): Boolean = lessonId.endsWith(SUFFIX) || lessonId in ids

    /** The library lesson (lessons/{id}) a class lesson entry shows. */
    fun contentId(entryId: String): String = entryId.removeSuffix(SUFFIX)

    /** Reads one lesson's flag from Room (the app may start on a lesson before any sync). */
    suspend fun refresh(context: Context, lessonId: String): Boolean {
        val practice = AppDatabase.getDatabase(context).lessonDao().getLesson(lessonId)?.practice == true
        ids = if (practice) ids + lessonId else ids - lessonId
        return practice || isPractice(lessonId)
    }

    /** A classLessons link the teacher marked Practice. */
    fun isPracticeLink(link: DocumentSnapshot): Boolean = link.getString("mode") == "practice"
}
