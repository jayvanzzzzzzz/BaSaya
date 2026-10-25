package ph.basaya.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lectures")
data class LectureEntity(
    @PrimaryKey val lessonId: String,
    val title: String,
    val pagesJson: String
)