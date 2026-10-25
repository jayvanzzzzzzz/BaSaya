package ph.basaya.app.data.progress

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

/**
 * How many items each scored part of a lesson has, read from its content
 * (lessons/{id}/game|activity|pronunciation|quiz). A part missed at the deadline counts as a 0
 * weighted by these ([SkillScoring.withMissed]). Same counts as the web's js/lessonItems.js:
 * crossword words (all levels), Gawain pages, Bigkas words and quiz questions.
 */
object LessonItems {

    private val cache = ConcurrentHashMap<String, Map<Skill, Int>>()

    /** The counts, or an empty map when the content can't be read (then nothing counts as missed). */
    suspend fun counts(lessonId: String, firestore: FirebaseFirestore = FirebaseFirestore.getInstance()): Map<Skill, Int> {
        cache[lessonId]?.let { return it }
        return try {
            coroutineScope {
                val lesson = firestore.collection("lessons").document(PracticeLessons.contentId(lessonId))
                val game = async { lesson.collection("game").get().await() }
                val activity = async { lesson.collection("activity").get().await() }
                val pronunciation = async { lesson.collection("pronunciation").get().await() }
                val quiz = async { lesson.collection("quiz").get().await() }

                val gameWords = game.await().documents
                    .filter { it.id != "status" }
                    .sumOf { level -> strings(level.get("words")).size }
                mapOf(
                    Skill.TALASALITAAN to gameWords,
                    Skill.PAGKILALA to listSize(contentOf(activity.await())?.get("pages")),
                    Skill.PAGBIGKAS to strings(contentOf(pronunciation.await())?.get("words")).size,
                    Skill.PAGSUSULIT to listSize(contentOf(quiz.await())?.get("questions"))
                )
            }.also { counts ->
                // All zero is likely an empty offline cache: read again next time.
                if (counts.values.any { it > 0 }) cache[lessonId] = counts
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /** The part's content document ("content" if there is one), never the skip "status" one. */
    private fun contentOf(snapshot: QuerySnapshot): DocumentSnapshot? {
        val docs = snapshot.documents.filter { it.id != "status" }
        return docs.firstOrNull { it.id == "content" } ?: docs.firstOrNull()
    }

    private fun listSize(value: Any?): Int = (value as? List<*>)?.size ?: 0

    private fun strings(value: Any?): List<String> =
        (value as? List<*>).orEmpty().mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }
}
