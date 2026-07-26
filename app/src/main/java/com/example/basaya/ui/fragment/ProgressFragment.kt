package com.example.basaya.ui.fragment

import android.os.Bundle
import android.widget.TextView
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.example.basaya.R
import com.google.firebase.auth.FirebaseAuth
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
        val tvVocabPercent = view.findViewById<TextView>(R.id.tvVocabPercent)
        val tvVocabStatus = view.findViewById<TextView>(R.id.tvVocabStatus)
        val tvWordRecognitionPercent =
            view.findViewById<TextView>(R.id.tvWordRecognitionPercent)

        val tvWordRecognitionStatus =
            view.findViewById<TextView>(R.id.tvWordRecognitionStatus)

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

            applySkill(
                percent = averagePercent(docs, "quizScore", "quizTotal"),
                percentView = tvComprehensionPercent,
                statusView = tvComprehensionStatus
            )

            applySkill(
                percent = averagePercent(docs, "gameScore", "gameTotal"),
                percentView = tvVocabPercent,
                statusView = tvVocabStatus
            )

            applySkill(
                percent = averagePercent(docs, "activityScore", "activityTotal"),
                percentView = tvWordRecognitionPercent,
                statusView = tvWordRecognitionStatus
            )
        }

        return view
    }

    /**
     * Averages score/total (as %) across every assignedLessons doc that has
     * both fields present. Lessons that haven't touched this feature yet are skipped,
     * so they don't drag the average down as if the student scored 0.
     */
    private fun averagePercent(
        docs: List<com.google.firebase.firestore.DocumentSnapshot>,
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

    private fun applySkill(percent: Int?, percentView: TextView, statusView: TextView) {
        if (percent == null) {
            percentView.text = "--"
            statusView.text = "Hindi pa nasisimulan"
            statusView.setBackgroundResource(R.drawable.bg_status_neutral)
            statusView.setTextColor(resources.getColor(R.color.gray, requireContext().theme))
            return
        }

        percentView.text = "$percent%"

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
}