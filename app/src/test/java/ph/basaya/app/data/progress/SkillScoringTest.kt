package ph.basaya.app.data.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillScoringTest {

    private fun result(id: String, percent: Int, items: Int, order: Long) =
        LessonSkillResult(id, correct = percent * items, attempts = 100 * items, items = items, completedAtMillis = order)

    @Test
    fun readmeExampleRecencyWeightedAverage() {
        // The worked example from README.md: 40%, 60%, 75%, 90% with 10, 8, 10, 12 words.
        val summary = SkillScoring.summarizeSkill(
            Skill.PAGBIGKAS,
            listOf(
                result("l1", 40, 10, 1),
                result("l2", 60, 8, 2),
                result("l3", 75, 10, 3),
                result("l4", 90, 12, 4)
            )
        )
        assertEquals(71, summary.score)
        assertEquals(SkillLevel.UMUUNLAD, summary.level)
        assertEquals(Trend.UP, summary.trend)
        assertEquals(4, summary.lessons)
        assertFalse(summary.needsHelp)
    }

    @Test
    fun orderComesFromCompletionTimeNotListOrder() {
        val oldestFirst = SkillScoring.summarizeSkill(
            Skill.PAGSUSULIT,
            listOf(result("a", 40, 10, 1), result("b", 90, 10, 2))
        )
        val shuffled = SkillScoring.summarizeSkill(
            Skill.PAGSUSULIT,
            listOf(result("b", 90, 10, 2), result("a", 40, 10, 1))
        )
        assertEquals(oldestFirst.score, shuffled.score)
        assertEquals(listOf(40, 90), shuffled.lessonScores)
    }

    @Test
    fun noResultsMeansNoDataNotZero() {
        val summary = SkillScoring.summarizeSkill(Skill.PAG_UNAWA, emptyList())
        assertNull(summary.score)
        assertEquals(SkillLevel.KULANG_ANG_DATOS, summary.level)
    }

    @Test
    fun oneSmallLessonIsNotEnoughEvidence() {
        val summary = SkillScoring.summarizeSkill(Skill.PAGKILALA, listOf(result("a", 20, 3, 1)))
        assertEquals(20, summary.score)
        assertEquals(SkillLevel.KULANG_ANG_DATOS, summary.level)
    }

    @Test
    fun oneLargeLessonIsEnoughEvidence() {
        val summary = SkillScoring.summarizeSkill(Skill.PAGSUSULIT, listOf(result("a", 90, 10, 1)))
        assertEquals(SkillLevel.MAHUSAY, summary.level)
    }

    @Test
    fun levelBoundaries() {
        assertEquals(SkillLevel.MAHUSAY, SkillScoring.levelFor(85))
        assertEquals(SkillLevel.UMUUNLAD, SkillScoring.levelFor(84))
        assertEquals(SkillLevel.UMUUNLAD, SkillScoring.levelFor(70))
        assertEquals(SkillLevel.NAGSISIMULA, SkillScoring.levelFor(69))
        assertEquals(SkillLevel.NAGSISIMULA, SkillScoring.levelFor(50))
        assertEquals(SkillLevel.KAILANGAN_NG_TULONG, SkillScoring.levelFor(49))
    }

    @Test
    fun twoLowLessonsInARowNeedHelp() {
        val summary = SkillScoring.summarizeSkill(
            Skill.PAGBIGKAS,
            listOf(result("a", 90, 10, 1), result("b", 55, 10, 2), result("c", 50, 10, 3))
        )
        assertTrue(summary.needsHelp)
        assertEquals(Trend.DOWN, summary.trend)
    }

    @Test
    fun trendNeedsThreeLessons() {
        assertNull(SkillScoring.trendOf(listOf(40.0, 90.0)))
        assertEquals(Trend.STEADY, SkillScoring.trendOf(listOf(70.0, 72.0, 75.0)))
    }

    @Test
    fun skillsAreScoredSeparately() {
        // Strong in the quiz, struggling in pronunciation: each skill keeps its own level,
        // and only the weak one is flagged.
        val summary = SkillScoring.summarize(
            mapOf(
                Skill.PAGSUSULIT to listOf(result("a", 90, 10, 1), result("b", 95, 10, 2)),
                Skill.PAGBIGKAS to listOf(result("a", 40, 10, 1), result("b", 35, 10, 2))
            )
        )
        assertEquals(SkillLevel.MAHUSAY, summary.skills.getValue(Skill.PAGSUSULIT).level)
        assertEquals(SkillLevel.KAILANGAN_NG_TULONG, summary.skills.getValue(Skill.PAGBIGKAS).level)
        assertEquals(listOf(Skill.PAGBIGKAS), summary.needsHelp)
        assertNull(summary.skills.getValue(Skill.TALASALITAAN).score)
    }

    private val items = mapOf(
        Skill.TALASALITAAN to 5, Skill.PAGKILALA to 4, Skill.PAGBIGKAS to 6, Skill.PAGSUSULIT to 10
    )

    @Test
    fun partsLeftUndoneAtTheDeadlineCountAsZero() {
        val results = mapOf(
            Skill.PAGSUSULIT to listOf(result("a", 80, 1, 100)),
            Skill.TALASALITAAN to listOf(result("b", 75, 1, 200)) // half-played crossword
        )
        val finished = mapOf(
            "a" to FinishedParts(quiz = true),
            "c" to FinishedParts(pronunciation = true) // finished before skill tracking: no record
        )
        val lessons = listOf(
            HeldLesson("a", dueAt = 500, gameSkipped = false, activitySkipped = false, items = items),
            HeldLesson("b", dueAt = 600, gameSkipped = false, activitySkipped = true, items = items),
            HeldLesson("c", dueAt = 700, gameSkipped = false, activitySkipped = false,
                items = items + (Skill.PAGKILALA to 0)),
            HeldLesson("d", dueAt = 2000, gameSkipped = false, activitySkipped = false, items = items), // still open
            HeldLesson("e", dueAt = null, gameSkipped = false, activitySkipped = false, items = items) // no deadline
        )

        val out = SkillScoring.withMissed(results, finished, lessons, now = 1000)
        fun ids(skill: Skill) = out.getValue(skill).map { "${it.lessonId}:${it.correct}/${it.attempts}${if (it.missed) "*" else ""}" }

        // b: the half-played crossword's 4 unplayed words are added as wrong
        assertEquals(listOf("b:75/104*", "a:0/5*", "c:0/5*"), ids(Skill.TALASALITAAN))
        assertEquals(listOf("a:0/4*"), ids(Skill.PAGKILALA)) // b skipped, c has no Gawain
        assertEquals(listOf("a:0/6*", "b:0/6*"), ids(Skill.PAGBIGKAS))
        assertEquals(listOf("a:80/100", "b:0/10*", "c:0/10*"), ids(Skill.PAGSUSULIT))
        assertTrue(out.getValue(Skill.PAG_UNAWA).isEmpty())
        assertEquals(500L, out.getValue(Skill.PAGKILALA).single().completedAtMillis)
    }

    @Test
    fun unfinishedPartsKeepWhatWasRightAndTheRestIsWrong() {
        // 3 of 5 questions answered with 2 right; 4 of 6 Gawain pages with 3 right.
        val lessons = listOf(
            HeldLesson("a", 500, false, false, mapOf(Skill.PAGSUSULIT to 5, Skill.PAGKILALA to 6, Skill.PAGBIGKAS to 4))
        )
        val finished = mapOf(
            "a" to FinishedParts(
                correctSoFar = mapOf(Skill.PAGSUSULIT to 2, Skill.PAGKILALA to 3, Skill.PAGBIGKAS to 9)
            )
        )
        val out = SkillScoring.withMissed(emptyMap(), finished, lessons, now = 1000)
        assertEquals(2, out.getValue(Skill.PAGSUSULIT).single().correct)
        assertEquals(5, out.getValue(Skill.PAGSUSULIT).single().attempts)
        assertEquals(3, out.getValue(Skill.PAGKILALA).single().correct)
        assertEquals(6, out.getValue(Skill.PAGKILALA).single().attempts)
        // A saved count above the item count (content edited) is capped
        assertEquals(4, out.getValue(Skill.PAGBIGKAS).single().correct)
    }

    @Test
    fun missedQuizPullsTheScoreDown() {
        val results = mapOf(Skill.PAGSUSULIT to listOf(result("a", 80, 10, 100)))
        val lessons = listOf(HeldLesson("b", 600, false, false, items))
        val summary = SkillScoring.summarize(SkillScoring.withMissed(results, emptyMap(), lessons, now = 1000))
        // 80% × 10 × 0.85 and 0% × 10 → 68 / 18.5 = 37%
        assertEquals(37, summary.skills.getValue(Skill.PAGSUSULIT).score)
    }
}
