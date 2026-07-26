package com.example.basaya.adapter

import android.graphics.Color
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.basaya.R
import com.example.basaya.data.entity.LessonEntity

class LessonAdapter(
    private var lessonList: List<LessonEntity>,
    private val onItemClick: (LessonEntity) -> Unit
) : RecyclerView.Adapter<LessonAdapter.LessonViewHolder>() {

    private val lessonImages = intArrayOf(
        R.drawable.lesson_bg_2,
        R.drawable.lesson_bg_1,
        R.drawable.lesson_bg_3
    )

    private var highlightQuery: String = ""

    inner class LessonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        private val tvProgress: TextView = itemView.findViewById(R.id.tvProgress)
        private val tvDifficultyLabel: TextView = itemView.findViewById(R.id.tvDifficultyLabel)
        private val vDifficultyDot: View = itemView.findViewById(R.id.vDifficultyDot)
        private val ivLessonImage: ImageView = itemView.findViewById(R.id.ivLessonImage)
        private val progressLesson: ProgressBar = itemView.findViewById(R.id.progressLesson)
        private val tvProgressPercent: TextView = itemView.findViewById(R.id.tvProgressPercent)

        fun bind(lesson: LessonEntity) {
            tvTitle.text = buildHighlightedTitle(lesson.title, highlightQuery)
            tvDescription.text = lesson.description
            tvDifficultyLabel.text = lesson.difficulty.replaceFirstChar { it.uppercase() }

            bindProgressBadge(tvProgress, lesson)
            bindProgressBar(progressLesson, tvProgressPercent, lesson)


            val imageIndex = Math.floorMod(lesson.id.hashCode(), lessonImages.size)
            ivLessonImage.setImageResource(lessonImages[imageIndex])

            val (dotColor, labelColor) = when (lesson.difficulty.lowercase()) {
                "beginner"     -> "#34C47C" to "#34C47C"
                "intermediate" -> "#F5A623" to "#F5A623"
                "advanced"     -> "#E8445A" to "#E8445A"
                else           -> "#4A90E2" to "#4A90E2"
            }

            vDifficultyDot.backgroundTintList =
                android.content.res.ColorStateList.valueOf(Color.parseColor(dotColor))
            tvDifficultyLabel.setTextColor(Color.parseColor(labelColor))

            itemView.setOnClickListener { onItemClick(lesson) }

        }
    }

    private fun bindProgressBadge(tvProgress: TextView, lesson: LessonEntity) {
        val context = tvProgress.context

        val lectureFinished = lesson.lectureFinished
        val gameFinished = lesson.gameFinished
        val activityFinished = lesson.activityFinished
        val quizFinished = lesson.quizFinished

        val anyFinished = lectureFinished || gameFinished || activityFinished || quizFinished
        val allFinished = lectureFinished && gameFinished && activityFinished && quizFinished

        when {
            allFinished -> {
                tvProgress.text = "Tapos na"
                tvProgress.setBackgroundResource(R.drawable.bg_status_completed)
                tvProgress.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_status_check, 0, 0, 0
                )
            }
            anyFinished -> {
                tvProgress.text = "Nasa progreso"
                tvProgress.setBackgroundResource(R.drawable.bg_status_progress)
                tvProgress.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_status_progress, 0, 0, 0
                )
            }
            else -> {
                tvProgress.text = "Hindi pa nasisimulan"
                tvProgress.setBackgroundResource(R.drawable.bg_status_not_started)
                tvProgress.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_status_locked, 0, 0, 0
                )
            }
        }

        tvProgress.compoundDrawablePadding = (6 * context.resources.displayMetrics.density).toInt()
    }

    private fun bindProgressBar(progressLesson: ProgressBar, tvProgressPercent: TextView, lesson: LessonEntity) {
        val completedCount = listOf(
            lesson.lectureFinished,
            lesson.gameFinished,
            lesson.activityFinished,
            lesson.quizFinished
        ).count { it }

        val percent = (completedCount * 100) / 4  // 4 total content types per lesson

        progressLesson.progress = percent
        tvProgressPercent.text = "$percent%"
    }

    private fun buildHighlightedTitle(title: String, query: String): CharSequence {
        if (query.isBlank()) return title

        val startIndex = title.indexOf(query, ignoreCase = true)
        if (startIndex == -1) return title

        val endIndex = startIndex + query.length

        return SpannableString(title).apply {
            setSpan(
                BackgroundColorSpan(Color.parseColor("#FFF176")),
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
}