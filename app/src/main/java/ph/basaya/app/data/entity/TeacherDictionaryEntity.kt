package ph.basaya.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "teacher_dictionary")
data class TeacherDictionaryEntity(
    @PrimaryKey val key: String,
    val word: String,
    val accent: String,
    val sensesJson: String,
    val synonymsJson: String,
    val hidden: Boolean,
    val fixedSyllables: String
)
