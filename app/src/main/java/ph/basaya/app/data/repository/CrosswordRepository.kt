package ph.basaya.app.data.repository

import ph.basaya.app.data.progress.ProgressSession

import android.content.Context
import ph.basaya.app.data.database.AppDatabase
import ph.basaya.app.data.entity.CrosswordLevelEntity
import ph.basaya.app.data.model.CrosswordGameLevel
import androidx.room.withTransaction
import ph.basaya.app.data.progress.PracticeLessons
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import android.util.Log
import kotlinx.coroutines.tasks.await
import ph.basaya.app.data.entity.CrosswordProgress
import com.google.firebase.auth.FirebaseAuth
import kotlin.Float
import java.util.concurrent.ConcurrentHashMap

class CrosswordRepository(private val context: Context) {

    private companion object {
        /** Keep undownloaded levels in memory while the app is open so the next level still works offline. */
        val sessionLevels = ConcurrentHashMap<String, List<CrosswordGameLevel>>()
    }

    private val db by lazy { AppDatabase.getDatabase(context) }
    private val firestore = FirebaseFirestore.getInstance()

    /** Read from Room first, then Firestore for display only; only explicit downloads save lesson content to Room. */
    suspend fun getLevels(lessonId: String): List<CrosswordGameLevel> {
        LessonContentPrefetch.takeGame(lessonId)?.let { return it }

        val isDownloaded = db.lessonDao().isLessonDownloaded(lessonId) ?: false

        if (isDownloaded) {
            val saved = db.crosswordLevelDao().getLevelsForLesson(lessonId)
            if (saved.isNotEmpty()) {
                DownloadedLessonRefresh.launch("crossword:$lessonId") {
                    fetchLevelEntities(lessonId)?.let { replaceSavedLevels(lessonId, it) }
                }
                return saved
                .map { entity ->
                    CrosswordGameLevel(
                        id = entity.level,
                        level = entity.level,
                        words = entity.words.split(",")
                    )
                }
            }
        }

        return fetchLevelsRemote(lessonId)
    }

    /** Fetch and save levels to Room only when the student downloads the lesson. */
    suspend fun syncLevels(lessonId: String): Boolean {
        val levels = fetchLevelEntities(lessonId) ?: return false
        if (levels.isEmpty()) return false
        replaceSavedLevels(lessonId, levels)
        return true
    }

    /** The lesson's levels from the server, or null when it can't be reached. */
    private suspend fun fetchLevelEntities(lessonId: String): List<CrosswordLevelEntity>? {
        val snapshot = try {
            firestore
                .collection("lessons")
                .document(PracticeLessons.contentId(lessonId))
                .collection("game")
                .orderBy("level")
                .get(Source.SERVER)
                .await()
        } catch (e: Exception) {
            return null
        }

        return snapshot.documents.map { doc ->
            CrosswordLevelEntity(
                id = doc.id,
                lessonId = lessonId,
                level = doc.getLong("level")?.toInt() ?: 0,
                words = (doc.get("words") as? List<*>)?.joinToString(",") ?: "",
                order = doc.getLong("order")?.toInt() ?: 0
            )
        }
    }

    /** Replace saved levels: web edits create new IDs, so inserting alone would leave old levels behind. */
    private suspend fun replaceSavedLevels(lessonId: String, levels: List<CrosswordLevelEntity>) {
        db.withTransaction {
            db.crosswordLevelDao().deleteByLessonId(lessonId)
            db.crosswordLevelDao().insertAll(levels)
        }
    }

    /** Fetch undownloaded levels from Firestore for display without saving them to Room. */
    suspend fun fetchLevelsRemote(lessonId: String): List<CrosswordGameLevel> {
        val levels = try {
            val snapshot = firestore
                .collection("lessons")
                .document(PracticeLessons.contentId(lessonId))
                .collection("game")
                .orderBy("level")
                .get(Source.SERVER)
                .await()

            snapshot.documents.map { doc ->
                CrosswordGameLevel(
                    id = doc.getLong("level")?.toInt() ?: 0,
                    level = doc.getLong("level")?.toInt() ?: 0,
                    words = (doc.get("words") as? List<*>)?.map { it.toString() } ?: emptyList()
                )
            }
        } catch (e: Exception) {
            // Offline and not downloaded: levels read earlier while the app is open still work.
            return sessionLevels[lessonId].orEmpty()
        }
        if (levels.isNotEmpty()) sessionLevels[lessonId] = levels
        return levels
    }

    /** Reconcile only the captured student's state. Legacy lesson-only rows are never read. */
    suspend fun syncProgress(
        lessonId: String,
        progressDocument: com.google.firebase.firestore.DocumentSnapshot? = null,
        progressLoaded: Boolean = false,
        cacheOnly: Boolean = false
    ) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val key = ProgressSession.gameKey(lessonId, uid)
        fun ownsSession() = FirebaseAuth.getInstance().currentUser?.uid == uid &&
            ProgressSession.gameKey(lessonId, uid) == key
        try {
            val doc = if (progressLoaded) progressDocument else firestore
                .collection("users").document(uid).collection("assignedLessons").document(lessonId)
                .get(if (cacheOnly) Source.CACHE else Source.DEFAULT).await()
            if (!ownsSession() || doc == null) return
            if (doc.reference.path != "users/$uid/assignedLessons/$lessonId") return
            val authoritative = !doc.metadata.isFromCache && !doc.metadata.hasPendingWrites()
            val completed = doc.getLong("gameScore")?.toInt()?.coerceAtLeast(0) ?: 0
            val words = if (doc.getLong("resume.game.level")?.toInt() == completed + 1) {
                (doc.get("resume.game.foundWords") as? List<*>)?.filterIsInstance<String>().orEmpty()
            } else emptyList()
            db.withTransaction {
                if (!ownsSession()) return@withTransaction
                val existing = db.crosswordProgressDao().getProgress(key)
                db.crosswordProgressDao().saveProgress(
                    ph.basaya.app.data.progress.CrosswordProgressState.reconcile(
                        key, existing, completed, words, authoritative
                    )
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("CrosswordRepository", "Could not load saved game progress for $lessonId", e)
        }
    }
}
