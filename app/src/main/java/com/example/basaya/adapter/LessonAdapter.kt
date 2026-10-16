package com.example.basaya.adapter

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.view.animation.AccelerateDecelerateInterpolator
import com.google.android.material.card.MaterialCardView
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.basaya.R
import com.example.basaya.data.entity.LessonEntity
import com.example.basaya.data.schedule.LessonSchedule
import com.example.basaya.data.schedule.LessonSchedule.Availability

class LessonAdapter(
    private var lessonList: List<LessonEntity>,
    private val onItemClick: (LessonEntity) -> Unit,
    private val onDownloadClick: (LessonEntity, onResult: (Boolean) -> Unit) -> Unit,
    private val onRemoveDownloadClick: (LessonEntity, onResult: (Boolean) -> Unit) -> Unit
) : RecyclerView.Adapter<LessonAdapter.LessonViewHolder>() {

    private var highlightQuery: String = ""

    // Tracks which lessonIds currently have a download in flight, so the
    // spinner survives view recycling and re-binds correctly.
    private val downloadingIds = mutableSetOf<String>()

    // Tracks which lessonIds are currently being removed, so the 3-dot
    // menu can't be double-tapped mid-delete.
    private val removingIds = mutableSetOf<String>()

    // Lessons from a tapped notification; each glows once, the next time its card is shown.
    private val glowIds = mutableSetOf<String>()

    fun highlight(lessonIds: Collection<String>) {
        glowIds.clear()
        glowIds.addAll(lessonIds)
        lessonList.forEachIndexed { index, lesson ->
            if (lesson.id in glowIds) notifyItemChanged(index)
        }
    }

    inner class LessonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        private val tvSchedule: TextView = itemView.findViewById(R.id.tvSchedule)
        private val tvProgress: TextView = itemView.findViewById(R.id.tvProgress)
        private val tvDifficultyLabel: TextView = itemView.findViewById(R.id.tvDifficultyLabel)
        private val vDifficultyDot: View = itemView.findViewById(R.id.vDifficultyDot)
        private val tvLessonEyebrow: TextView = itemView.findViewById(R.id.tvLessonEyebrow)
        private val progressLesson: ProgressBar = itemView.findViewById(R.id.progressLesson)
        private val tvProgressPercent: TextView = itemView.findViewById(R.id.tvProgressPercent)
        private val tvCheckDownload: TextView = itemView.findViewById(R.id.tvCheckDownload)
        private val ibDownload: ImageView = itemView.findViewById(R.id.ibDownload)
        private val pbDownload: ProgressBar = itemView.findViewById(R.id.pbDownload)
        private val ibMoreOptions: ImageButton = itemView.findViewById(R.id.ibMoreOptions)
        private val downloadButton: LinearLayout = itemView.findViewById(R.id.downloadButton)

        fun bind(lesson: LessonEntity) {
            tvTitle.text = buildHighlightedTitle(tvTitle.context, lesson.title, highlightQuery)
            tvDescription.text = lesson.description
            tvDifficultyLabel.text = lesson.difficulty.replaceFirstChar { it.uppercase() }
            tvLessonEyebrow.text = if (lesson.order > 0) {
                itemView.context.getString(R.string.lesson_card_eyebrow_numbered, lesson.order)
            } else {
                itemView.context.getString(R.string.lesson_card_eyebrow)
            }

            bindProgressBadge(tvProgress, lesson)
            bindProgressBar(progressLesson, tvProgressPercent, lesson)
            bindDownloadState(lesson)
            bindSchedule(tvSchedule, lesson)
            // Locked lessons stay visible but dimmed; tapping explains why.
            itemView.alpha = if (LessonSchedule.canOpen(itemView.context, lesson)) 1f else 0.6f
            if (glowIds.remove(lesson.id)) playGlow(itemView as MaterialCardView)

            val difficultyColor = ContextCompat.getColor(
                itemView.context,
                when (lesson.difficulty.lowercase()) {
                    "beginner"     -> R.color.lesson_diff_beginner
                    "intermediate" -> R.color.lesson_diff_intermediate
                    "advanced"     -> R.color.lesson_diff_advanced
                    else           -> R.color.lesson_diff_default
                }
            )

            vDifficultyDot.backgroundTintList = ColorStateList.valueOf(difficultyColor)
            tvDifficultyLabel.setTextColor(difficultyColor)

            itemView.setOnClickListener { onItemClick(lesson) }

            downloadButton.setOnClickListener {
                if (lesson.isDownloaded || downloadingIds.contains(lesson.id)) return@setOnClickListener

                downloadingIds.add(lesson.id)
                bindDownloadState(lesson)

                onDownloadClick(lesson) { success ->
                    downloadingIds.remove(lesson.id)

                    if (success) {
                        markDownloaded(lesson.id)
                    } else {
                        bindDownloadState(lesson)
                    }
                }
            }

            ibMoreOptions.setOnClickListener {
                if (removingIds.contains(lesson.id)) return@setOnClickListener
                showRemoveDownloadMenu(lesson)
            }
        }

        private fun showRemoveDownloadMenu(lesson: LessonEntity) {
            val popup = PopupMenu(itemView.context, ibMoreOptions)
            popup.menu.add("Alisin ang Download")

            popup.setOnMenuItemClickListener {
                removingIds.add(lesson.id)

                onRemoveDownloadClick(lesson) { success ->
                    removingIds.remove(lesson.id)

                    if (success) {
                        markRemoved(lesson.id)
                    } else {
                        bindDownloadState(lesson)
                    }
                }
                true
            }

            popup.show()
        }

        private fun bindDownloadState(lesson: LessonEntity) {
            val isDownloading = downloadingIds.contains(lesson.id)

            when {
                isDownloading -> {
                    ibDownload.visibility = View.GONE
                    pbDownload.visibility = View.VISIBLE
                    ibMoreOptions.visibility = View.GONE
                    tvCheckDownload.text = "Dina-download..."
                }
                lesson.isDownloaded -> {
                    ibDownload.visibility = View.VISIBLE
                    pbDownload.visibility = View.GONE
                    ibDownload.setImageResource(R.drawable.ic_check_download)
                    ibMoreOptions.visibility = View.VISIBLE
                    tvCheckDownload.text = "Na-download na"
                }
                else -> {
                    ibDownload.visibility = View.VISIBLE
                    pbDownload.visibility = View.GONE
                    ibDownload.setImageResource(R.drawable.ic_download)
                    ibMoreOptions.visibility = View.GONE
                    tvCheckDownload.text = "I-download"
                }
            }
        }
    }

    private fun bindProgressBadge(tvProgress: TextView, lesson: LessonEntity) {
        val context = tvProgress.context

        val lectureFinished = lesson.lectureFinished
        // A game or Gawain the teacher skipped is not part of the lesson: it never counts as
        // "started", and it never blocks the lesson from being complete.
        val gameFinished = lesson.gameFinished && !lesson.gameSkipped
        val gameDone = lesson.gameFinished || lesson.gameSkipped
        val activityFinished = lesson.activityFinished && !lesson.activitySkipped
        val activityDone = lesson.activityFinished || lesson.activitySkipped
        val quizFinished = lesson.quizFinished

        val anyFinished = lectureFinished || gameFinished || activityFinished || quizFinished
        val allFinished = lectureFinished && gameDone && activityDone && quizFinished

        val foregroundRes = when {
            allFinished -> {
                tvProgress.text = "Tapos na"
                tvProgress.setBackgroundResource(R.drawable.bg_lesson_status_done)
                tvProgress.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_status_check, 0, 0, 0
                )
                R.color.lesson_status_done_fg
            }
            anyFinished -> {
                tvProgress.text = "Nasa progreso"
                tvProgress.setBackgroundResource(R.drawable.bg_lesson_status_progress)
                tvProgress.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_status_progress, 0, 0, 0
                )
                R.color.lesson_status_progress_fg
            }
            else -> {
                tvProgress.text = "Hindi pa nasisimulan"
                tvProgress.setBackgroundResource(R.drawable.bg_lesson_status_new)
                tvProgress.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_status_locked, 0, 0, 0
                )
                R.color.lesson_status_new_fg
            }
        }

        // Status icons are white vectors; tint them to match the soft badge foreground.
        val foreground = ContextCompat.getColor(context, foregroundRes)
        tvProgress.setTextColor(foreground)
        TextViewCompat.setCompoundDrawableTintList(tvProgress, ColorStateList.valueOf(foreground))
        tvProgress.compoundDrawablePadding = (5 * context.resources.displayMetrics.density).toInt()
    }

    /** About 2.5 s of a gold-and-violet glowing outline and a gentle pulse, then back to normal. */
    private fun playGlow(card: MaterialCardView) {
        val context = card.context
        val density = context.resources.displayMetrics.density
        val originalStroke = card.strokeColorStateList
        val originalWidth = card.strokeWidth
        val gold = ContextCompat.getColor(context, R.color.tb_ring)
        val violet = ContextCompat.getColor(context, R.color.tb_end)

        card.strokeWidth = (3 * density).toInt()
        val glow = ValueAnimator.ofArgb(gold, violet, gold).apply {
            duration = 850
            repeatCount = 2
            addUpdateListener { card.setStrokeColor(ColorStateList.valueOf(it.animatedValue as Int)) }
        }
        val pulse = ObjectAnimator.ofPropertyValuesHolder(
            card,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.035f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.035f, 1f)
        ).apply {
            duration = 850
            repeatCount = 2
            interpolator = AccelerateDecelerateInterpolator()
        }
        AnimatorSet().apply {
            playTogether(glow, pulse)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    card.strokeWidth = originalWidth
                    card.setStrokeColor(originalStroke)
                    card.scaleX = 1f
                    card.scaleY = 1f
                }
            })
            start()
        }
    }

    /** "Bubukas: …" before the open date, "Deadline: …" while open, "Sarado na" after the deadline. */
    private fun bindSchedule(tvSchedule: TextView, lesson: LessonEntity) {
        val context = tvSchedule.context
        val availability = LessonSchedule.availability(context, lesson)

        val text: String
        val backgroundRes: Int
        val foregroundRes: Int
        val iconRes: Int
        when {
            availability == Availability.NOT_YET_OPEN || availability == Availability.NEEDS_INTERNET -> {
                text = context.getString(R.string.schedule_opens, LessonSchedule.format(lesson.openAt))
                backgroundRes = R.drawable.bg_lesson_status_new
                foregroundRes = R.color.lesson_status_new_fg
                iconRes = R.drawable.ic_status_locked
            }
            availability == Availability.CLOSED -> {
                text = context.getString(R.string.schedule_closed, LessonSchedule.format(lesson.dueAt))
                backgroundRes = R.drawable.bg_lesson_status_new
                foregroundRes = R.color.lesson_diff_advanced
                iconRes = R.drawable.ic_status_locked
            }
            lesson.dueAt != null -> {
                text = context.getString(R.string.schedule_due, LessonSchedule.format(lesson.dueAt))
                backgroundRes = R.drawable.bg_lesson_status_progress
                foregroundRes = R.color.lesson_status_progress_fg
                iconRes = 0
            }
            else -> {
                tvSchedule.visibility = View.GONE
                return
            }
        }

        val foreground = ContextCompat.getColor(context, foregroundRes)
        tvSchedule.visibility = View.VISIBLE
        tvSchedule.text = text
        tvSchedule.setBackgroundResource(backgroundRes)
        tvSchedule.setTextColor(foreground)
        tvSchedule.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0)
        TextViewCompat.setCompoundDrawableTintList(tvSchedule, ColorStateList.valueOf(foreground))
    }

    private fun bindProgressBar(progressLesson: ProgressBar, tvProgressPercent: TextView, lesson: LessonEntity) {
        // 4 content types per lesson, minus the game and/or Gawain when the teacher skipped them
        val parts = listOfNotNull(
            lesson.lectureFinished,
            lesson.gameFinished.takeUnless { lesson.gameSkipped },
            lesson.activityFinished.takeUnless { lesson.activitySkipped },
            lesson.quizFinished
        )
        val completedCount = parts.count { it }

        val percent = (completedCount * 100) / parts.size

        progressLesson.progress = percent
        tvProgressPercent.text = "$percent%"
    }

    private fun buildHighlightedTitle(context: Context, title: String, query: String): CharSequence {
        if (query.isBlank()) return title

        val startIndex = title.indexOf(query, ignoreCase = true)
        if (startIndex == -1) return title

        val endIndex = startIndex + query.length

        return SpannableString(title).apply {
            setSpan(
                BackgroundColorSpan(ContextCompat.getColor(context, R.color.lesson_search_highlight)),
                startIndex,
                endIndex,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LessonViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lesson, parent, false)
        return LessonViewHolder(view)
    }

    override fun onBindViewHolder(holder: LessonViewHolder, position: Int) {
        holder.bind(lessonList[position])
    }

    override fun getItemCount() = lessonList.size

    fun updateList(newList: List<LessonEntity>, query: String = "") {
        lessonList = newList
        highlightQuery = query
        notifyDataSetChanged()
    }

    /** Flips a single lesson's isDownloaded flag to true and refreshes just that row. */
    private fun markDownloaded(lessonId: String) {
        val index = lessonList.indexOfFirst { it.id == lessonId }
        if (index == -1) return

        lessonList = lessonList.toMutableList().apply {
            this[index] = this[index].copy(isDownloaded = true)
        }
        notifyItemChanged(index)
    }

    /** Flips a single lesson's isDownloaded flag back to false and refreshes just that row. */
    private fun markRemoved(lessonId: String) {
        val index = lessonList.indexOfFirst { it.id == lessonId }
        if (index == -1) return

        lessonList = lessonList.toMutableList().apply {
            this[index] = this[index].copy(isDownloaded = false)
        }
        notifyItemChanged(index)
    }
}