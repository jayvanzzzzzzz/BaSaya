package ph.basaya.app.data.repository

import android.content.Context
import ph.basaya.app.data.database.AppDatabase
import ph.basaya.app.data.entity.CrosswordLevelEntity
import ph.basaya.app.data.model.CrosswordGameLevel
import androidx.room.withTransaction
import ph.basaya.app.data.progress.PracticeLessons
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import ph.basaya.app.data.entity.CrosswordProgress
import com.google.firebase.auth.FirebaseAuth
import kotlin.Float
import java.util.concurrent.ConcurrentHashMap

class CrosswordRepository(private val context: Context) {

    private companion object {
        /**
         * Levels of lessons that aren't downloaded, kept in memory (never in Room) while the
         * app is open, so "Next level" still works if the connection drops mid-game.
         */
        val sessionLevels = ConcurrentHashMap<String, List<CrosswordGameLevel>>()
    }

    private val db by lazy { AppDatabase.getDatabase(context) }
    private val firestore = FirebaseFirestore.getInstance()

    /**
     * Room-first, Firestore-fallback for DISPLAY purposes only.
     * The fallback does NOT write to Room — only a downloaded lesson's copy is kept locally.
     */
    suspend fun getLevels(lessonId: String): List<CrosswordGameLevel> {
        val isDownloaded = db.lessonDao().isLessonDownloaded(lessonId) ?: false

        if (isDownloaded) {
            // When online, refresh the downloaded copy so level edits made in basaya-web reach
            // the student (Gawain and quiz do the same). Offline, the saved copy is used as before.
            fetchLevelEntities(lessonId)?.let { replaceSavedLevels(lessonId, it) }
            return db.crosswordLevelDao().getLevelsForLesson(lessonId)
                .map { entity ->
                    CrosswordGameLevel(
                        id = entity.level,
                        level = entity.level,
                        words = entity.words.split(",")
                    )
                }
        }

        return fetchLevelsRemote(lessonId)
    }

    /**
     * Explicit download: fetches from Firestore and persists to Room.
     * Only called from the lesson Download button (LessonRepository.downloadLesson).
     */
    suspend fun syncLevels(lessonId: String) {
        val levels = fetchLevelEntities(lessonId) ?: return // Offline — leave whatever's already cached alone
        replaceSavedLevels(lessonId, levels)
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

    /**
     * The saved levels become exactly these. basaya-web gives the levels new ids on every
     * save, so only inserting would keep the old levels next to the new ones.
     */
    private suspend fun replaceSavedLevels(lessonId: String, levels: List<CrosswordLevelEntity>) {
        db.withTransaction {
            db.crosswordLevelDao().deleteByLessonId(lessonId)
            db.crosswordLevelDao().insertAll(levels)
        }
    }

    /**
     * Fetches levels directly from Firestore for DISPLAY ONLY — does not
     * write to Room. Used as the fallback inside getLevels() when this
     * lesson hasn't been downloaded yet.
     */
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

    /**
     * Only writes progress from Firestore if there's no local progress yet
     * (e.g. first time opening this lesson's game on this device).
     * Never overwrites existing local progress — that would wipe out
     * foundWords for a level the student is mid-way through.
     *
     * NOTE: this is the student's own progress state, not lesson content —
     * it's kept separate from the download-only content caching rule above,
     * since gameplay can't function without somewhere to persist it.
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

        }
    }
}