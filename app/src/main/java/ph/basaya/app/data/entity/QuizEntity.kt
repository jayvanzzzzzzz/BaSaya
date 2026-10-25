package ph.basaya.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quizzes")
data class QuizEntity(
    @PrimaryKey val lessonId: String,
    val title: String,
    val questionsJson: String
)