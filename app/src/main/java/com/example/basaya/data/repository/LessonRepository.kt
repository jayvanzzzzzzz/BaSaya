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

        //get this user's assigned lesson IDs
        val assignedSnapshot = firestore
            .collection("users")
            .document(userId)
            .collection("assignedLessons")
            .get()
            .await()

        val unlockedMap = assignedSnapshot.documents.associate { doc ->
            doc.id to (doc.getBoolean("unlocked") ?: false)
        }

        val lessonIds = unlockedMap.keys.toList()

        //fetch only those lesson documents
        val lessons = mutableListOf<LessonEntity>()

        lessonIds.chunked(30).forEach { chunk ->
            val lessonsSnapshot = firestore
                .collection("lessons")
                .whereIn(FieldPath.documentId(), chunk)
                .get()
                .await()

            lessonsSnapshot.documents.forEach { doc ->
                lessons.add(
                    LessonEntity(
                        id = doc.id,
                        userId = userId,
                        title = doc.getString("title") ?: "",
                        description = doc.getString("description") ?: "",
                        difficulty = doc.getString("difficulty") ?: "",
                        order = doc.getLong("order")?.toInt() ?: 0,
                        unlocked = unlockedMap[doc.id] ?: false
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