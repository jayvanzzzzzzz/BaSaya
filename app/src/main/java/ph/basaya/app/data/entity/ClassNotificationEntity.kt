package ph.basaya.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A "new lesson" notification from classes/{classId}/notifications (written by notify-api). */
@Entity(tableName = "class_notifications")
data class ClassNotificationEntity(
    @PrimaryKey
    val id: String,
    val classId: String,
    val title: String,
    val body: String,
    /** Comma-separated lesson ids. */
    val lessonIds: String,
    /** When students may see it: when it was sent, or the lesson's open date if later. Epoch millis. */
    val visibleAt: Long
) {
    fun lessonIdList(): List<String> = lessonIds.split(',').filter { it.isNotBlank() }
}
