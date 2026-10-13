package com.example.basaya.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * basaya-web marks a lesson's game as skipped with lessons/{id}/game/status = { completion: "skipped" }.
 * A skipped game is "not part of this lesson": hidden on the lesson path, treated as done for
 * lesson completion, and left out of Talasalitaan monitoring.
 */
object GameStatus {

    suspend fun isSkipped(lessonId: String, firestore: FirebaseFirestore = FirebaseFirestore.getInstance()): Boolean? =
        try {
            firestore.collection("lessons").document(lessonId)
                .collection("game").document("status")
                .get().await()
                .getString("completion") == "skipped"
        } catch (e: Exception) {
            null // unknown (offline and never read)
        }
}
