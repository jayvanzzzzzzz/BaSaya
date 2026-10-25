package ph.basaya.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "crossword_levels")
data class CrosswordLevelEntity(
    @PrimaryKey
    val id: String,
    val lessonId: String,
    val level: Int,
    val words: String,
    val order: Int
)