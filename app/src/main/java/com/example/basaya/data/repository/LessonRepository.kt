package com.example.basaya.data.repository

import android.content.Context
import com.example.basaya.data.database.AppDatabase
import com.example.basaya.data.entity.LessonEntity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class LessonRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun syncLessons(userId: String) {

        // 1. Find the student's class — no class, no lessons.
        val userDoc = firestore.collection("users").document(userId).get().await()
        val classId = userDoc.getString("classId")

        if (classId.isNullOrBlank()) {
            db.lessonDao().deleteLessonsForUser(userId)
            return
        }

        // 2. Progress flags per lesson (unchanged — still per-user)
        data class ProgressInfo(
            val lectureFinished: Boolean,
            val gameFinished: Boolean,
            val activityFinished: Boolean,
            val quizFinished: Boolean
        )

        val assignedSnapshot = firestore
            .collection("users")
            .document(userId)
            .collection("assignedLessons")
            .get()
            .await()

        val progressMap = assignedSnapshot.documents.associate { doc ->
            doc.id to ProgressInfo(
                lectureFinished = doc.getBoolean("lectureFinished") ?: false,
                gameFinished = doc.getBoolean("gameFinished") ?: false,
                activityFinished = doc.getBoolean("activityFinished") ?: false,
                quizFinished = doc.getBoolean("quizFinished") ?: false
            )
        }

        // 3a. Get the pointer list — which lessonIds belong to this class
        val classLessonsSnapshot = firestore
            .collection("classes")
            .document(classId)
            .collection("classLessons")
            .get()
            .await()

        val lessonIds = classLessonsSnapshot.documents.map { it.id }

        if (lessonIds.isEmpty()) {
            db.lessonDao().deleteLessonsForUser(userId)
            return
        }

        // 3b. Fetch the actual lesson content docs by those IDs.
        // whereIn supports up to 30 values per query — fine for a class-sized lesson list.
        // If a class ever exceeds 30 lessons, this needs to be chunked into multiple queries.
        val lessonsSnapshot = firestore
            .collection("lessons")
            .whereIn(com.google.firebase.firestore.FieldPath.documentId(), lessonIds)
            .get()
            .await()

        // preserve isDownloaded flags already stored locally
        val existingDownloadedIds = db.lessonDao().getLessonsForUser(userId)
            .filter { it.isDownloaded }
            .map { it.id }
            .toSet()

        val lessons = lessonsSnapshot.documents.map { doc ->
            val progress = progressMap[doc.id]

            LessonEntity(
                id = doc.id,
                userId = userId,
                classId = classId,
                title = doc.getString("title") ?: "",
                description = doc.getString("description") ?: "",
                difficulty = doc.getString("difficulty") ?: "",
                order = doc.getLong("order")?.toInt() ?: 0,
                unlocked = true,
                lectureFinished = progress?.lectureFinished ?: false,
                gameFinished = progress?.gameFinished ?: false,
                activityFinished = progress?.activityFinished ?: false,
                quizFinished = progress?.quizFinished ?: false,
                isDownloaded = existingDownloadedIds.contains(doc.id)
            )
        }

        db.lessonDao().deleteLessonsForUser(userId)
        db.lessonDao().insertAll(lessons)
    }

    suspend fun getLessons(userId: String): List<LessonEntity> {
        return db.lessonDao().getLessonsForUser(userId)
    }

    suspend fun downloadLesson(lessonId: String): Boolean {
        return try {
            val lectureRepo = LectureRepository(context)
            val crosswordRepo = CrosswordRepository(context)
            val activityRepo = ActivityRepository(context)
            val quizRepo = QuizRepository(context)
            val pronunciationRepo = PronunciationRepository(context)

            lectureRepo.downloadLecture(lessonId)
            crosswordRepo.syncLevels(lessonId)
            crosswordRepo.syncProgress(lessonId)
            activityRepo.downloadActivity(lessonId)
            quizRepo.downloadQuiz(lessonId)
            pronunciationRepo.downloadPronunciation(lessonId)

            db.lessonDao().updateDownloaded(lessonId, true)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun removeDownload(lessonId: String): Boolean {
        return try {
            db.lectureDao().deleteByLessonId(lessonId)
            db.activityDao().deleteByLessonId(lessonId)
            db.quizDao().deleteByLessonId(lessonId)
            db.crosswordLevelDao().deleteByLessonId(lessonId)
            db.pronunciationDao().deleteByLessonId(lessonId)

            db.lessonDao().markLessonRemoved(lessonId)
            true
        } catch (e: Exception) {
            false
        }
    }
}