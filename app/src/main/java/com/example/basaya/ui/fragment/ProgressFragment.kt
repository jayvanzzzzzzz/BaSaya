package com.example.basaya.ui.fragment

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.basaya.R
import com.example.basaya.data.database.AppDatabase
import com.example.basaya.data.entity.LessonEntity
import com.example.basaya.data.progress.LessonCompletion
import com.example.basaya.data.progress.Skill
import com.example.basaya.data.progress.SkillLevel
import com.example.basaya.data.progress.SkillProgressRepository
import com.example.basaya.data.progress.SkillScoring
import com.example.basaya.data.progress.SkillSummary
import com.example.basaya.data.progress.StudentSummary
import com.example.basaya.data.progress.Trend
import com.example.basaya.ui.pronunciation.PronunciationBookView
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * The student's own progress: five separate skill cards (never one combined score).
 * Scores come from [SkillScoring]; see README.md ("Paano kinukwenta ang progreso").
 */
class ProgressFragment : Fragment() {

    /** How each skill looks on its card. */
    private data class SkillStyle(
        @param:StringRes val name: Int,
        @param:StringRes val source: Int,
        @param:DrawableRes val icon: Int,
        @param:ColorRes val color: Int,
        @param:ColorRes val tint: Int
    )

    private val styles = mapOf(
        Skill.PAG_UNAWA to SkillStyle(R.string.pg_skill_unawa, R.string.pg_skill_unawa_sub, R.drawable.ic_book, R.color.pg_unawa, R.color.pg_unawa_tint),
        Skill.TALASALITAAN to SkillStyle(R.string.pg_skill_talasalitaan, R.string.pg_skill_talasalitaan_sub, R.drawable.ic_puzzle, R.color.pg_talasalitaan, R.color.pg_talasalitaan_tint),
        Skill.PAGKILALA to SkillStyle(R.string.pg_skill_pagkilala, R.string.pg_skill_pagkilala_sub, R.drawable.ic_pencil, R.color.pg_pagkilala, R.color.pg_pagkilala_tint),
        Skill.PAGBIGKAS to SkillStyle(R.string.pg_skill_pagbigkas, R.string.pg_skill_pagbigkas_sub, R.drawable.ic_mic, R.color.pg_pagbigkas, R.color.pg_pagbigkas_tint),
        Skill.PAGSUSULIT to SkillStyle(R.string.pg_skill_pagsusulit, R.string.pg_skill_pagsusulit_sub, R.drawable.ic_clipboard_check, R.color.pg_pagsusulit, R.color.pg_pagsusulit_tint)
    )

    /** What one card shows. */
    private data class CardState(
        val percent: Int?,
        val percentText: String,
        @param:StringRes val level: Int,
        @param:ColorRes val levelText: Int,
        @param:ColorRes val levelBg: Int,
        val stars: Int?,
        val trend: Trend?,
        val lessonScores: List<Int>,
        @param:StringRes val message: Int
    )

    private lateinit var buddy: PronunciationBookView
    private lateinit var tvGreeting: TextView
    private lateinit var tvHeroMessage: TextView
    private lateinit var tvLessonsDone: TextView
    private lateinit var lessonRing: View
    private lateinit var progressLessons: CircularProgressIndicator
    private lateinit var skillsContainer: LinearLayout
    private lateinit var wordsCard: View
    private lateinit var wordsContainer: FlexboxLayout

    private var loadJob: Job? = null
    private var allGamesSkipped = false
    /** The student's class lessons (from Room); the only lessons that count. */
    private var classLessons: List<LessonEntity> = emptyList()
    private val density get() = resources.displayMetrics.density
    private fun dp(value: Float) = (value * density).toInt()
    private fun color(@ColorRes res: Int) = ContextCompat.getColor(requireContext(), res)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_progress, container, false)
        buddy = view.findViewById(R.id.buddy)
        tvGreeting = view.findViewById(R.id.tvGreeting)
        tvHeroMessage = view.findViewById(R.id.tvHeroMessage)
        tvLessonsDone = view.findViewById(R.id.tvLessonsDone)
        lessonRing = view.findViewById(R.id.lessonRing)
        progressLessons = view.findViewById(R.id.progressLessons)
        skillsContainer = view.findViewById(R.id.skillsContainer)
        wordsCard = view.findViewById(R.id.wordsCard)
        wordsContainer = view.findViewById(R.id.wordsContainer)
        return view
    }

    // Reload whenever the student comes back, e.g. after finishing an activity.
    override fun onResume() {
        super.onResume()
        if (!isHidden) load()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && isResumed) load()
    }

    private fun load() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            val firestore = FirebaseFirestore.getInstance()
            val allDocs = try {
                firestore.collection("users").document(uid)
                    .collection("assignedLessons").get().await().documents
            } catch (e: Exception) {
                return@launch
            }
            val name = try {
                firestore.collection("users").document(uid).get().await().getString("fullName")
            } catch (e: Exception) {
                null
            } ?: FirebaseAuth.getInstance().currentUser?.displayName

            // Only the lessons of the student's class count (synced into Room by the home screen).
            // Stray assignedLessons documents — old test assignments, a previous class — are ignored.
            classLessons = try {
                AppDatabase.getDatabase(requireContext()).lessonDao().getLessonsForUser(uid)
            } catch (e: Exception) {
                emptyList()
            }
            val docs = LessonCompletion.classDocs(allDocs, classLessons)

            val summary = SkillScoring.summarize(SkillProgressRepository.resultsFrom(docs))
            if (view == null) return@launch

            // Every class lesson has its game skipped by the teacher → there is nothing to measure.
            allGamesSkipped = classLessons.isNotEmpty() && classLessons.all { it.gameSkipped }

            showHero(name, docs, summary)
            showSkills(docs, summary)
            showHardWords(docs)

            // Keep the saved summary (read by the teacher dashboard) in step with what the student sees.
            try {
                SkillProgressRepository().saveSummary(uid, summary, lessonsAssigned = classLessons.size)
            } catch (e: Exception) {
                Log.w("ProgressFragment", "Could not save skill summary", e)
            }
        }
    }

    // ───────────────────────────── Hero ─────────────────────────────

    private fun showHero(name: String?, docs: List<DocumentSnapshot>, summary: StudentSummary) {
        val firstName = name?.trim()?.split(" ")?.firstOrNull()?.takeIf { it.isNotBlank() }
        tvGreeting.text = if (firstName != null) getString(R.string.pg_greeting, firstName) else getString(R.string.pg_greeting_plain)

        val levels = summary.skills.values.map { it.level }
        val hasData = summary.skills.values.any { it.lessons > 0 } || docs.any { it.getBoolean("lectureFinished") == true }
        when {
            summary.needsHelp.isNotEmpty() -> {
                tvHeroMessage.setText(R.string.pg_hero_help)
                buddy.setMood(PronunciationBookView.Mood.CHEER)
            }
            SkillLevel.MAHUSAY in levels -> {
                tvHeroMessage.setText(R.string.pg_hero_great)
                buddy.setMood(PronunciationBookView.Mood.HAPPY)
            }
            hasData -> {
                tvHeroMessage.setText(R.string.pg_hero_going)
                buddy.setMood(PronunciationBookView.Mood.IDLE)
            }
            else -> {
                tvHeroMessage.setText(R.string.pg_hero_start)
                buddy.setMood(PronunciationBookView.Mood.IDLE)
            }
        }

        // Lessons finished: class lessons whose every part is done (Lecture, Laro or skipped,
        // Gawain, Bigkas, Pagsusulit).
        val total = classLessons.size
        val finished = LessonCompletion.finishedCount(docs, classLessons)
        tvLessonsDone.text = getString(R.string.pg_lessons_done, finished, total)
        lessonRing.contentDescription = getString(R.string.pg_lessons_cd, finished, total)
        progressLessons.setProgressCompat(0, false)
        progressLessons.postDelayed({
            if (isAdded) progressLessons.setProgressCompat(if (total > 0) finished * 100 / total else 0, true)
        }, 300)
    }

    // ───────────────────────────── Skill cards ─────────────────────────────

    private fun showSkills(docs: List<DocumentSnapshot>, summary: StudentSummary) {
        skillsContainer.removeAllViews()
        Skill.values().forEachIndexed { index, skill ->
            val skillSummary = summary.skills.getValue(skill)
            val state = if (skill == Skill.PAG_UNAWA && skillSummary.lessons == 0) {
                lectureCompletionState(docs)
            } else if (skill == Skill.TALASALITAAN && skillSummary.lessons == 0 && allGamesSkipped) {
                noGameState()
            } else {
                scoredState(skillSummary)
            }
            val card = layoutInflater.inflate(R.layout.item_skill_progress, skillsContainer, false)
            bindCard(card, styles.getValue(skill), state, index)
            skillsContainer.addView(card)
        }
    }

    private fun scoredState(summary: SkillSummary): CardState {
        val score = summary.score
        if (summary.lessons == 0 || score == null) {
            return CardState(
                null, "--", R.string.pg_level_not_started, R.color.pg_ink_soft, R.color.pg_track,
                stars = 0, trend = null, lessonScores = emptyList(), message = R.string.pg_msg_not_started
            )
        }
        val (level, text, bg, stars, message) = when (summary.level) {
            SkillLevel.MAHUSAY -> Quint(R.string.pg_level_mahusay, R.color.pg_up, R.color.pg_up_tint, 3, R.string.pg_msg_mahusay)
            SkillLevel.UMUUNLAD -> Quint(R.string.pg_level_umuunlad, R.color.pg_unawa, R.color.pg_unawa_tint, 2, R.string.pg_msg_umuunlad)
            SkillLevel.NAGSISIMULA -> Quint(R.string.pg_level_nagsisimula, R.color.pg_pagkilala, R.color.pg_pagkilala_tint, 1, R.string.pg_msg_nagsisimula)
            SkillLevel.KAILANGAN_NG_TULONG -> Quint(R.string.pg_level_tulong, R.color.pg_down, R.color.pg_down_tint, 0, R.string.pg_msg_tulong)
            SkillLevel.KULANG_ANG_DATOS -> Quint(R.string.pg_level_kulang, R.color.pg_ink_soft, R.color.pg_track, 0, R.string.pg_msg_kulang)
        }
        return CardState(score, getString(R.string.pg_percent, score), level, text, bg, stars, summary.trend, summary.lessonScores, message)
    }

    /** Talasalitaan when the teacher skipped the game in every assigned lesson. */
    private fun noGameState() = CardState(
        percent = null,
        percentText = "--",
        level = R.string.pg_level_no_game,
        levelText = R.color.pg_ink_soft,
        levelBg = R.color.pg_track,
        stars = null,
        trend = null,
        lessonScores = emptyList(),
        message = R.string.pg_msg_no_game
    )

    /** Pag-unawa has no score until lectures have comprehension questions: show completion. */
    private fun lectureCompletionState(docs: List<DocumentSnapshot>): CardState {
        // Out of the class's lessons, not the number of progress documents
        val total = classLessons.size
        val finished = docs.count { it.getBoolean("lectureFinished") == true }
        if (total == 0 || finished == 0) {
            return CardState(
                null, "--", R.string.pg_level_not_started, R.color.pg_ink_soft, R.color.pg_track,
                stars = null, trend = null, lessonScores = emptyList(), message = R.string.pg_msg_not_started
            )
        }
        val done = finished == total
        return CardState(
            percent = finished * 100 / total,
            percentText = getString(R.string.pg_fraction, finished, total),
            level = if (done) R.string.pg_level_done else R.string.pg_level_in_progress,
            levelText = if (done) R.color.pg_up else R.color.pg_unawa,
            levelBg = if (done) R.color.pg_up_tint else R.color.pg_unawa_tint,
            stars = null, trend = null, lessonScores = emptyList(),
            message = if (done) R.string.pg_msg_lecture_done else R.string.pg_msg_lecture_progress
        )
    }

    private fun bindCard(card: View, style: SkillStyle, state: CardState, index: Int) {
        val accent = color(style.color)

        card.findViewById<ImageView>(R.id.ivSkillIcon).apply {
            setImageResource(style.icon)
            backgroundTintList = ColorStateList.valueOf(color(style.tint))
            imageTintList = ColorStateList.valueOf(accent)
        }
        card.findViewById<TextView>(R.id.tvSkillName).setText(style.name)
        card.findViewById<TextView>(R.id.tvSkillSource).setText(style.source)
        card.findViewById<TextView>(R.id.tvSkillPercent).apply {
            text = state.percentText
            setTextColor(if (state.percent == null) color(R.color.pg_ink_soft) else accent)
        }
        card.findViewById<TextView>(R.id.tvSkillLevel).apply {
            setText(state.level)
            setTextColor(color(state.levelText))
            backgroundTintList = ColorStateList.valueOf(color(state.levelBg))
        }
        card.findViewById<TextView>(R.id.tvSkillTrend).apply {
            when (state.trend) {
                Trend.UP -> show(R.string.pg_trend_up, R.color.pg_up, R.color.pg_up_tint)
                Trend.DOWN -> show(R.string.pg_trend_down, R.color.pg_down, R.color.pg_down_tint)
                else -> visibility = View.GONE
            }
        }
        card.findViewById<TextView>(R.id.tvSkillMessage).setText(state.message)

        // Stars: earned ones turn gold and pop in
        val starsRow = card.findViewById<View>(R.id.starsRow)
        val stars = listOf<ImageView>(card.findViewById(R.id.star1), card.findViewById(R.id.star2), card.findViewById(R.id.star3))
        if (state.stars == null) {
            starsRow.visibility = View.INVISIBLE
        } else {
            stars.forEachIndexed { i, star ->
                val earned = i < state.stars
                star.imageTintList = ColorStateList.valueOf(color(if (earned) R.color.pg_star_on else R.color.pg_star_off))
                star.scaleX = 0f
                star.scaleY = 0f
                star.animate().scaleX(1f).scaleY(1f)
                    .setStartDelay(350L + index * 90L + i * 120L)
                    .setDuration(380)
                    .setInterpolator(OvershootInterpolator(if (earned) 3f else 1.2f))
                    .start()
            }
        }

        // Score bar fills up
        val track = card.findViewById<View>(R.id.scoreTrack)
        val fill = card.findViewById<View>(R.id.scoreFill)
        fill.backgroundTintList = ColorStateList.valueOf(accent)
        val target = (state.percent ?: 0).coerceIn(0, 100)
        track.post {
            ValueAnimator.ofInt(0, track.width * target / 100).apply {
                startDelay = 250L + index * 90L
                duration = 700
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    fill.layoutParams = fill.layoutParams.apply { width = it.animatedValue as Int }
                }
                start()
            }
        }

        bindLessonBars(card.findViewById(R.id.lessonBars), state.lessonScores, accent, index)

        // Card rises in, one after another
        card.alpha = 0f
        card.translationY = dp(24f).toFloat()
        card.animate().alpha(1f).translationY(0f)
            .setStartDelay(index * 90L)
            .setDuration(380)
            .setInterpolator(DecelerateInterpolator())
            .start()

        val starCount = state.stars ?: 0
        card.contentDescription = getString(
            R.string.pg_skill_cd, getString(style.name), state.percentText, getString(state.level), starCount
        )
    }

    private fun TextView.show(@StringRes text: Int, @ColorRes fg: Int, @ColorRes bg: Int) {
        visibility = View.VISIBLE
        setText(text)
        setTextColor(color(fg))
        backgroundTintList = ColorStateList.valueOf(color(bg))
    }

    /** Mini chart: one bar per lesson (latest 6), newest in full color. */
    private fun bindLessonBars(container: LinearLayout, scores: List<Int>, accent: Int, index: Int) {
        container.removeAllViews()
        val recent = scores.takeLast(6)
        val maxHeight = dp(28f)
        recent.forEachIndexed { i, score ->
            val bar = View(requireContext()).apply {
                setBackgroundResource(R.drawable.pg_bar)
                backgroundTintList = ColorStateList.valueOf(accent)
                alpha = if (i == recent.lastIndex) 1f else 0.45f
                layoutParams = LinearLayout.LayoutParams(dp(7f), maxOf(dp(4f), maxHeight * score / 100)).apply {
                    marginStart = if (i == 0) 0 else dp(3f)
                }
                pivotY = maxHeight.toFloat()
                scaleY = 0f
            }
            container.addView(bar)
            bar.post { bar.pivotY = bar.height.toFloat() }
            bar.animate().scaleY(1f).setStartDelay(500L + index * 90L + i * 50L)
                .setDuration(320).setInterpolator(OvershootInterpolator(1.5f)).start()
        }
    }

    // ───────────────────────────── Words to practice ─────────────────────────────

    /** Pronunciation words the student missed at least once, most recent lessons first. */
    private fun showHardWords(docs: List<DocumentSnapshot>) {
        val words = docs
            .mapNotNull { doc ->
                val record = (doc.get("skills") as? Map<*, *>)?.get(Skill.PAGBIGKAS.key) as? Map<*, *>
                val list = (record?.get("hardWords") as? List<*>)?.filterIsInstance<String>().orEmpty()
                val time = (record?.get("completedAt") as? Timestamp)?.toDate()?.time ?: 0L
                if (list.isEmpty()) null else time to list
            }
            .sortedByDescending { it.first }
            .flatMap { it.second }
            .distinctBy { it.lowercase() }
            .take(10)

        wordsContainer.removeAllViews()
        if (words.isEmpty()) {
            wordsCard.visibility = View.GONE
            return
        }
        wordsCard.visibility = View.VISIBLE
        words.forEachIndexed { i, word ->
            val chip = TextView(requireContext()).apply {
                text = word
                typeface = ResourcesCompat.getFont(requireContext(), R.font.be_vietnam_bold)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(color(R.color.pg_pagbigkas))
                setBackgroundResource(R.drawable.pg_round)
                backgroundTintList = ColorStateList.valueOf(color(R.color.pg_pagbigkas_tint))
                setPadding(dp(14f), dp(6f), dp(14f), dp(7f))
                layoutParams = FlexboxLayout.LayoutParams(
                    FlexboxLayout.LayoutParams.WRAP_CONTENT,
                    FlexboxLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dp(4f), dp(8f), dp(4f)) }
                scaleX = 0f
                scaleY = 0f
            }
            wordsContainer.addView(chip)
            chip.animate().scaleX(1f).scaleY(1f).setStartDelay(700L + i * 60L)
                .setDuration(300).setInterpolator(OvershootInterpolator(2.5f)).start()
        }
    }

    private data class Quint(val a: Int, val b: Int, val c: Int, val d: Int, val e: Int)
}
