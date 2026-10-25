package ph.basaya.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_progress")
data class CrosswordProgress(
    @PrimaryKey
    val lessonId: String,
    val currentLevel: Int?,
    val completedLevel: Int = 0,
    val foundWords: String = ""
)