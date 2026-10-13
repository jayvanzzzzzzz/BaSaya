package com.example.basaya.data.progress

import com.example.basaya.data.entity.LessonEntity
import com.google.firebase.firestore.DocumentSnapshot

/**
 * Which lessons count, and when a lesson counts as finished.
 *
 * - Only the lessons of the student's current class count (synced into Room from
 *   classes/{classId}/classLessons). Other documents under users/{uid}/assignedLessons,
 *   such as old test assignments or lessons from a previous class, are ignored.
 * - A lesson is finished only when every part is done: Lecture, Laro (or skipped by the
 *   teacher), Gawain, Bigkas and Pagsusulit.
 */
object LessonCompletion {

    /** Progress documents of the class's lessons only. */
    fun classDocs(docs: List<DocumentSnapshot>, classLessons: List<LessonEntity>): List<DocumentSnapshot> {
        val ids = classLessons.map { it.id }.toSet()
        return docs.filter { it.id in ids }
    }

    fun isFinished(doc: DocumentSnapshot, gameSkipped: Boolean): Boolean =
        doc.getBoolean("lectureFinished") == true &&
            (gameSkipped || doc.getBoolean("gameFinished") == true) &&
            doc.getBoolean("activityFinished") == true &&
            doc.getBoolean("pronunciationFinished") == true &&
            doc.getBoolean("quizFinished") == true

    /** Number of class lessons whose every part is done. */
    fun finishedCount(docs: List<DocumentSnapshot>, classLessons: List<LessonEntity>): Int {
        val skipped = classLessons.filter { it.gameSkipped }.map { it.id }.toSet()
        return classDocs(docs, classLessons).count { isFinished(it, gameSkipped = it.id in skipped) }
    }
}
