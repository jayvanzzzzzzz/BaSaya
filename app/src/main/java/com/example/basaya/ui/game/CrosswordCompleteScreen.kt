package com.example.basaya.ui.game

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.example.basaya.R
import com.example.basaya.data.mock.CrosswordMockData
import com.example.basaya.data.database.AppDatabase


class CrosswordCompleteScreen : AppCompatActivity() {
    private lateinit var btnNext: Button
    private lateinit var lessonId: String

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_crossword_complete_screen)

        lessonId = intent.getStringExtra("LESSON_ID") ?: ""

        val title = findViewById<TextView>(R.id.tvLessonTitle)
        val levelBox = findViewById<LinearLayout>(R.id.currentLevel)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val dimOverlay = findViewById<View>(R.id.dimOverlay)
        val progressText = findViewById<TextView>(R.id.tvProgress)
        btnNext = findViewById(R.id.btnNext)

        val allLevels = CrosswordMockData.gameLevels.size
        val currentLevel = intent.getIntExtra("NEXT_LEVEL", 1)

        title.alpha = 0f
        title.translationY = -40f

        levelBox.alpha = 0f
        levelBox.scaleX = 0.85f
        levelBox.scaleY = 0.85f

        progressText.text = "$currentLevel / $allLevels"
        progressBar.max = allLevels
        progressBar.progress = currentLevel

        if (currentLevel < allLevels) {
            btnNext.setOnClickListener {
                val intent =
                    Intent(this@CrosswordCompleteScreen, CrosswordActivity::class.java).apply {
                        putExtra("LESSON_ID", lessonId)   // 👈 add this
                    }
                val options =
                    ActivityOptions.makeCustomAnimation(this@CrosswordCompleteScreen, 0, 0)
                startActivity(intent, options.toBundle())
                finish()
            }
        } else {
            btnNext.text = "COMPLETE"
        }


        title.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .start()

        levelBox.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(250)
            .setDuration(450)
            .start()

        progressBar.animate()
            .alpha(1f)
            .setStartDelay(500)
            .setDuration(300)
            .start()

        progressText.animate()
            .alpha(1f)
            .setStartDelay(500)
            .setDuration(300)
            .start()

        dimOverlay.alpha = 0f
        dimOverlay.animate()
            .alpha(1f)
            .setDuration(400)
            .start()

    }
}