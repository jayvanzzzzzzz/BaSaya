package com.example.basaya.data.progress

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Where the student stopped in the lecture, Gawain, Bigkas or quiz, so closing the screen or the
 * app (or switching to the website) continues from there. At the deadline, an unfinished part
 * is scored from its saved place (SkillScoring.withMissed).
 *
 * users/{uid}/assignedLessons/{lessonId}.resume.{part} = { done, total, ...part fields, updatedAt }
 * quiz: { score, answered: [question index as stored], missed: [question text] }
 *
 * `done` is how many pages/words are finished and `total` how many there are, which the lesson
 * path shows as "2/5 pages". Same shape as basaya-web's student/data.js (resumeOf/saveResume).
 */
object LessonResume {

    const val LECTURE = "lecture"
    /** Crossword: { done: words found on the level being played, total, level }. */
    const val GAME = "game"
    const val ACTIVITY = "activity"
    const val PRONUNCIATION = "pronunciation"
    const val QUIZ = "quiz"

    private fun lessonDoc(uid: String, lessonId: String) =
        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .collection("assignedLessons").document(lessonId)

    /** The lesson's progress document, or null when it can't be read (offline with no cache). */
    suspend fun loadProgress(uid: String, lessonId: String): DocumentSnapshot? =
        // A Practice lesson keeps no place: it always starts from the beginning.
        if (PracticeLessons.isPractice(lessonId)) null else try {
            lessonDoc(uid, lessonId).get().await()
        } catch (e: Exception) {
            Log.w("LessonResume", "Could not read progress for $lessonId", e)
            null
        }

    /** The saved place in one part, or null. A place saved for a different item count is ignored. */
    fun of(progress: DocumentSnapshot?, part: String, total: Int): Map<String, Any?>? {
        @Suppress("UNCHECKED_CAST")
        val state = (progress?.get("resume") as? Map<String, Any?>)?.get(part) as? Map<String, Any?>
            ?: return null
        val done = (state["done"] as? Number)?.toInt() ?: return null
        if (done < 0 || (state["total"] as? Number)?.toInt() != total) return null
        return state + ("done" to done.coerceAtMost(total))
    }

    /**
     * The page the lecture opens on: the next unread page (the last page stays readable before
     * finishing), or page 1 when rereading a finished lecture.
     */
    fun lectureStartPage(progress: DocumentSnapshot?, pageCount: Int): Int {
        if (pageCount == 0 || progress?.getBoolean("lectureFinished") == true) return 0
        val done = of(progress, LECTURE, pageCount)?.get("done") as? Int ?: 0
        return done.coerceAtMost(pageCount - 1)
    }

    /** How far the student got in a part, for the lesson path; 0 when nothing is saved. */
    fun done(progress: DocumentSnapshot?, part: String): Int {
        @Suppress("UNCHECKED_CAST")
        val state = (progress?.get("resume") as? Map<String, Any?>)?.get(part) as? Map<String, Any?>
        return (state?.get("done") as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
    }

    /**
     * Something was done even if no part is finished yet: a part left halfway, a game level,
     * or Bigkas. Makes the lesson Nasa progreso (basaya-web's hasStarted in student/data.js).
     */
    fun started(progress: DocumentSnapshot?): Boolean {
        if (progress == null) return false
        val resume = progress.get("resume") as? Map<*, *>
        return resume?.values?.any { ((it as? Map<*, *>)?.get("done") as? Number)?.toInt() ?: 0 > 0 } == true ||
            (progress.getLong("gameScore") ?: 0) > 0 ||
            progress.getBoolean("pronunciationFinished") == true
    }

    /** The item count the saved place was made for, or null. */
    fun total(progress: DocumentSnapshot?, part: String): Int? {
        @Suppress("UNCHECKED_CAST")
        val state = (progress?.get("resume") as? Map<String, Any?>)?.get(part) as? Map<String, Any?>
        return (state?.get("total") as? Number)?.toInt()
    }

    /**
     * Saves the place without waiting: offline, Firestore keeps the write and sends it later,
     * and losing one save only costs a few steps.
     */
    fun save(uid: String, lessonId: String, part: String, state: Map<String, Any>) {
        if (PracticeLessons.isPractice(lessonId)) return // Practice: nothing is saved
        lessonDoc(uid, lessonId)
            .set(
                mapOf("resume" to mapOf(part to state + ("updatedAt" to FieldValue.serverTimestamp()))),
                SetOptions.merge()
            )
            .addOnFailureListener { Log.w("LessonResume", "Could not save the $part place", it) }
    }

    /** Drops the saved place once the part is finished. */
    fun clear(uid: String, lessonId: String, part: String) {
        if (PracticeLessons.isPractice(lessonId)) return // Practice: nothing is saved
        lessonDoc(uid, lessonId)
            .set(mapOf("resume" to mapOf(part to FieldValue.delete())), SetOptions.merge())
            .addOnFailureListener { Log.w("LessonResume", "Could not clear the $part place", it) }
    }
}
