package com.example.basaya.data.repository

import android.content.Context
import com.example.basaya.data.database.AppDatabase
import com.example.basaya.data.entity.CrosswordLevelEntity
import com.example.basaya.model.CrosswordGameLevel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.example.basaya.data.entity.CrosswordProgress
import com.google.firebase.auth.FirebaseAuth

class CrosswordRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun syncLevels(lessonId: String) {

        val snapshot = firestore
            .collection("lessons")
            .document(lessonId)
            .collection("game")
            .orderBy("level")
            .get()
            .await()

        val levels = snapshot.documents.map { doc ->
            CrosswordLevelEntity(
                id = doc.id,
                lessonId = lessonId,
                level = doc.getLong("level")?.toInt() ?: 0,
                words = (doc.get("words") as? List<*>)?.joinToString(",") ?: "",
                order = doc.getLong("order")?.toInt() ?: 0
            )
        }

        db.crosswordLevelDao().insertAll(levels)
    }

    suspend fun getLevels(lessonId: String): List<CrosswordGameLevel> {
        return db.crosswordLevelDao().getLevelsForLesson(lessonId)
            .map { entity ->
                CrosswordGameLevel(
                    id = entity.level,
                    level = entity.level,
                    words = entity.words.split(",")
                )
            }
    }

    /**
     * Only writes progress from Firestore if there's no local progress yet
     * (e.g. first time opening this lesson's game on this device).
     * Never overwrites existing local progress — that would wipe out
     * foundWords for a level the student is mid-way through.
     */
    suspend fun syncProgress(lessonId: String) {

        val existing = db.crosswordProgressDao().getProgress(lessonId)
        if (existing != null) return   // local progress already exists — don't touch it

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        try {
            val doc = firestore
                .collection("users")
                .document(uid)
                .collection("assignedLessons")
                .document(lessonId)
                .get()
                .await()

            if (doc.exists()) {
                val currentLevel = doc.getLong("gameScore")?.toInt()?.plus(1) ?: 1
                val completedLevel = doc.getLong("gameScore")?.toInt() ?: 0

                db.crosswordProgressDao().saveProgress(
                    CrosswordProgress(
                        lessonId = lessonId,
                        currentLevel = currentLevel,
                        completedLevel = completedLevel,
                        foundWords = ""
                    )
                )
            } else {
                db.crosswordProgressDao().saveProgress(
                    CrosswordProgress(
                        lessonId = lessonId,
                        currentLevel = 1,
                        completedLevel = 0,
                        foundWords = ""
                    )
                )
            }
        } catch (e: Exception) {
            // network failure here is fine — local progress will just stay absent
            // and get retried next time syncProgress is called with no local row yet
        }
    }
}