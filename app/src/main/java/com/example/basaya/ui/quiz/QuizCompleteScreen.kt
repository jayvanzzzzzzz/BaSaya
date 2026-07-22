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

        val percent = if (total > 0) {
            (score * 100) / total
        } else {
            0
        }

        findViewById<TextView>(R.id.tvScorePercent).text = "$percent%"

        val remark = when {
            percent == 100 -> "Napakahusay! Perpekto ang iyong iskor!"
            percent >= 90 -> "Napakahusay! Ipagpatuloy mo!"
            percent >= 75 -> "Mahusay! Magpatuloy sa pag-aaral."
            percent >= 50 -> "Maganda ang iyong pagsisikap. Subukan muli upang mas mapabuti."
            else -> "Huwag panghinaan ng loob. Mag-aral pa at subukan muli."
        }

        findViewById<TextView>(R.id.tvRemark).text = remark
    }
}