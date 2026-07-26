package com.example.basaya.data.repository

import android.content.Context
import com.example.basaya.data.database.AppDatabase
import com.example.basaya.data.entity.LessonEntity
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class LessonRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun syncLessons(userId: String) {

        //get this user's assigned lesson IDs + progress flags
        val assignedSnapshot = firestore
            .collection("users")
            .document(userId)
            .collection("assignedLessons")
            .get()
            .await()

        data class AssignedInfo(
            val unlocked: Boolean,
            val lectureFinished: Boolean,
            val gameFinished: Boolean,
            val activityFinished: Boolean,
            val quizFinished: Boolean
        )

        val assignedMap = assignedSnapshot.documents.associate { doc ->
            doc.id to AssignedInfo(
                unlocked = doc.getBoolean("unlocked") ?: false,
                lectureFinished = doc.getBoolean("lectureFinished") ?: false,
                gameFinished = doc.getBoolean("gameFinished") ?: false,
                activityFinished = doc.getBoolean("activityFinished") ?: false,
                quizFinished = doc.getBoolean("quizFinished") ?: false
            )
        }

        val lessonIds = assignedMap.keys.toList()

        //fetch only those lesson documents
        val lessons = mutableListOf<LessonEntity>()

        lessonIds.chunked(30).forEach { chunk ->
            val lessonsSnapshot = firestore
                .collection("lessons")
                .whereIn(FieldPath.documentId(), chunk)
                .get()
                .await()

            lessonsSnapshot.documents.forEach { doc ->
                val info = assignedMap[doc.id]

                lessons.add(
                    LessonEntity(
                        id = doc.id,
                        userId = userId,
                        title = doc.getString("title") ?: "",
                        description = doc.getString("description") ?: "",
                        difficulty = doc.getString("difficulty") ?: "",
                        order = doc.getLong("order")?.toInt() ?: 0,
                        unlocked = info?.unlocked ?: false,
                        lectureFinished = info?.lectureFinished ?: false,
                        gameFinished = info?.gameFinished ?: false,
                        activityFinished = info?.activityFinished ?: false,
                        quizFinished = info?.quizFinished ?: false
                    )
                )
            }
        }

        db.lessonDao().deleteLessonsForUser(userId)
        db.lessonDao().insertAll(lessons)
    }



    suspend fun getLessons(userId: String): List<LessonEntity> {
        return db.lessonDao().getLessonsForUser(userId)
    }
}