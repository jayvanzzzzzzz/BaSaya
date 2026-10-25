package ph.basaya.app.data.progress

import ph.basaya.app.data.entity.LessonEntity
import com.google.firebase.firestore.DocumentSnapshot

/**
 * Which lessons count, and when a lesson counts as finished.
 *
 * - Only the lessons of the student's current class count (synced into Room from
 *   classes/{classId}/classLessons). Other documents under users/{uid}/assignedLessons,
 *   such as old test assignments or lessons from a previous class, are ignored.
 * - A lesson is finished only when every part is done: Lecture, Laro and Gawain (or skipped
 *   by the teacher), Bigkas and Pagsusulit.
 */
object LessonCompletion {

    /** Progress documents of the class's lessons only. */
    fun classDocs(docs: List<DocumentSnapshot>, classLessons: List<LessonEntity>): List<DocumentSnapshot> {
        val ids = classLessons.map { it.id }.toSet()
        return docs.filter { it.id in ids }
    }

    fun isFinished(doc: DocumentSnapshot, gameSkipped: Boolean, activitySkipped: Boolean): Boolean =
        doc.getBoolean("lectureFinished") == true &&
            (gameSkipped || doc.getBoolean("gameFinished") == true) &&
            (activitySkipped || doc.getBoolean("activityFinished") == true) &&
            doc.getBoolean("pronunciationFinished") == true &&
            doc.getBoolean("quizFinished") == true

    /**
     * The class lessons the student is held to: not the ones whose deadline passed before they
     * joined the class (they never could do them). Same rule as the web's teacher progress page.
     */
    fun countedLessons(
        docs: List<DocumentSnapshot>,
        classLessons: List<LessonEntity>,
        joinedAt: Long?
    ): List<LessonEntity> {
        if (joinedAt == null) return classLessons
        val byId = docs.associateBy { it.id }
        return classLessons.filter { lesson ->
            val due = lesson.dueAt ?: return@filter true
            if (due >= joinedAt) return@filter true
            byId[lesson.id]?.let { isFinished(it, lesson.gameSkipped, lesson.activitySkipped) } == true
        }
    }

    /** Number of class lessons whose every part is done. */
    fun finishedCount(docs: List<DocumentSnapshot>, classLessons: List<LessonEntity>): Int {
        val byId = classLessons.associateBy { it.id }
        return classDocs(docs, classLessons).count {
            val lesson = byId[it.id]
            isFinished(it, gameSkipped = lesson?.gameSkipped == true, activitySkipped = lesson?.activitySkipped == true)
        }
    }
}
