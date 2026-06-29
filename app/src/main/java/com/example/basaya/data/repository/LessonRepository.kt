package com.example.basaya.data.repository

import android.content.Context
import com.example.basaya.data.database.GameDatabase
import com.example.basaya.data.entity.LessonEntity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class LessonRepository(private val context: Context) {

    private val db = GameDatabase.getDatabase(context)
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun syncLessons() {
        val snapshot = firestore
            .collection("lessons")
            .get()
            .await()

        val lessons = snapshot.documents.map { doc ->
            LessonEntity(
                id = doc.id,
                title = doc.getString("title") ?: "",
                description = doc.getString("description") ?: "",
                difficulty = doc.getString("difficulty") ?: "",
                order = doc.getLong("order")?.toInt() ?: 0
            )
        }

        db.lessonDao().insertAll(lessons)
    }

    suspend fun getLessons(): List<LessonEntity> {
        return db.lessonDao().getAllLessons()
    }
}