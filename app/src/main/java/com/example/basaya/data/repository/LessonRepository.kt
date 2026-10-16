package com.example.basaya.data.repository

import android.content.Context
import android.util.Log
import com.example.basaya.data.audio.LectureAudioStore
import com.example.basaya.data.audio.PronunciationAudioStore
import com.example.basaya.data.database.AppDatabase
import com.example.basaya.data.entity.ClassNotificationEntity
import com.example.basaya.data.entity.LessonEntity
import com.example.basaya.data.notifications.LessonInbox
import com.example.basaya.data.notifications.NotificationReadState
import com.example.basaya.data.notifications.NotificationScheduler
import com.example.basaya.data.progress.LessonCompletion
import com.example.basaya.data.schedule.TrustedClock
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LessonRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun syncLessons(userId: String) {
        try {
            coroutineScope {
                // Refreshes the server time used to lock scheduled lessons (no-op offline).
                launch { TrustedClock.sync(context, userId) }
                syncLessonList(userId)
            }
        } finally {
            // Notifications and deadline reminders follow the freshly synced lessons.
            NotificationScheduler.run(context)
            LessonInbox.notifyChanged()
        }
    }

    private suspend fun syncLessonList(userId: String) {

        // 1. Find the student's class — no class, no lessons.
        val userDoc = firestore.collection("users").document(userId).get().await()
        val classId = userDoc.getString("classId")
        Log.d(TAG, "syncLessons uid=$userId classId=$classId")
        NotificationReadState.mergeRemote(
            context, userId, userDoc.getTimestamp("notifReadAt")?.toDate()?.time
        )

        if (classId.isNullOrBlank()) {
            db.lessonDao().deleteLessonsForUser(userId)
            return
        }

        syncNotifications(classId)

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

        // Link docs may be keyed by the lesson id, or carry it in a "lessonId" field
        // (auto-id links written by the teacher side).
        // The link's "order" is the teacher's ordering for this class.
        val linkOrders = classLessonsSnapshot.documents.associate { link ->
            (link.getString("lessonId")?.takeIf { it.isNotBlank() } ?: link.id) to
                link.getLong("order")?.toInt()
        }
        // The teacher's optional open date and deadline, and when the lesson was added.
        data class LinkSchedule(val openAt: Long?, val dueAt: Long?, val assignedAt: Long?)
        val linkSchedules = classLessonsSnapshot.documents.associate { link ->
            (link.getString("lessonId")?.takeIf { it.isNotBlank() } ?: link.id) to
                LinkSchedule(
                    openAt = link.getTimestamp("openAt")?.toDate()?.time,
                    dueAt = link.getTimestamp("dueAt")?.toDate()?.time,
                    assignedAt = link.getTimestamp("createdAt")?.toDate()?.time
                )
        }
        val lessonIds = linkOrders.keys.toList()
        Log.d(TAG, "classes/$classId/classLessons -> ${lessonIds.size} ids: $lessonIds")

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
        Log.d(TAG, "lessons whereIn -> ${lessonsSnapshot.size()} docs")

        // preserve isDownloaded flags already stored locally
        val existingLessons = db.lessonDao().getLessonsForUser(userId)
        val existingDownloadedIds = existingLessons
            .filter { it.isDownloaded }
            .map { it.id }
            .toSet()

        // Which lessons the teacher set to "Skip game" (read in parallel; offline keeps the last known value)
        val skippedGames = coroutineScope {
            lessonsSnapshot.documents.map { doc -> async { doc.id to GameStatus.isSkipped(doc.id, firestore) } }.awaitAll()
        }.toMap()
        val previousSkipped = existingLessons.associate { it.id to it.gameSkipped }
        // Same for "Skip Gawain"
        val skippedActivities = coroutineScope {
            lessonsSnapshot.documents.map { doc -> async { doc.id to ActivityStatus.isSkipped(doc.id, firestore) } }.awaitAll()
        }.toMap()
        val previousSkippedActivities = existingLessons.associate { it.id to it.activitySkipped }

        // When the student joined the class. A lesson whose deadline passed before then (and
        // that they never finished) is left out: they could never have done it.
        val joinedAt = try {
            firestore.collection("classes").document(classId)
                .collection("students").document(userId).get().await()
                .getTimestamp("joinedAt")?.toDate()?.time
        } catch (e: Exception) {
            null
        }
        val progressDocs = assignedSnapshot.documents.associateBy { it.id }
        val closedBeforeJoining = lessonsSnapshot.documents.map { it.id }.filter { id ->
            val due = linkSchedules[id]?.dueAt
            val finished = progressDocs[id]?.let {
                LessonCompletion.isFinished(
                    it,
                    gameSkipped = skippedGames[id] ?: previousSkipped[id] ?: false,
                    activitySkipped = skippedActivities[id] ?: previousSkippedActivities[id] ?: false
                )
            } == true
            joinedAt != null && due != null && due < joinedAt && !finished
        }.toSet()

        val lessons = lessonsSnapshot.documents.filter { it.id !in closedBeforeJoining }.map { doc ->
            val progress = progressMap[doc.id]

            LessonEntity(
                id = doc.id,
                userId = userId,
                classId = classId,
                title = doc.getString("title") ?: "",
                description = doc.getString("description") ?: "",
                difficulty = doc.getString("difficulty") ?: "",
                order = linkOrders[doc.id] ?: doc.getLong("order")?.toInt() ?: 0,
                unlocked = true,
                lectureFinished = progress?.lectureFinished ?: false,
                gameFinished = progress?.gameFinished ?: false,
                activityFinished = progress?.activityFinished ?: false,
                quizFinished = progress?.quizFinished ?: false,
                isDownloaded = existingDownloadedIds.contains(doc.id),
                gameSkipped = skippedGames[doc.id] ?: previousSkipped[doc.id] ?: false,
                activitySkipped = skippedActivities[doc.id] ?: previousSkippedActivities[doc.id] ?: false,
                openAt = linkSchedules[doc.id]?.openAt,
                dueAt = linkSchedules[doc.id]?.dueAt,
                assignedAt = linkSchedules[doc.id]?.assignedAt
            )
        }

        db.lessonDao().deleteLessonsForUser(userId)
        db.lessonDao().insertAll(lessons)

        // Finishing a crossword level always saves gameScore, so a phone past level 1 with no
        // gameScore in Firestore is stale: the teacher removed the lesson (which deletes the
        // class's progress) and added it back. Start the game over, like the rest of the lesson.
        val gameScored = assignedSnapshot.documents
            .filter { it.getLong("gameScore") != null }
            .map { it.id }
            .toSet()
        val resetGames = lessons.map { it.id }.filter { it !in gameScored }
        if (resetGames.isNotEmpty()) db.crosswordProgressDao().deleteAdvancedProgress(resetGames)
    }

    /** The class's "new lesson" notifications (classes/{classId}/notifications) into Room. */
    private suspend fun syncNotifications(classId: String) {
        try {
            val snapshot = firestore
                .collection("classes")
                .document(classId)
                .collection("notifications")
                .orderBy("visibleAt", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .await()
            val items = snapshot.documents.mapNotNull { doc ->
                ClassNotificationEntity(
                    id = doc.id,
                    classId = classId,
                    title = doc.getString("title") ?: return@mapNotNull null,
                    body = doc.getString("body").orEmpty(),
                    lessonIds = (doc.get("lessonIds") as? List<*>)
                        ?.filterIsInstance<String>()?.joinToString(",").orEmpty(),
                    visibleAt = doc.getTimestamp("visibleAt")?.toDate()?.time
                        ?: return@mapNotNull null
                )
            }
            db.classNotificationDao().upsertAll(items)
        } catch (e: Exception) {
            Log.w(TAG, "Notification sync failed", e)
        }
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
            LectureAudioStore.delete(context, lessonId)
            PronunciationAudioStore.delete(context, lessonId)

            db.lessonDao().markLessonRemoved(lessonId)
            true
        } catch (e: Exception) {
            false
        }
    }

    private companion object {
        const val TAG = "LESSON_SYNC"
    }
}