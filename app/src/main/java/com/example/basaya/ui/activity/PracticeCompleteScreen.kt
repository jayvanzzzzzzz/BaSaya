package com.example.basaya.ui.activity

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.basaya.R
import com.example.basaya.ui.game.ConfettiView
import com.example.basaya.ui.pronunciation.PronunciationBookView
import com.example.basaya.ui.theme.useDarkStatusBarIconsInLightTheme

class PracticeCompleteScreen : AppCompatActivity() {

    private var scoreAnimator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_practice_complete_screen)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        useDarkStatusBarIconsInLightTheme()

        val score = intent.getIntExtra("SCORE", 0)
        val total = intent.getIntExtra("TOTAL", 1)
        val activityTitle = intent.getStringExtra("ACTIVITY_TITLE") ?: ""
        val percent = if (total > 0) (score * 100 / total) else 0

        val tvActivityTitle = findViewById<TextView>(R.id.tvActivityTitle)
        tvActivityTitle.text = activityTitle
        tvActivityTitle.visibility = if (activityTitle.isBlank()) View.GONE else View.VISIBLE

        findViewById<TextView>(R.id.tvRemark).setText(
            when {
                percent >= 80 -> R.string.ac_remark_great
                percent >= 50 -> R.string.ac_remark_good
                else -> R.string.ac_remark_keep_going
            }
        )

        val starCount = when {
            percent >= 80 -> 3
            percent >= 50 -> 2
            score > 0 -> 1
            else -> 0
        }
        findViewById<View>(R.id.starsRow).contentDescription = getString(R.string.ac_stars_cd, starCount)

        val btnDone = findViewById<TextView>(R.id.btnDone)
        btnDone.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = Button::class.java.name
            }
        }
        btnDone.setOnClickListener { finish() }

        playEntrance(score, total, starCount, celebrate = percent >= 50)
    }

    private fun playEntrance(score: Int, total: Int, starCount: Int, celebrate: Boolean) {
        val buddy = findViewById<PronunciationBookView>(R.id.buddy)
        val scoreCard = findViewById<View>(R.id.scoreCard)
        val tvScore = findViewById<TextView>(R.id.tvScoreFraction)
        val confetti = findViewById<ConfettiView>(R.id.confettiView)
        val stars = listOf<ImageView>(findViewById(R.id.star1), findViewById(R.id.star2), findViewById(R.id.star3))
        val density = resources.displayMetrics.density

        // Score card rises in
        scoreCard.alpha = 0f
        scoreCard.translationY = 40f * density
        scoreCard.animate().alpha(1f).translationY(0f).setStartDelay(150).setDuration(450)
            .setInterpolator(DecelerateInterpolator()).start()

        // The buddy celebrates a good score and cheers on a lower one
        buddy.setMood(if (celebrate) PronunciationBookView.Mood.HAPPY else PronunciationBookView.Mood.CHEER)

        // Score counts up from zero
        tvScore.text = getString(R.string.ac_score, 0, total)
        tvScore.contentDescription = getString(R.string.ac_score, score, total)
        scoreAnimator = ValueAnimator.ofInt(0, score).apply {
            startDelay = 500
            duration = (300L + score * 120L).coerceAtMost(1400L)
            interpolator = DecelerateInterpolator()
            addUpdateListener { tvScore.text = getString(R.string.ac_score, it.animatedValue as Int, total) }
            start()
        }

        // Earned stars pop in one by one, turning gold
        stars.forEachIndexed { i, star ->
            star.scaleX = 0f
            star.scaleY = 0f
            val earned = i < starCount
            if (earned) star.setColorFilter(ContextCompat.getColor(this, R.color.cw_star))
            star.animate()
                .scaleX(1f).scaleY(1f)
                .setStartDelay(700L + i * 260L)
                .setDuration(420)
                .setInterpolator(OvershootInterpolator(if (earned) 3.5f else 1.5f))
                .withStartAction { if (earned) star.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
                .start()
        }

        if (celebrate) {
            confetti.postDelayed({ confetti.rain(140) }, 600)
        }
    }

    override fun onDestroy() {
        scoreAnimator?.cancel()
        super.onDestroy()
    }
}
