package com.example.basaya.data.repository

import android.content.Context
import com.example.basaya.data.database.AppDatabase
import com.example.basaya.data.entity.CrosswordLevelEntity
import com.example.basaya.model.CrosswordGameLevel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class CrosswordRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun syncLevels(lessonId:String){

        val snapshot = firestore
            .collection("lessons")
            .document(lessonId)
            .collection("game")
            .orderBy("level")
            .get()
            .await()

        val levels = snapshot.documents.map { doc->
            CrosswordLevelEntity(
                id = doc.id,
                lessonId = lessonId,
                level = doc.getLong("level")?.toInt() ?: 0,
                words = (doc.get("words") as? List<*>)?.joinToString(",")?:"",
                order = doc.getLong("order")?.toInt()?:0
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

}