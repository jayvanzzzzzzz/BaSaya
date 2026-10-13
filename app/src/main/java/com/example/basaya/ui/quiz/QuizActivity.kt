package com.example.basaya.ui.quiz

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.basaya.R
import com.example.basaya.data.model.Quiz
import com.example.basaya.data.progress.Skill
import com.example.basaya.data.progress.SkillProgressRepository
import com.example.basaya.data.repository.QuizRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class QuizActivity : AppCompatActivity() {

    private lateinit var lessonId: String
    private lateinit var quizRepository: QuizRepository
    private lateinit var quiz: Quiz

    private var currentQuestionIndex = 0
    /** Choices picked on the current question (several when the question has multiple answers). */
    private val selectedChoices = mutableSetOf<Int>()
    private val userAnswers = mutableListOf<Set<Int>>()

    private lateinit var tvQuizTitle: TextView
    private lateinit var tvQuestionProgress: TextView
    private lateinit var tvPickHint: TextView
    private lateinit var tvQuestion: TextView
    private lateinit var choicesContainer: LinearLayout
    private lateinit var btnNextQuestion: TextView
    private lateinit var rocketTrack: FrameLayout
    private lateinit var trackFill: View
    private lateinit var ivRocket: ImageView

    private var trackAnimator: ValueAnimator? = null
    private var rocketWobble: ObjectAnimator? = null
    private var trackFraction = 0f

    /** Game-show colors per answer letter: A coral, B blue, C orange, D green. */
    private val letterColors = listOf(
        R.color.qz_a to R.color.qz_a_lip,
        R.color.qz_b to R.color.qz_b_lip,
        R.color.qz_c to R.color.qz_c_lip,
        R.color.qz_d to R.color.qz_d_lip
    )

    private val density by lazy { resources.displayMetrics.density }
    private fun dp(value: Float): Int = (value * density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_quiz)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        // Light status bar icons on the dark space background
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        lessonId = intent.getStringExtra("LESSON_ID") ?: ""

        if (lessonId.isBlank()) {
            Toast.makeText(this, getString(R.string.qz_missing_lesson), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        tvQuizTitle = findViewById(R.id.tvQuizTitle)
        tvQuestionProgress = findViewById(R.id.tvQuestionProgress)
        tvPickHint = findViewById(R.id.tvPickHint)
        tvQuestion = findViewById(R.id.tvQuestion)
        choicesContainer = findViewById(R.id.choicesContainer)
        btnNextQuestion = findViewById(R.id.btnNextQuestion)
        rocketTrack = findViewById(R.id.rocketTrack)
        trackFill = findViewById(R.id.trackFill)
        ivRocket = findViewById(R.id.ivRocket)

        findViewById<ImageButton>(R.id.btnClose).setOnClickListener { finish() }
        announceAsButton(btnNextQuestion)
        startRocketWobble()

        quizRepository = QuizRepository(this)

        lifecycleScope.launch {
            val fetchedQuiz = quizRepository.getQuiz(lessonId)

            if (fetchedQuiz == null || fetchedQuiz.questions.isEmpty()) {
                Toast.makeText(this@QuizActivity, getString(R.string.qz_not_found), Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            quiz = fetchedQuiz
            tvQuizTitle.text = quiz.title
            userAnswers.addAll(List(quiz.questions.size) { emptySet<Int>() })

            showQuestion(currentQuestionIndex, animateIn = false)

            btnNextQuestion.setOnClickListener {
                pressBounce(btnNextQuestion)
                userAnswers[currentQuestionIndex] = selectedChoices.toSet()

                if (currentQuestionIndex < quiz.questions.lastIndex) {
                    currentQuestionIndex++
                    showQuestion(currentQuestionIndex)
                } else {
                    btnNextQuestion.isEnabled = false
                    finishQuiz()
                }
            }
        }
    }

    // ───────────────────────────── Questions ─────────────────────────────

    private fun showQuestion(index: Int, animateIn: Boolean = true) {
        val question = quiz.questions[index]

        tvQuestionProgress.text = getString(R.string.qz_question_progress, index + 1, quiz.questions.size)
        moveRocket((index + 1f) / quiz.questions.size)

        tvQuestion.text = question.question
        if (animateIn) {
            tvQuestion.alpha = 0f
            tvQuestion.translationX = tvQuestion.width * 0.25f
            tvQuestion.animate().alpha(1f).translationX(0f).setDuration(320)
                .setInterpolator(DecelerateInterpolator()).start()
        }

        choicesContainer.removeAllViews()
        selectedChoices.clear()
        selectedChoices.addAll(userAnswers.getOrNull(index).orEmpty())

        // Multiple-answer questions tell the child how many to pick
        tvPickHint.text = if (question.isMultiple) {
            getString(R.string.qz_pick_many)
        } else {
            getString(R.string.qz_pick)
        }

        question.choices.forEachIndexed { choiceIndex, choiceText ->
            val tile = buildChoiceTile(choiceIndex, choiceText)
            choicesContainer.addView(tile)
            renderChoice(tile, choiceIndex, choiceText, choiceIndex in selectedChoices)

            // Answers bounce up one after another
            tile.alpha = 0f
            tile.translationY = dp(24f).toFloat()
            tile.animate()
                .alpha(1f).translationY(0f)
                .setStartDelay(120L + choiceIndex * 70L)
                .setDuration(340)
                .setInterpolator(OvershootInterpolator(1.6f))
                .start()
        }

        btnNextQuestion.isEnabled = selectedChoices.isNotEmpty()
        btnNextQuestion.setText(if (index == quiz.questions.lastIndex) R.string.qz_finish else R.string.qz_next)
    }

    private fun buildChoiceTile(choiceIndex: Int, choiceText: String): LinearLayout {
        val letter = TextView(this).apply {
            tag = "letter"
            text = letterFor(choiceIndex)
            typeface = ResourcesCompat.getFont(this@QuizActivity, R.font.lexend)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            includeFontPadding = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = LinearLayout.LayoutParams(dp(42f), dp(42f))
        }

        val label = TextView(this).apply {
            tag = "label"
            text = choiceText
            typeface = ResourcesCompat.getFont(this@QuizActivity, R.font.be_vietnam_bold)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(14f)
                marginEnd = dp(8f)
            }
        }

        val check = ImageView(this).apply {
            tag = "check"
            setImageResource(R.drawable.ac_ic_check)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = LinearLayout.LayoutParams(dp(26f), dp(26f))
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(68f)
            setPadding(dp(12f), dp(10f), dp(16f), dp(15f))
            isClickable = true
            isFocusable = true
            announceAsButton(this)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12f) }

            addView(letter)
            addView(label)
            addView(check)

            setOnClickListener {
                val question = quiz.questions[currentQuestionIndex]
                if (question.isMultiple) {
                    // Tap to pick, tap again to unpick
                    if (!selectedChoices.remove(choiceIndex)) selectedChoices.add(choiceIndex)
                } else {
                    selectedChoices.clear()
                    selectedChoices.add(choiceIndex)
                }
                for (i in 0 until choicesContainer.childCount) {
                    renderChoice(choicesContainer.getChildAt(i) as LinearLayout, i, question.choices[i], i in selectedChoices)
                }
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                pop(this)
                btnNextQuestion.isEnabled = selectedChoices.isNotEmpty()
            }
        }
    }

    /** Idle: white tile with a colored letter badge. Picked: the whole tile lights up in its color. */
    private fun renderChoice(tile: LinearLayout, choiceIndex: Int, choiceText: String, selected: Boolean) {
        val (fillRes, lipRes) = letterColors[choiceIndex % letterColors.size]
        val accent = ContextCompat.getColor(this, fillRes)
        val accentLip = ContextCompat.getColor(this, lipRes)
        val ink = ContextCompat.getColor(this, R.color.qz_ink)

        tile.isSelected = selected
        tile.background = if (selected) {
            candy(accent, accentLip, dp(20f).toFloat())
        } else {
            candy(Color.WHITE, ContextCompat.getColor(this, R.color.qz_card_lip), dp(20f).toFloat())
        }

        tile.findViewWithTag<TextView>("letter").apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (selected) Color.WHITE else accent)
            }
            setTextColor(if (selected) accent else Color.WHITE)
        }
        tile.findViewWithTag<TextView>("label").setTextColor(if (selected) Color.WHITE else ink)
        tile.findViewWithTag<ImageView>("check").visibility = if (selected) View.VISIBLE else View.INVISIBLE

        tile.contentDescription = getString(
            if (selected) R.string.qz_choice_selected_cd else R.string.qz_choice_cd,
            letterFor(choiceIndex), choiceText
        )
    }

    private fun letterFor(index: Int): String = ('A' + index).toString()

    private fun candy(fill: Int, lip: Int, radius: Float): Drawable {
        val lipHeight = dp(5f)
        val base = GradientDrawable().apply {
            cornerRadius = radius
            setColor(lip)
        }
        val face = GradientDrawable().apply {
            cornerRadius = radius
            setColor(fill)
        }
        return LayerDrawable(arrayOf(base, face)).apply {
            setLayerInset(0, 0, lipHeight, 0, 0)
            setLayerInset(1, 0, 0, 0, lipHeight)
        }
    }

    // ───────────────────────────── Rocket progress ─────────────────────────────

    private fun moveRocket(fraction: Float) {
        rocketTrack.post {
            trackAnimator?.cancel()
            trackAnimator = ValueAnimator.ofFloat(trackFraction, fraction).apply {
                duration = 650
                interpolator = OvershootInterpolator(1.1f)
                addUpdateListener {
                    trackFraction = it.animatedValue as Float
                    val trackWidth = rocketTrack.width
                    val fillWidth = (trackWidth * trackFraction).toInt().coerceIn(0, trackWidth)
                    trackFill.layoutParams = trackFill.layoutParams.apply { width = fillWidth }
                    ivRocket.translationX = (fillWidth - ivRocket.width * 0.75f)
                        .coerceIn(0f, (trackWidth - ivRocket.width).toFloat())
                }
                start()
            }
        }
    }

    private fun startRocketWobble() {
        rocketWobble = ObjectAnimator.ofFloat(ivRocket, View.TRANSLATION_Y, -dp(2f).toFloat(), dp(2f).toFloat()).apply {
            duration = 900
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    // ───────────────────────────── Finish ─────────────────────────────

    private fun finishQuiz() {
        var score = 0
        quiz.questions.forEachIndexed { i, question ->
            // Right only when exactly the correct choice(s) were picked
            if (userAnswers[i] == question.correctAnswers) score++
        }
        val total = quiz.questions.size

        val uid = FirebaseAuth.getInstance().currentUser?.uid

        lifecycleScope.launch {
            if (uid != null) {
                try {
                    quizRepository.saveQuizResult(uid, lessonId, score, total)
                } catch (e: Exception) {
                    android.util.Log.e("QuizActivity", "Failed to save quiz result", e)
                }

                // Pagsusulit skill
                try {
                    SkillProgressRepository().recordResult(
                        uid, lessonId, Skill.PAGSUSULIT,
                        correct = score, attempts = total, items = total
                    )
                    SkillProgressRepository.refreshInBackground(uid)
                } catch (e: Exception) {
                    android.util.Log.e("QuizActivity", "Failed to save quiz progress", e)
                }
            }

            val intent = Intent(this@QuizActivity, QuizCompleteScreen::class.java).apply {
                putExtra("SCORE", score)
                putExtra("TOTAL", total)
                putExtra("QUIZ_TITLE", quiz.title)
            }
            startActivity(intent)
            finish()
        }
    }

    // ───────────────────────────── Motion helpers ─────────────────────────────

    private fun pop(view: View) {
        view.animate().cancel()
        view.scaleX = 1.05f
        view.scaleY = 1.05f
        view.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(300)
            .setInterpolator(OvershootInterpolator(3f)).start()
    }

    private fun pressBounce(view: View) {
        view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(70)
            .withEndAction {
                view.animate().scaleX(1f).scaleY(1f).setDuration(220)
                    .setInterpolator(OvershootInterpolator(3f)).start()
            }
            .start()
    }

    private fun announceAsButton(view: View) {
        view.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = Button::class.java.name
            }
        }
    }

    override fun onDestroy() {
        trackAnimator?.cancel()
        rocketWobble?.cancel()
        super.onDestroy()
    }
}
