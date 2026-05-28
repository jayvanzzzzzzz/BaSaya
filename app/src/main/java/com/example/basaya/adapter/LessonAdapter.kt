package com.example.basaya.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.basaya.R
import com.example.basaya.model.Lesson
import kotlinx.coroutines.NonCancellable.parent

class LessonAdapter(private val lessonList: List<Lesson>, private val onItemClick: (Lesson) -> Unit): RecyclerView.Adapter<LessonAdapter.LessonViewHolder>() {

    inner class LessonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val ivLessonImage: ImageView = itemView.findViewById(R.id.ivLessonImage)
        private val tvTitle: TextView        = itemView.findViewById(R.id.tvTitle)
        private val tvDescription: TextView  = itemView.findViewById(R.id.tvDescription)
        private val tvDifficulty: TextView = itemView.findViewById(R.id.tvDifficulty)
        private val tvDifficultyLabel: TextView = itemView.findViewById(R.id.tvDifficultyLabel)
        private val vDifficultyDot: View     = itemView.findViewById(R.id.vDifficultyDot)

        fun bind(lesson: Lesson) {
            tvTitle.text       = lesson.title
            tvDescription.text = lesson.description
            tvDifficulty.text  = lesson.difficulty.uppercase()
            tvDifficultyLabel.text = lesson.difficulty.replaceFirstChar { it.uppercase() }

            if (lesson.imageRes != 0) {
                ivLessonImage.setImageResource(lesson.imageRes)
            }

            // Color-code by difficulty
            val (dotColor, labelColor) = when (lesson.difficulty.lowercase()) {
                "beginner"     -> "#34C47C" to "#34C47C"
                "intermediate" -> "#F5A623" to "#F5A623"
                "advanced"     -> "#E8445A" to "#E8445A"
                else           -> "#4A90E2" to "#4A90E2"
            }

            val dot   = Color.parseColor(dotColor)
            val label = Color.parseColor(labelColor)
            vDifficultyDot.backgroundTintList =
                android.content.res.ColorStateList.valueOf(dot)
            tvDifficultyLabel.setTextColor(label)

            itemView.setOnClickListener { onItemClick(lesson) }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): LessonViewHolder {

        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lesson, parent, false)
        return LessonViewHolder(view)

    }

    override fun onBindViewHolder(
        holder: LessonViewHolder,
        position: Int
    ) {
        val lesson = lessonList[position]
        holder.bind(lesson)
    }

    override fun getItemCount(): Int {
         return lessonList.size
    }

}
