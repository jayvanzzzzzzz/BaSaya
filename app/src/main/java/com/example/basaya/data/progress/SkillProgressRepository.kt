package com.example.basaya.data.progress

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Stores first-try results per skill per lesson and keeps the student's skill summary
 * up to date.
 *
 * Per lesson:  users/{uid}/assignedLessons/{lessonId}.skills.{skillKey}
 *              = { correct, attempts, items, completedAt, ...extras }
 * Summary:     users/{uid}.skillSummary  (read by the Progress screen and teacher dashboard)
 *
 * See README.md ("Paano kinukwenta ang progreso") for how the summary is computed.
 */
class SkillProgressRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private fun lessonDoc(uid: String, lessonId: String) =
        firestore.collection("users").document(uid).collection("assignedLessons").document(lessonId)

    /** Saves one lesson's result for a skill (replaces any earlier result for that lesson). */
    suspend fun recordResult(
        uid: String,
        lessonId: String,
        skill: Skill,
        correct: Int,
        attempts: Int,
        items: Int,
        extras: Map<String, Any> = emptyMap()
    ) {
        val record = mapOf(
            "correct" to correct,
            "attempts" to attempts,
            "items" to items,
            "completedAt" to FieldValue.serverTimestamp()
        ) + extras
        lessonDoc(uid, lessonId)
            .set(mapOf("skills" to mapOf(skill.key to record)), SetOptions.merge())
            .await()
    }

    /**
     * Adds one finished crossword level to the lesson's Talasalitaan record. Levels of a lesson
     * are played separately, so the counts accumulate:
     * correct = words found, attempts = words found + wrong swipes, items = words found.
     */
    suspend fun addCrosswordLevel(uid: String, lessonId: String, wordsFound: Int, wrongTries: Int) {
        val record = mapOf(
            "correct" to FieldValue.increment(wordsFound.toLong()),
            "attempts" to FieldValue.increment((wordsFound + wrongTries).toLong()),
            "items" to FieldValue.increment(wordsFound.toLong()),
            "wrongTries" to FieldValue.increment(wrongTries.toLong()),
            "levels" to FieldValue.increment(1),
            "completedAt" to FieldValue.serverTimestamp()
        )
        lessonDoc(uid, lessonId)
            .set(mapOf("skills" to mapOf(Skill.TALASALITAAN.key to record)), SetOptions.merge())
            .await()
    }

    /** Recomputes the summary from every assigned lesson and saves it on the user document. */
    suspend fun refreshSummary(uid: String): StudentSummary {
        val allDocs = firestore.collection("users").document(uid)
            .collection("assignedLessons").get().await().documents

        // Only the lessons of the student's class count (see LessonCompletion).
        val classId = firestore.collection("users").document(uid).get().await().getString("classId")
        val classLessonIds = if (classId.isNullOrBlank()) {
            emptySet()
        } else {
            firestore.collection("classes").document(classId).collection("classLessons")
                .get().await().documents
                .map { link -> link.getString("lessonId")?.takeIf { it.isNotBlank() } ?: link.id }
                .toSet()
        }
        val docs = allDocs.filter { it.id in classLessonIds }

        val summary = SkillScoring.summarize(resultsFrom(docs))
        saveSummary(uid, summary, lessonsAssigned = classLessonIds.size)
        return summary
    }

    suspend fun saveSummary(uid: String, summary: StudentSummary, lessonsAssigned: Int) {
        val skills = summary.skills.values.associate { s ->
            s.skill.key to mapOf(
                "score" to s.score,
                "level" to s.level.name,
                "trend" to s.trend?.name,
                "lessons" to s.lessons,
                "items" to s.items,
                "needsHelp" to s.needsHelp,
                "lessonScores" to s.lessonScores
            )
        }
        val data = mapOf(
            "skillSummary" to mapOf(
                "needsHelp" to summary.needsHelp.map { it.key },
                "lessonsAssigned" to lessonsAssigned,
                "skills" to skills,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        // mergeFields replaces skillSummary as a whole and leaves the rest of the user doc alone.
        firestore.collection("users").document(uid)
            .set(data, SetOptions.mergeFields("skillSummary"))
            .await()
    }

    companion object {
        private const val TAG = "SkillProgress"

        // Outlives the activity that finished the lesson, so the summary still updates
        // after the screen closes.
        private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun refreshInBackground(uid: String) {
            backgroundScope.launch {
                try {
                    SkillProgressRepository().refreshSummary(uid)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not refresh skill summary", e)
                }
            }
        }

        /** Reads every lesson's skill records. Lessons without a record are simply absent. */
        fun resultsFrom(docs: List<DocumentSnapshot>): Map<Skill, List<LessonSkillResult>> {
            val results = Skill.values().associateWith { mutableListOf<LessonSkillResult>() }

            docs.forEach { doc ->
                val skills = doc.get("skills") as? Map<*, *>
                Skill.values().forEach { skill ->
                    val record = skills?.get(skill.key) as? Map<*, *>
                    val parsed = record?.let { parseRecord(doc.id, it) }
                        ?: if (skill == Skill.PAGSUSULIT) legacyQuiz(doc) else null
                    parsed?.let { results.getValue(skill).add(it) }
                }
            }
            return results
        }

        private fun parseRecord(lessonId: String, record: Map<*, *>): LessonSkillResult? {
            val attempts = (record["attempts"] as? Number)?.toInt() ?: return null
            return LessonSkillResult(
                lessonId = lessonId,
                correct = (record["correct"] as? Number)?.toInt() ?: 0,
                attempts = attempts,
                items = (record["items"] as? Number)?.toInt() ?: attempts,
                completedAtMillis = millis(record["completedAt"])
            )
        }

        /**
         * Quizzes taken before skill tracking already saved quizScore/quizTotal with the same
         * meaning, so they still count. Older Gawain/game/pronunciation scores used different
         * rules (retries counted as correct) and are not mixed in.
         */
        private fun legacyQuiz(doc: DocumentSnapshot): LessonSkillResult? {
            val score = doc.getLong("quizScore") ?: return null
            val total = doc.getLong("quizTotal") ?: return null
            if (total <= 0) return null
            return LessonSkillResult(
                lessonId = doc.id,
                correct = score.toInt(),
                attempts = total.toInt(),
                items = total.toInt(),
                completedAtMillis = millis(doc.get("quizCompletedAt"))
            )
        }

        // A server timestamp still pending (offline write) reads as null: treat it as "now".
        private fun millis(value: Any?): Long =
            (value as? Timestamp)?.toDate()?.time ?: System.currentTimeMillis()
    }
}
