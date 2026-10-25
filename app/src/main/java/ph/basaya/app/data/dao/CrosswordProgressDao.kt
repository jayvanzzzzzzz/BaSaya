package ph.basaya.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ph.basaya.app.data.entity.CrosswordProgress

@Dao
interface CrosswordProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: CrosswordProgress)

    @Query("SELECT * FROM game_progress WHERE lessonId = :lessonId")
    suspend fun getProgress(lessonId: String): CrosswordProgress?

    /** Drops progress past level 1 for these lessons (only used when Firestore no longer has it). */
    @Query("DELETE FROM game_progress WHERE lessonId IN (:lessonIds) AND currentLevel > 1")
    suspend fun deleteAdvancedProgress(lessonIds: List<String>)
}