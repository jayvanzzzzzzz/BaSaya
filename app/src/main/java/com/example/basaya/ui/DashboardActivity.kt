package com.example.basaya.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.basaya.R
import com.example.basaya.data.repository.LectureRepository
import com.example.basaya.ui.game.CrosswordActivity
import com.example.basaya.ui.quiz.QuizActivity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class DashboardActivity : AppCompatActivity() {

    private lateinit var gameCard: CardView
    private lateinit var lectureCard: CardView
    private lateinit var quizCard: CardView
    private lateinit var quizLockOverlay: LinearLayout

    private lateinit var lectureRepository: LectureRepository
    private lateinit var lessonId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dashboard)

        val statusBarBg = findViewById<View>(R.id.statusBarBg)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            statusBarBg.layoutParams.height = statusBarHeight
            statusBarBg.requestLayout()
            insets
        }

        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        lessonId = intent.getStringExtra("LESSON_ID") ?: ""

        gameCard = findViewById(R.id.gameCard)
        lectureCard = findViewById(R.id.lectureCard)
        quizCard = findViewById(R.id.quizCard)
        quizLockOverlay = findViewById(R.id.quizLockOverlay)

        lectureRepository = LectureRepository(this)

        gameCard.setOnClickListener {
            val intent = Intent(this@DashboardActivity, CrosswordActivity::class.java).apply {
                putExtra("LESSON_ID", lessonId)
            }
            startActivity(intent)
        }

        lectureCard.setOnClickListener {
            val intent = Intent(this@DashboardActivity, LectureActivity::class.java).apply {
                putExtra("LESSON_ID", lessonId)
            }
            startActivity(intent)
        }

        // quiz starts locked
        quizCard.isClickable = false
        quizCard.setOnClickListener {
            val intent = Intent(this@DashboardActivity, QuizActivity::class.java).apply {
                putExtra("LESSON_ID", lessonId)
            }
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // re-check every time this screen becomes visible
        checkQuizUnlockState()
    }

    private fun checkQuizUnlockState() {
        if (lessonId.isBlank()) return

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        lifecycleScope.launch {
            val isFinished = lectureRepository.checkCompletion(uid, lessonId)

            if (isFinished) {
                quizCard.isClickable = true
                quizLockOverlay.visibility = View.GONE
            } else {
                quizCard.isClickable = false
                quizLockOverlay.visibility = View.VISIBLE
            }
        }
    }
}