package com.example.basaya.ui.activity

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.basaya.R
import com.google.android.material.button.MaterialButton

class PracticeCompleteScreen : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_practice_complete_screen)

        val statusBarBg = findViewById<View>(R.id.statusBarBg)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            statusBarBg.layoutParams.height = statusBarHeight
            statusBarBg.requestLayout()
            insets
        }

        val score = intent.getIntExtra("SCORE", 0)
        val total = intent.getIntExtra("TOTAL", 1)
        val activityTitle = intent.getStringExtra("ACTIVITY_TITLE") ?: ""
        val percent = if (total > 0) (score * 100 / total) else 0

        findViewById<TextView>(R.id.tvActivityTitle).text = activityTitle
        findViewById<TextView>(R.id.tvScoreFraction).text = "$score / $total"

        val tvRemark = findViewById<TextView>(R.id.tvRemark)

        when {
            percent >= 80 -> {
                tvRemark.text = "Mahusay! Handa ka na sa susunod na hakbang."
            }
            percent >= 50 -> {
                tvRemark.text = "Magaling! Konting ulit pa lang."
            }
            else -> {
                tvRemark.text = "Magpatuloy! Balikan ang aralin at subukan muli."
            }
        }

        findViewById<MaterialButton>(R.id.btnDone).setOnClickListener {
            finish()
        }
    }
}