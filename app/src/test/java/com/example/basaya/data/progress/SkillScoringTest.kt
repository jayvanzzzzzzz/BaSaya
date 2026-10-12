package com.example.basaya.data.progress

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
}
