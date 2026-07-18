package com.example.basaya.ui.quiz

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.basaya.R
import com.example.basaya.data.repository.QuizRepository
import com.example.basaya.model.Quiz
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class QuizActivity : AppCompatActivity() {

    private lateinit var lessonId: String
    private lateinit var quizRepository: QuizRepository
    private lateinit var quiz: Quiz

    private var currentQuestionIndex = 0
    private var selectedChoiceIndex: Int? = null
    private val userAnswers = mutableListOf<Int?>()

    private lateinit var tvQuizTitle: TextView
    private lateinit var tvQuestionProgress: TextView
    private lateinit var tvQuestion: TextView
    private lateinit var choicesContainer: android.widget.LinearLayout
    private lateinit var btnNextQuestion: MaterialButton
    private lateinit var progressQuiz: android.widget.ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_quiz)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        lessonId = intent.getStringExtra("LESSON_ID") ?: ""

        if (lessonId.isBlank()) {
            Toast.makeText(this, "Missing lesson data.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        tvQuizTitle = findViewById(R.id.tvQuizTitle)
        tvQuestionProgress = findViewById(R.id.tvQuestionProgress)
        tvQuestion = findViewById(R.id.tvQuestion)
        choicesContainer = findViewById(R.id.choicesContainer)
        btnNextQuestion = findViewById(R.id.btnNextQuestion)
        progressQuiz = findViewById(R.id.progressQuiz)

        quizRepository = QuizRepository(this)

        lifecycleScope.launch {
            val fetchedQuiz = quizRepository.getQuiz(lessonId)

            if (fetchedQuiz == null || fetchedQuiz.questions.isEmpty()) {
                Toast.makeText(this@QuizActivity, "Quiz not found.", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            quiz = fetchedQuiz
            tvQuizTitle.text = quiz.title
            userAnswers.addAll(List(quiz.questions.size) { null })

            showQuestion(currentQuestionIndex)

            btnNextQuestion.setOnClickListener {
                userAnswers[currentQuestionIndex] = selectedChoiceIndex

                if (currentQuestionIndex < quiz.questions.lastIndex) {
                    currentQuestionIndex++
                    showQuestion(currentQuestionIndex)
                } else {
                    finishQuiz()
                }
            }
        }
    }

    private fun showQuestion(index: Int) {
        val question = quiz.questions[index]

        tvQuestionProgress.text = "Question ${index + 1} / ${quiz.questions.size}"
        progressQuiz.max = quiz.questions.size
        progressQuiz.progress = index + 1

        tvQuestion.text = question.question

        choicesContainer.removeAllViews()
        selectedChoiceIndex = userAnswers.getOrNull(index)

        //padding for choices
        // Padding and spacing for choices
        val horizontalPadding = (18 * resources.displayMetrics.density).toInt()
        val verticalPadding = (16 * resources.displayMetrics.density).toInt()
        val choiceSpacing = (12 * resources.displayMetrics.density).toInt()
        val iconSpacing = (14 * resources.displayMetrics.density).toInt()

        question.choices.forEachIndexed { choiceIndex, choiceText ->
            val choiceView = TextView(this).apply {
                text = choiceText
                textSize = 15f
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(resources.getColor(android.R.color.black, theme))

                setBackgroundResource(R.drawable.bg_quiz_choice)

                setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.bg_choice_indicator,
                    0,
                    0,
                    0
                )

                compoundDrawablePadding = iconSpacing

                setPadding(
                    horizontalPadding,
                    verticalPadding,
                    horizontalPadding,
                    verticalPadding
                )

                isSelected = choiceIndex == selectedChoiceIndex


                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = choiceSpacing
                }

                setOnClickListener {
                    selectedChoiceIndex = choiceIndex

                    for (i in 0 until choicesContainer.childCount) {
                        choicesContainer.getChildAt(i).isSelected = i == choiceIndex
                    }

                    btnNextQuestion.isEnabled = true
                }
            }

            choicesContainer.addView(choiceView)
        }

        btnNextQuestion.isEnabled = selectedChoiceIndex != null
        btnNextQuestion.text = if (index == quiz.questions.lastIndex) "Finish" else "Next"
    }

    private fun finishQuiz() {
        var score = 0
        quiz.questions.forEachIndexed { i, question ->
            if (userAnswers[i] == question.answer) score++
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
            }

            val intent = Intent(this@QuizActivity, QuizCompleteScreen::class.java).apply {
                putExtra("SCORE", score)
                putExtra("TOTAL", total)
            }
            startActivity(intent)
            finish()
        }
    }
}