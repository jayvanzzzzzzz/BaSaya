package com.example.basaya.ui.activity

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.basaya.R
import com.example.basaya.data.repository.ActivityRepository
import com.example.basaya.data.model.PracticeActivityData
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class PracticeActivity : AppCompatActivity() {

    private lateinit var lessonId: String
    private lateinit var activityRepository: ActivityRepository
    private lateinit var practiceData: PracticeActivityData

    private var currentPageIndex = 0
    private var hasChecked = false
    private val selectedIndices = mutableSetOf<Int>()
    private var score = 0

    private lateinit var tvPageTag: TextView
    private lateinit var tvActivityTitle: TextView
    private lateinit var tvInstruction: TextView
    private lateinit var chipsContainer: FlexboxLayout
    private lateinit var feedbackContainer: LinearLayout
    private lateinit var tvFeedback: TextView
    private lateinit var btnAction: MaterialButton
    private lateinit var llProgressBars: LinearLayout

    private val trackSegments = mutableListOf<View>()
    private val colorIncomplete = 0xFFD9DDE3.toInt()
    private val colorComplete = 0xFF0066CC.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_practice)

        val statusBarBg = findViewById<View>(R.id.statusBarBg)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            statusBarBg.layoutParams.height = statusBarHeight
            statusBarBg.requestLayout()
            insets
        }

        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        lessonId = intent.getStringExtra("LESSON_ID") ?: ""

        if (lessonId.isBlank()) {
            Toast.makeText(this, "Missing lesson data.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        tvPageTag = findViewById(R.id.tvPageTag)
        tvActivityTitle = findViewById(R.id.tvActivityTitle)
        tvInstruction = findViewById(R.id.tvInstruction)
        chipsContainer = findViewById(R.id.chipsContainer)
        feedbackContainer = findViewById(R.id.feedbackContainer)
        tvFeedback = findViewById(R.id.tvFeedback)
        btnAction = findViewById(R.id.btnAction)
        llProgressBars = findViewById(R.id.llProgressBars)

        activityRepository = ActivityRepository(this)

        activityRepository = ActivityRepository(this)

        lifecycleScope.launch {
            val t0 = System.currentTimeMillis()
            Log.d("PerfCheck", "Starting getActivity()")

            val fetched = activityRepository.getActivity(lessonId)

            Log.d("PerfCheck", "getActivity() returned after ${System.currentTimeMillis() - t0}ms")

            if (fetched == null || fetched.pages.isEmpty()) {
                Toast.makeText(this@PracticeActivity, "Activity not found.", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            practiceData = fetched
            tvActivityTitle.text = practiceData.title
            tvInstruction.text = practiceData.instruction

            setupProgressTrack()
            showPage(currentPageIndex)
            Log.d("PerfCheck", "UI fully rendered at ${System.currentTimeMillis() - t0}ms")

            btnAction.setOnClickListener {
                if (!hasChecked) {
                    checkAnswer()
                } else {
                    goToNext()
                }
            }
        }
    }

    private fun setupProgressTrack() {
        llProgressBars.removeAllViews()
        trackSegments.clear()

        val pageCount = practiceData.pages.size
        val dashHeightPx = (4 * resources.displayMetrics.density).toInt()
        val gapPx = (10 * resources.displayMetrics.density).toInt()

        for (i in 0 until pageCount) {
            val dash = View(this).apply {
                setBackgroundResource(R.drawable.dash_track)
            }
            val params = LinearLayout.LayoutParams(0, dashHeightPx, 1f).apply {
                if (i != 0) marginStart = gapPx
            }
            dash.layoutParams = params
            llProgressBars.addView(dash)
            trackSegments.add(dash)
        }
    }

    private fun updateProgressTrack() {
        for (i in trackSegments.indices) {
            val isDone = i < currentPageIndex || (i == currentPageIndex && hasChecked)
            trackSegments[i].setBackgroundColor(if (isDone) colorComplete else colorIncomplete)
        }
        tvPageTag.text = "PAHINA ${currentPageIndex + 1}/${practiceData.pages.size}"
    }

    private fun showPage(index: Int) {
        val page = practiceData.pages[index]

        selectedIndices.clear()
        hasChecked = false
        feedbackContainer.visibility = View.GONE
        btnAction.text = "Suriin ang Sagot"
        btnAction.isEnabled = false

        chipsContainer.removeAllViews()

        page.words.forEachIndexed { wordIndex, word ->
            val chip = TextView(this).apply {
                text = word
                textSize = 17f
                typeface = Typeface.MONOSPACE
                setTextColor(0xFF1B2430.toInt())
                setPadding(12, 8, 12, 8)
                gravity = Gravity.CENTER
                background = null
                rotation = if (wordIndex % 2 == 0) -1.2f else 1.2f

                val params = FlexboxLayout.LayoutParams(
                    FlexboxLayout.LayoutParams.WRAP_CONTENT,
                    FlexboxLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(4, 4, 4, 4)
                }
                layoutParams = params

                setOnClickListener {
                    if (hasChecked) return@setOnClickListener

                    if (selectedIndices.contains(wordIndex)) {
                        selectedIndices.remove(wordIndex)
                        background = null
                    } else {
                        selectedIndices.add(wordIndex)
                        setBackgroundResource(R.drawable.highlight_yellow)
                    }

                    btnAction.isEnabled = selectedIndices.isNotEmpty()
                }
            }
            chipsContainer.addView(chip)
        }

        updateProgressTrack()
    }

    private fun checkAnswer() {
        val page = practiceData.pages[currentPageIndex]
        val correctSet = page.correctIndices.toSet()
        val isCorrect = selectedIndices == correctSet

        for (i in 0 until chipsContainer.childCount) {
            val chip = chipsContainer.getChildAt(i) as TextView
            val wasSelected = selectedIndices.contains(i)
            val shouldBeSelected = correctSet.contains(i)

            chip.isEnabled = false

            when {
                wasSelected && shouldBeSelected -> {
                    chip.setBackgroundResource(R.drawable.highlight_green)
                    chip.text = "${page.words[i]} ✓"
                }
                wasSelected && !shouldBeSelected -> {
                    chip.setBackgroundResource(R.drawable.highlight_red)
                    chip.text = "${page.words[i]} ✕"
                }
                else -> {
                    chip.background = null
                }
            }
        }

        hasChecked = true
        feedbackContainer.visibility = View.VISIBLE
        updateProgressTrack()

        if (isCorrect) {
            score++
            tvFeedback.text = "Mahusay! ${practiceData.explanation}"
            btnAction.text = if (currentPageIndex == practiceData.pages.lastIndex) "Tapusin" else "Susunod"
        } else {
            tvFeedback.text = "Subukan muli. ${practiceData.explanation}"
            btnAction.text = "Subukan Muli"
        }
    }

    private fun goToNext() {
        val page = practiceData.pages[currentPageIndex]
        val wasCorrect = selectedIndices == page.correctIndices.toSet()

        if (!wasCorrect) {
            showPage(currentPageIndex)
            return
        }

        if (currentPageIndex < practiceData.pages.lastIndex) {
            currentPageIndex++
            showPage(currentPageIndex)
        } else {
            finishActivity()
        }
    }

    private fun finishActivity() {
        val total = practiceData.pages.size
        val uid = FirebaseAuth.getInstance().currentUser?.uid

        lifecycleScope.launch {
            if (uid != null) {
                try {
                    activityRepository.markActivityFinished(uid, lessonId, score, total)
                } catch (e: Exception) {
                    Log.e("PracticeActivity", "Failed to save activity result", e)
                }
            }

            val intent = Intent(this@PracticeActivity, PracticeCompleteScreen::class.java).apply {
                putExtra("SCORE", score)
                putExtra("TOTAL", total)
                putExtra("ACTIVITY_TITLE", practiceData.title)
            }
            startActivity(intent)
            finish()
        }
    }

}