package com.example.basaya.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * A lesson has no game when basaya-web marked it skipped
 * (lessons/{id}/game/status = { completion: "skipped" }) or when it has no game levels at all.
 * Either way the game is "not part of this lesson": hidden on the lesson path, treated as done
 * for lesson completion, and left out of Talasalitaan monitoring.
 */
object GameStatus {

    suspend fun isSkipped(lessonId: String, firestore: FirebaseFirestore = FirebaseFirestore.getInstance()): Boolean? =
        try {
            val snapshot = firestore.collection("lessons").document(lessonId)
                .collection("game")
                .get().await()
            val docs = snapshot.documents
            // Offline and never read: an empty cache says nothing about the lesson.
            if (docs.isEmpty() && snapshot.metadata.isFromCache) {
                null
            } else {
                val status = docs.firstOrNull { it.id == "status" }
                status?.getString("completion") == "skipped" || docs.none { it.id != "status" }
            }
        } catch (e: Exception) {
            null // unknown (offline and never read)
        }
}
