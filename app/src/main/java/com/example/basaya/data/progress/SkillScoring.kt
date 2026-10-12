package com.example.basaya.data.progress

import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/** The five skills a student is monitored on. [key] is the Firestore field name. */
enum class Skill(val key: String) {
    PAG_UNAWA("pagUnawa"),         // Pag-unawa sa binasa — lecture comprehension check
    TALASALITAAN("talasalitaan"),  // Talasalitaan — crossword game
    PAGKILALA("pagkilala"),        // Pagkilala sa salita — Gawain (practice)
    PAGBIGKAS("pagbigkas"),        // Pagbigkas — pronunciation
    PAGSUSULIT("pagsusulit")       // Pagsusulit — quiz
}

enum class SkillLevel { MAHUSAY, UMUUNLAD, NAGSISIMULA, KAILANGAN_NG_TULONG, KULANG_ANG_DATOS }

enum class Trend { UP, DOWN, STEADY }

/**
 * One lesson's result in one skill.
 * score = correct / attempts; [items] (questions, words, pages) is the lesson's weight.
 */
data class LessonSkillResult(
    val lessonId: String,
    val correct: Int,
    val attempts: Int,
    val items: Int,
    val completedAtMillis: Long
) {
    val score: Double get() = if (attempts > 0) correct * 100.0 / attempts else 0.0
}

data class SkillSummary(
    val skill: Skill,
    /** Recency-weighted score 0..100, null when the skill has no results yet. */
    val score: Int?,
    val level: SkillLevel,
    /** null until there are at least 3 lessons to compare. */
    val trend: Trend?,
    val lessons: Int,
    val items: Int,
    /** True when the last two lessons were both below [SkillScoring.HELP_THRESHOLD]. */
    val needsHelp: Boolean,
    /** Per-lesson scores, oldest first. */
    val lessonScores: List<Int>
)

/**
 * A student's five skills, each scored on its own. There is deliberately no combined score:
 * the teacher needs to see *which* skill (e.g. Pagbigkas vs. Pagsusulit) a student finds hard.
 */
data class StudentSummary(
    val skills: Map<Skill, SkillSummary>,
    val needsHelp: List<Skill>
)

/**
 * Combines a student's per-lesson results into one score per skill.
 * The method is documented in README.md ("Paano kinukwenta ang progreso").
 */
object SkillScoring {

    /** Each older lesson counts 15% less than the one after it. */
    const val RECENCY_DECAY = 0.85
    /** A level is only shown after this many lessons… */
    const val MIN_LESSONS = 2
    /** …or this many answered items, whichever comes first. */
    const val MIN_ITEMS = 10
    /** Points of change between recent and earlier lessons that count as a trend. */
    const val TREND_THRESHOLD = 10.0
    /** Two lessons in a row below this flag the skill for the teacher. */
    const val HELP_THRESHOLD = 60.0

    fun levelFor(score: Int): SkillLevel = when {
        score >= 85 -> SkillLevel.MAHUSAY
        score >= 70 -> SkillLevel.UMUUNLAD
        score >= 50 -> SkillLevel.NAGSISIMULA
        else -> SkillLevel.KAILANGAN_NG_TULONG
    }

    fun summarize(results: Map<Skill, List<LessonSkillResult>>): StudentSummary {
        val skills = Skill.values().associateWith { skill -> summarizeSkill(skill, results[skill].orEmpty()) }
        return StudentSummary(
            skills = skills,
            needsHelp = skills.values.filter { it.needsHelp }.map { it.skill }
        )
    }

    fun summarizeSkill(skill: Skill, results: List<LessonSkillResult>): SkillSummary {
        // Lessons not attempted are "no data", never 0%.
        val done = results.filter { it.attempts > 0 }.sortedBy { it.completedAtMillis }
        if (done.isEmpty()) {
            return SkillSummary(skill, null, SkillLevel.KULANG_ANG_DATOS, null, 0, 0, false, emptyList())
        }

        // Weighted average: weight = items × 0.85^(lessons since this one)
        var weightedSum = 0.0
        var weightTotal = 0.0
        done.forEachIndexed { index, result ->
            val age = done.lastIndex - index
            val weight = max(result.items, 1) * RECENCY_DECAY.pow(age)
            weightedSum += result.score * weight
            weightTotal += weight
        }
        val score = (weightedSum / weightTotal).roundToInt()

        val items = done.sumOf { it.items }
        val enoughEvidence = done.size >= MIN_LESSONS || items >= MIN_ITEMS
        val lessonScores = done.map { it.score }
        val lastTwo = lessonScores.takeLast(2)

        return SkillSummary(
            skill = skill,
            score = score,
            level = if (enoughEvidence) levelFor(score) else SkillLevel.KULANG_ANG_DATOS,
            trend = trendOf(lessonScores),
            lessons = done.size,
            items = items,
            needsHelp = lastTwo.size == 2 && lastTwo.all { it < HELP_THRESHOLD },
            lessonScores = lessonScores.map { it.roundToInt() }
        )
    }

    /** Average of the last 2 lessons vs. the (up to) 2 lessons before them. */
    fun trendOf(scores: List<Double>): Trend? {
        if (scores.size < 3) return null
        val recent = scores.takeLast(2).average()
        val before = scores.dropLast(2).takeLast(2).average()
        return when {
            recent - before > TREND_THRESHOLD -> Trend.UP
            before - recent > TREND_THRESHOLD -> Trend.DOWN
            else -> Trend.STEADY
        }
    }
}
