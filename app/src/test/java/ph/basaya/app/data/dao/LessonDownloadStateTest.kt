package ph.basaya.app.data.dao

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import ph.basaya.app.data.entity.LessonEntity

class LessonDownloadStateTest {
    @Test fun refreshKeepsDownloadCompletedAfterMetadataWasFetched() = runBlocking {
        val dao = MemoryLessons()
        val remote = lesson(downloaded = false, title = "Updated title")
        dao.insertAll(listOf(lesson(downloaded = false)))
        assertTrue(dao.completeDownload("lesson", "student", "class"))

        dao.replaceLessonsForUser("student", listOf(remote))

        assertEquals(true, dao.isLessonDownloaded("lesson"))
        assertEquals("Updated title", dao.getLesson("lesson")?.title)
    }

    @Test fun refreshDoesNotRestoreADownloadRemovedAfterMetadataWasFetched() = runBlocking {
        val dao = MemoryLessons()
        val stale = lesson(downloaded = true)
        dao.insertAll(listOf(stale))
        dao.markLessonRemoved("lesson")

        dao.replaceLessonsForUser("student", listOf(stale))

        assertEquals(false, dao.isLessonDownloaded("lesson"))
    }

    @Test fun newAssignmentsCannotInheritDownloadFlagsFromIncomingMetadata() = runBlocking {
        val dao = MemoryLessons()
        dao.replaceLessonsForUser("student", listOf(lesson(downloaded = true)))
        assertEquals(false, dao.isLessonDownloaded("lesson"))
    }

    @Test fun changingClassDoesNotTransferThePreviousDownloadFlag() = runBlocking {
        val dao = MemoryLessons()
        dao.insertAll(listOf(lesson(downloaded = true)))
        dao.replaceLessonsForUser("student", listOf(lesson(downloaded = true).copy(classId = "other")))
        assertEquals(false, dao.isLessonDownloaded("lesson"))
        assertFalse(dao.completeDownload("lesson", "student", "class"))
    }

    @Test fun changingAccountDoesNotTransferThePreviousDownloadFlag() = runBlocking {
        val dao = MemoryLessons()
        dao.insertAll(listOf(lesson(downloaded = true)))
        dao.replaceLessonsForUser("other", listOf(lesson(downloaded = true).copy(userId = "other")))
        assertEquals(false, dao.isLessonDownloaded("lesson"))
        assertFalse(dao.completeDownload("lesson", "student", "class"))
    }

    @Test fun removedAssignmentsCannotReportSuccessfulDownload() = runBlocking {
        val dao = MemoryLessons()
        dao.insertAll(listOf(lesson(downloaded = false)))
        dao.replaceLessonsForUser("student", emptyList())
        assertFalse(dao.completeDownload("lesson", "student", "class"))
        assertNull(dao.isLessonDownloaded("lesson"))
    }

    @Test fun refreshLeavesOtherStudentsLessonsAlone() = runBlocking {
        val dao = MemoryLessons()
        dao.insertAll(listOf(lesson(downloaded = true).copy(id = "other-lesson", userId = "other")))
        dao.replaceLessonsForUser("student", listOf(lesson(downloaded = false)))
        assertEquals(true, dao.isLessonDownloaded("other-lesson"))
        assertEquals(false, dao.isLessonDownloaded("lesson"))
    }

    private fun lesson(downloaded: Boolean, title: String = "Lesson") = LessonEntity(
        id = "lesson", userId = "student", classId = "class", title = title,
        description = "", difficulty = "", order = 0, unlocked = true, isDownloaded = downloaded
    )

    /** Exercises the production DAO merge/commit methods without a phone or Firestore. */
    private class MemoryLessons : LessonDao {
        private val rows = linkedMapOf<String, LessonEntity>()
        override suspend fun insertAll(lessons: List<LessonEntity>) {
            lessons.forEach { rows[it.id] = it }
        }
        override suspend fun getAllLessons() = rows.values.toList()
        override suspend fun getLessonsForUser(userId: String) = rows.values.filter { it.userId == userId }
        override suspend fun deleteLessonsForUser(userId: String) {
            rows.entries.removeAll { it.value.userId == userId }
        }
        override suspend fun updateDownloaded(lessonId: String, isDownloaded: Boolean): Int {
            val row = rows[lessonId] ?: return 0
            rows[lessonId] = row.copy(isDownloaded = isDownloaded)
            return 1
        }
        override suspend fun getLesson(lessonId: String) = rows[lessonId]
        override suspend fun isLessonDownloaded(lessonId: String) = rows[lessonId]?.isDownloaded
        override suspend fun markLessonRemoved(lessonId: String) { updateDownloaded(lessonId, false) }
        override suspend fun updateProgress(
            userId: String, lessonId: String, lectureFinished: Boolean, gameFinished: Boolean,
            activityFinished: Boolean, quizFinished: Boolean, started: Boolean
        ) {
            val row = rows[lessonId]?.takeIf { it.userId == userId } ?: return
            rows[lessonId] = row.copy(
                lectureFinished = lectureFinished, gameFinished = gameFinished,
                activityFinished = activityFinished, quizFinished = quizFinished, started = started
            )
        }
    }
}
