// ui/quiz/QuizCompleteScreen.kt
package com.example.basaya.ui.quiz

import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.basaya.R
import com.google.android.material.button.MaterialButton

class QuizCompleteScreen : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_quiz_complete_screen)

        val score = intent.getIntExtra("SCORE", 0)
        val total = intent.getIntExtra("TOTAL", 0)

        findViewById<TextView>(R.id.tvScore).text = "$score / $total"

        findViewById<MaterialButton>(R.id.btnDone).setOnClickListener {
            finish()
        }
    }
}