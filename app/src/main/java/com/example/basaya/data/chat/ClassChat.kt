package com.example.basaya.data.chat

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Class chat: one group chat per class (classes/{classId}/messages), shared with the
 * teacher's web app. The Firestore rules ("Class chat" in BaSaya-web/firestore.rules)
 * enforce what the app also checks here: up to [MAX_LENGTH] characters, one message every
 * [COOLDOWN_MS], only while the teacher keeps the chat open, and deleting your own
 * message only within [DELETE_WINDOW_MS].
 *
 * Reads are kept low: only the latest [PAGE] messages are live, older ones load on
 * request, Firestore's offline cache keeps what was already downloaded, and the unread
 * dot comes from chat/state (one small doc).
 */
object ClassChat {

    const val PAGE = 30L
    const val MAX_LENGTH = 300
    const val COOLDOWN_MS = 5_000L
    const val DELETE_WINDOW_MS = 5 * 60_000L

    // Filipino surname prefixes that belong to the surname: "Juan Dela Cruz" → "Dela Cruz, Juan".
    private val SURNAME_PARTICLES = setOf(
        "de", "del", "dela", "delos", "la", "las", "los", "san", "santa", "sta", "sto", "santo",
        "di", "da", "dos", "van", "von"
    )
    private val NAME_SUFFIXES = setOf("jr", "sr", "ii", "iii", "iv")
    // "Gng. Santos" reads wrong reordered, so names that start with a title stay as written.
    private val NAME_TITLES = setOf("gng", "g", "bb", "mr", "mrs", "ms", "dr", "sir", "maam", "ma'am", "teacher")

    /**
     * A teacher's name as "Last, First" (students are already saved that way). Teachers only
     * have one display name ("Juan Dela Cruz"), so it is reordered when shown; names that
     * already have a comma, or are one word, stay as they are. Same as lastFirst() in
     * BaSaya-web/js/chat.js.
     */
    fun lastFirst(name: String): String {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty() || ',' in clean) return clean
        val words = clean.split(" ").toMutableList()
        fun key(word: String) = word.lowercase().replace(".", "")
        if (key(words.first()) in NAME_TITLES) return clean
        val suffix = if (words.size > 2 && key(words.last()) in NAME_SUFFIXES) words.removeAt(words.lastIndex) else ""
        if (words.size < 2) return clean
        var start = words.lastIndex
        while (start > 1 && key(words[start - 1]) in SURNAME_PARTICLES) start--
        val first = (words.subList(0, start) + suffix).filter { it.isNotEmpty() }.joinToString(" ")
        return "${words.subList(start, words.size).joinToString(" ")}, $first"
    }

    // How a teacher wants students to address them (users/{uid}.title); "" = name only.
    private val TEACHER_TITLES = mapOf("maam" to "Ma'am", "sir" to "Sir")

    /**
     * The teacher as students see them: "Ma'am Reyes" / "Sir Dela Cruz" when a title is picked,
     * else "Reyes, Ana" ([lastFirst]). A name that already starts with a title stays as written.
     * Same as teacherName() in BaSaya-web/js/chat.js.
     */
    fun teacherName(name: String, title: String?): String {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        val label = TEACHER_TITLES[title.orEmpty()]
        val titled = clean.split(" ").first().lowercase().replace(".", "") in NAME_TITLES
        if (label == null || clean.isEmpty() || titled) return lastFirst(clean)
        return "$label ${lastFirst(clean).substringBefore(",").trim()}"
    }

    /** The class's teacher and their title, so their chat messages can show "Ma'am Reyes". */
    data class Teacher(val id: String, val title: String)

    suspend fun classTeacher(classId: String): Teacher? = try {
        val teacherId = classRef(classId).get().await().getString("teacherId").orEmpty()
        if (teacherId.isEmpty()) null else {
            val snap = db.collection("users").document(teacherId).get().await()
            if (snap.exists()) Teacher(teacherId, snap.getString("title").orEmpty()) else null
        }
    } catch (e: Exception) {
        android.util.Log.w("ClassChat", "Couldn't read the class's teacher", e)
        null
    }

    data class Message(
        val id: String,
        val text: String,
        val senderId: String,
        val senderName: String,
        val isTeacher: Boolean,
        val sentAt: Long,
        val deleted: Boolean,
        val deletedByTeacher: Boolean,
        val pending: Boolean,
        val snapshot: DocumentSnapshot
    )

    data class State(
        val open: Boolean = true,
        val lastAt: Long = 0L,
        val lastText: String = "",
        val lastSenderName: String = "",
        val lastIsTeacher: Boolean = false
    )

    private val db get() = FirebaseFirestore.getInstance()
    private fun classRef(classId: String) = db.collection("classes").document(classId)
    private fun messages(classId: String) = classRef(classId).collection("messages")
    private fun stateRef(classId: String) = classRef(classId).collection("chat").document("state")

    private fun Timestamp?.millis() = this?.toDate()?.time

    private fun toMessage(doc: DocumentSnapshot): Message {
        val sentAt = doc.getTimestamp("sentAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        return Message(
            id = doc.id,
            text = doc.getString("text").orEmpty(),
            senderId = doc.getString("senderId").orEmpty(),
            senderName = doc.getString("senderName").orEmpty(),
            isTeacher = doc.getString("role") == "teacher",
            sentAt = sentAt.millis() ?: System.currentTimeMillis(),
            deleted = doc.getBoolean("deleted") == true,
            deletedByTeacher = doc.getString("deletedBy") == "teacher",
            pending = doc.metadata.hasPendingWrites(),
            snapshot = doc
        )
    }

    /** Live latest [PAGE] messages, oldest first; `full` = there may be older ones. */
    fun watchRecent(
        classId: String,
        onChange: (messages: List<Message>, full: Boolean) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration =
        messages(classId)
            .orderBy("sentAt", Query.Direction.DESCENDING)
            .limit(PAGE)
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snap, error ->
                if (error != null) return@addSnapshotListener onError(error)
                val docs = snap?.documents.orEmpty()
                onChange(docs.map(::toMessage).reversed(), docs.size.toLong() == PAGE)
            }

    /** The [PAGE] messages before [oldest], oldest first; `more` = there may be even older. */
    suspend fun loadOlder(classId: String, oldest: Message): Pair<List<Message>, Boolean> {
        val snap = messages(classId)
            .orderBy("sentAt", Query.Direction.DESCENDING)
            .startAfter(oldest.snapshot)
            .limit(PAGE)
            .get()
            .await()
        return snap.documents.map(::toMessage).reversed() to (snap.size().toLong() == PAGE)
    }

    fun watchState(classId: String, onChange: (State) -> Unit): ListenerRegistration =
        stateRef(classId).addSnapshotListener { snap, _ ->
            if (snap == null || !snap.exists()) return@addSnapshotListener onChange(State())
            onChange(
                State(
                    open = snap.getBoolean("open") != false,
                    lastAt = snap.getTimestamp(
                        "lastAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).millis() ?: 0L,
                    lastText = snap.getString("lastText").orEmpty(),
                    lastSenderName = snap.getString("lastSenderName").orEmpty(),
                    lastIsTeacher = snap.getString("lastRole") == "teacher"
                )
            )
        }

    /** The student's name as the class roster has it (the rules check messages against it). */
    suspend fun rosterName(classId: String, uid: String): String =
        classRef(classId).collection("students").document(uid).get().await()
            .getString("fullName").orEmpty()

    /**
     * Sends with one batch: the message, this student's last-send time (for the 5-second
     * limit) and the class's last-message summary. Throws if the rules refuse it.
     */
    suspend fun send(classId: String, uid: String, name: String, text: String) {
        val body = text.trim()
        require(body.isNotEmpty() && body.length <= MAX_LENGTH)
        val batch = db.batch()
        batch.set(
            messages(classId).document(),
            mapOf(
                "text" to body,
                "senderId" to uid,
                "senderName" to name,
                "role" to "student",
                "sentAt" to FieldValue.serverTimestamp()
            )
        )
        batch.set(
            classRef(classId).collection("chatSenders").document(uid),
            mapOf("lastSentAt" to FieldValue.serverTimestamp())
        )
        batch.set(
            stateRef(classId),
            mapOf(
                "lastAt" to FieldValue.serverTimestamp(),
                "lastText" to body.take(120),
                "lastSenderName" to name,
                "lastRole" to "student"
            ),
            SetOptions.merge()
        )
        batch.commit().await()
    }

    fun canDelete(message: Message, uid: String, now: Long = System.currentTimeMillis()) =
        !message.deleted && !message.pending && message.senderId == uid &&
            now - message.sentAt < DELETE_WINDOW_MS

    /** Deletes the student's own message; it stays in place as "deleted". */
    suspend fun deleteOwn(classId: String, messageId: String) {
        messages(classId).document(messageId)
            .update(mapOf("text" to "", "deleted" to true, "deletedBy" to "sender"))
            .await()
    }

    // ---------- Members (the (i) button in the chat header) ----------

    data class Member(val id: String, val name: String)

    /** The class's teacher (null when none is assigned) and its students A–Z. */
    data class Members(val teacher: Member?, val students: List<Member>)

    /**
     * Same as loadClassMembers() in BaSaya-web/js/classMembers.js: students are on the roster
     * as "Last, First", the teacher's display name is shown the same way, and a class whose
     * teacher account is gone (removed by the admin) has no teacher.
     */
    suspend fun members(classId: String): Members {
        val classSnap = classRef(classId).get().await()
        val roster = classRef(classId).collection("students").get().await()

        val teacherId = classSnap.getString("teacherId").orEmpty()
        val teacher = if (teacherId.isEmpty()) null else try {
            val snap = db.collection("users").document(teacherId).get().await()
            if (snap.exists() && snap.getString("role") == "teacher") {
                Member(teacherId, teacherName(snap.getString("displayName").orEmpty(), snap.getString("title")).ifEmpty { "Guro" })
            } else null
        } catch (e: Exception) {
            android.util.Log.w("ClassChat", "Couldn't read the class's teacher", e)
            null
        }

        val collator = java.text.Collator.getInstance().apply { strength = java.text.Collator.PRIMARY }
        val students = roster.documents
            .map { doc ->
                val name = doc.getString("fullName").orEmpty().trim().trim(',').trim()
                Member(doc.id, name.ifEmpty { "—" })
            }
            .sortedWith { a, b -> collator.compare(a.name, b.name) }
        return Members(teacher, students)
    }

    // ---------- Read state: kept on the phone (free) ----------

    private const val PREFS = "class_chat"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun lastRead(context: Context, classId: String): Long =
        prefs(context).getLong("read_$classId", 0L)

    fun markRead(context: Context, classId: String, at: Long) {
        if (at > lastRead(context, classId)) prefs(context).edit().putLong("read_$classId", at).apply()
    }

    /** Something new from someone else since the student last looked. */
    fun isUnread(context: Context, classId: String, state: State, myName: String): Boolean =
        state.lastAt > lastRead(context, classId) &&
            (state.lastIsTeacher || state.lastSenderName != myName)

    // ---------- Is the chat on screen? (skip push notifications while it is) ----------

    @Volatile
    var visibleClassId: String? = null
}
