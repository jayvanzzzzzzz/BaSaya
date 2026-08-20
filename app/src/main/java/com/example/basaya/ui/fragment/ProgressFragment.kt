package com.example.basaya.ui.fragment

import android.os.Bundle
import android.widget.TextView
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.example.basaya.R
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProgressFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_progress, container, false)

        val tvComprehensionPercent = view.findViewById<TextView>(R.id.tvComprehensionPercent)
        val tvComprehensionStatus = view.findViewById<TextView>(R.id.tvComprehensionStatus)
        val progressComprehension = view.findViewById<LinearProgressIndicator>(R.id.progressComprehension)

        val tvVocabPercent = view.findViewById<TextView>(R.id.tvVocabPercent)
        val tvVocabStatus = view.findViewById<TextView>(R.id.tvVocabStatus)
        val progressVocab = view.findViewById<LinearProgressIndicator>(R.id.progressVocab)

        val tvWordRecognitionPercent = view.findViewById<TextView>(R.id.tvWordRecognitionPercent)
        val tvWordRecognitionStatus = view.findViewById<TextView>(R.id.tvWordRecognitionStatus)
        val progressWordRecognition = view.findViewById<LinearProgressIndicator>(R.id.progressWordRecognition)

        val tvPronunciationPercent = view.findViewById<TextView>(R.id.tvPronunciationPercent)
        val tvPronunciationStatus = view.findViewById<TextView>(R.id.tvPronunciationStatus)
        val progressPronunciation = view.findViewById<LinearProgressIndicator>(R.id.progressPronunciation)

        val tvQuizPercent = view.findViewById<TextView>(R.id.tvQuizPercent)
        val tvQuizStatus = view.findViewById<TextView>(R.id.tvQuizStatus)
        val progressQuiz = view.findViewById<LinearProgressIndicator>(R.id.progressQuiz)

        val tvLessonProgressFraction = view.findViewById<TextView>(R.id.tvLessonProgressFraction)
        val tvLessonProgressPercent = view.findViewById<TextView>(R.id.tvLessonProgressPercent)
        val progressLessonOverall = view.findViewById<LinearProgressIndicator>(R.id.progressLessonOverall)

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return view

        viewLifecycleOwner.lifecycleScope.launch {
            val snapshot = try {
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(uid)
                    .collection("assignedLessons")
                    .get()
                    .await()
            } catch (e: Exception) {
                return@launch
            }

            val docs = snapshot.documents
            val totalLessons = docs.size

            // --- Overall "lessons finished" summary (top card) ---
            val finishedLessons = docs.count { it.getBoolean("quizFinished") == true }
            tvLessonProgressFraction.text = "$finishedLessons/$totalLessons aralin natapos"
            val overallPercent = if (totalLessons > 0) (finishedLessons * 100 / totalLessons) else 0
            tvLessonProgressPercent.text = "$overallPercent%"
            progressLessonOverall.progress = overallPercent

            // --- Pag-unawa sa Binasa (lecture) ---
            // Lecture has no score field, only a finished flag — so once fully
            // finished it just reports "Tapos na" instead of a score tier.
            applyCompletionSkill(
                docs = docs, totalLessons = totalLessons, finishedField = "lectureFinished",
                fractionView = tvComprehensionPercent, statusView = tvComprehensionStatus,
                progressView = progressComprehension
            )

            // --- Talasalitaan (game) ---
            applyGradedSkill(
                docs = docs, totalLessons = totalLessons,
                finishedField = "gameFinished", scoreField = "gameScore", totalField = "gameTotal",
                percentView = tvVocabPercent, statusView = tvVocabStatus, progressView = progressVocab
            )

            // --- Pagkilala ng Salita (activity) ---
            applyGradedSkill(
                docs = docs, totalLessons = totalLessons,
                finishedField = "activityFinished", scoreField = "activityScore", totalField = "activityTotal",
                percentView = tvWordRecognitionPercent, statusView = tvWordRecognitionStatus, progressView = progressWordRecognition
            )

            // --- Pagbigkas (pronunciation) ---
            // ASSUMED field names "pronunciationFinished"/"pronunciationScore"/"pronunciationTotal" — confirm against your schema.
            applyGradedSkill(
                docs = docs, totalLessons = totalLessons,
                finishedField = "pronunciationFinished", scoreField = "pronunciationScore", totalField = "pronunciationTotal",
                percentView = tvPronunciationPercent, statusView = tvPronunciationStatus, progressView = progressPronunciation
            )

            // --- Pagsusulit (quiz) ---
            applyGradedSkill(
                docs = docs, totalLessons = totalLessons,
                finishedField = "quizFinished", scoreField = "quizScore", totalField = "quizTotal",
                percentView = tvQuizPercent, statusView = tvQuizStatus, progressView = progressQuiz
            )
        }

        return view
    }

    /**
     * For skills with no numeric score (lecture): fraction while in progress,
     * "Tapos na" once every lesson is finished.
     */
    private fun applyCompletionSkill(
        docs: List<DocumentSnapshot>,
        totalLessons: Int,
        finishedField: String,
        fractionView: TextView,
        statusView: TextView,
        progressView: LinearProgressIndicator
    ) {
        if (totalLessons == 0) {
            setNeutral(fractionView, statusView, progressView)
            return
        }

        val finishedCount = docs.count { it.getBoolean(finishedField) == true }
        progressView.progress = finishedCount * 100 / totalLessons

        when {
            finishedCount == 0 -> setNeutral(fractionView, statusView, progressView)
            finishedCount < totalLessons -> {
                fractionView.text = "$finishedCount/$totalLessons"
                statusView.text = "Nasa Progreso"
                statusView.setBackgroundResource(R.drawable.bg_status_improving)
                statusView.setTextColor(0xFF0066CC.toInt())
            }
            else -> {
                fractionView.text = "$finishedCount/$totalLessons"
                statusView.text = "Tapos na"
                statusView.setBackgroundResource(R.drawable.bg_status_strong)
                statusView.setTextColor(0xFF2E9E4F.toInt())
            }
        }
    }

    /**
     * For skills with a real score (game, activity, pronunciation, quiz):
     * shows fraction + "Nasa Progreso" while some lessons are unfinished;
     * once every lesson is finished, switches to score % and the
     * Mahusay/Umuunlad/Magsanay Pa tiers.
     */
    private fun applyGradedSkill(
        docs: List<DocumentSnapshot>,
        totalLessons: Int,
        finishedField: String,
        scoreField: String,
        totalField: String,
        percentView: TextView,
        statusView: TextView,
        progressView: LinearProgressIndicator
    ) {
        if (totalLessons == 0) {
            setNeutral(percentView, statusView, progressView)
            return
        }

        val finishedCount = docs.count { it.getBoolean(finishedField) == true }

        if (finishedCount == 0) {
            setNeutral(percentView, statusView, progressView)
            return
        }

        if (finishedCount < totalLessons) {
            // Still in progress — don't grade yet, just show how far along they are.
            percentView.text = "$finishedCount/$totalLessons"
            progressView.progress = finishedCount * 100 / totalLessons
            statusView.text = "Nasa Progreso"
            statusView.setBackgroundResource(R.drawable.bg_status_improving)
            statusView.setTextColor(0xFF0066CC.toInt())
            return
        }

        // Fully finished — now grade it.
        val percent = averagePercent(docs, scoreField, totalField) ?: 0
        percentView.text = "$percent%"
        progressView.progress = percent

        when {
            percent >= 85 -> {
                statusView.text = "Mahusay"
                statusView.setBackgroundResource(R.drawable.bg_status_strong)
                statusView.setTextColor(0xFF2E9E4F.toInt())
            }
            percent >= 75 -> {
                statusView.text = "Umuunlad"
                statusView.setBackgroundResource(R.drawable.bg_status_improving)
                statusView.setTextColor(0xFF0066CC.toInt())
            }
            else -> {
                statusView.text = "Magsanay Pa"
                statusView.setBackgroundResource(R.drawable.bg_status_practice)
                statusView.setTextColor(0xFFB4650A.toInt())
            }
        }
    }

    private fun averagePercent(
        docs: List<DocumentSnapshot>,
        scoreField: String,
        totalField: String
    ): Int? {
        val percentages = docs.mapNotNull { doc ->
            val score = doc.getLong(scoreField)
            val total = doc.getLong(totalField)
            if (score != null && total != null && total > 0) {
                (score * 100 / total).toInt()
            } else null
        }
        if (percentages.isEmpty()) return null
        return percentages.sum() / percentages.size
    }

    private fun setNeutral(textView: TextView, statusView: TextView, progressView: LinearProgressIndicator) {
        textView.text = "--"
        statusView.text = "Hindi pa nasisimulan"
        statusView.setBackgroundResource(R.drawable.bg_status_neutral)
        statusView.setTextColor(resources.getColor(R.color.gray, requireContext().theme))
        progressView.progress = 0
    }
}