package ph.basaya.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * A lesson has no Gawain when basaya-web marked it skipped
 * (lessons/{id}/activity/status = { completion: "skipped" }). Like a skipped game
 * (see GameStatus): hidden on the lesson path, treated as done for lesson completion,
 * and left out of Pagkilala monitoring.
 */
object ActivityStatus {

    suspend fun isSkipped(lessonId: String, firestore: FirebaseFirestore = FirebaseFirestore.getInstance()): Boolean? =
        try {
            val snapshot = firestore.collection("lessons").document(lessonId)
                .collection("activity")
                .get().await()
            val docs = snapshot.documents
            // Offline and never read: an empty cache says nothing about the lesson.
            if (docs.isEmpty() && snapshot.metadata.isFromCache) {
                null
            } else {
                docs.firstOrNull { it.id == "status" }?.getString("completion") == "skipped"
            }
        } catch (e: Exception) {
            null // unknown (offline and never read)
        }
}
