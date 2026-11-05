package ph.basaya.app.data.progress

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot

object ProgressSession {
    private lateinit var prefs: SharedPreferences

    fun initialize(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("class_progress_session", Context.MODE_PRIVATE)
    }

    fun bind(uid: String, profile: DocumentSnapshot) {
        if (!profile.exists()) return
        prefs.edit().putString(uid + ":class", profile.getString("classId").orEmpty())
            .putString(uid + ":session", profile.getString("progressSession").orEmpty()).apply()
    }

    fun stamp(uid: String, fields: Map<String, Any>): Map<String, Any> = fields + mapOf(
        "classId" to prefs.getString(uid + ":class", "").orEmpty(),
        "progressSession" to prefs.getString(uid + ":session", "").orEmpty()
    )

    /** Local work belongs to a student and assessment record, never just a lesson. */
    fun gameKey(lessonId: String): String {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return "signed-out:legacy:$lessonId"
        return gameKey(lessonId, uid)
    }

    fun gameKey(lessonId: String, uid: String): String {
        require(uid.isNotBlank()) { "Crossword progress requires a signed-in student" }
        val session = prefs.getString(uid + ":session", "").orEmpty().ifBlank { "legacy" }
        return "$uid:$session:$lessonId"
    }
}
