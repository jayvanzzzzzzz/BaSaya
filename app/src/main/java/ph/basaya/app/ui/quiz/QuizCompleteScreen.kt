// ui/quiz/QuizCompleteScreen.kt
package ph.basaya.app.ui.quiz

import android.animation.ValueAnimator
import android.content.Intent
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
import androidx.core.view.WindowInsetsControllerCompat
import ph.basaya.app.R
import ph.basaya.app.data.progress.PracticeLessons
import ph.basaya.app.ui.game.ConfettiView
import ph.basaya.app.ui.pronunciation.PronunciationBookView
import ph.basaya.app.utils.Network
import com.google.android.material.progressindicator.CircularProgressIndicator

class QuizCompleteScreen : AppCompatActivity() {

    private var countAnimator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_quiz_complete_screen)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        val score = intent.getIntExtra("SCORE", 0)
        val total = intent.getIntExtra("TOTAL", 0)
        val quizTitle = intent.getStringExtra("QUIZ_TITLE").orEmpty()

        val percent = if (total > 0) {
            (score * 100) / total
        } else {
            0
        }

        val tvQuizTitle = findViewById<TextView>(R.id.tvQuizTitle)
        tvQuizTitle.text = quizTitle
        tvQuizTitle.visibility = if (quizTitle.isBlank()) View.GONE else View.VISIBLE

        findViewById<TextView>(R.id.tvScorePercent).text = getString(R.string.qz_percent, percent)

        findViewById<TextView>(R.id.tvRemark).setText(
            when {
                percent == 100 -> R.string.qz_remark_perfect
                percent >= 90 -> R.string.qz_remark_excellent
                percent >= 75 -> R.string.qz_remark_great
                percent >= 50 -> R.string.qz_remark_good
                else -> R.string.qz_remark_keep_going
            }
        )

        val starCount = when {
            percent >= 90 -> 3
            percent >= 75 -> 2
            percent >= 50 -> 1
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
        btnDone.setOnClickListener {
            finish()
        }

        // Practice lesson: nothing was saved, so it can be done again right away.
        val lessonId = intent.getStringExtra("LESSON_ID").orEmpty()
        if (PracticeLessons.isPractice(lessonId)) {
            findViewById<View>(R.id.tvPracticeNote).visibility = View.VISIBLE
            // The note adds height to the card, so a smaller buddy keeps everything on short screens
            findViewById<View>(R.id.buddy).layoutParams.apply {
                width = (120 * resources.displayMetrics.density).toInt()
                height = (140 * resources.displayMetrics.density).toInt()
            }
            findViewById<TextView>(R.id.btnRetry).apply {
                visibility = View.VISIBLE
                accessibilityDelegate = btnDone.accessibilityDelegate
                setOnClickListener {
                    startActivity(Intent(this@QuizCompleteScreen, QuizActivity::class.java).putExtra("LESSON_ID", lessonId))
                    finish()
                }
            }
        } else if (!Network.isOnline(this)) {
            // Offline: the score was saved on the phone and is sent once online.
            findViewById<TextView>(R.id.tvPracticeNote).apply {
                setText(R.string.saved_offline_note)
                visibility = View.VISIBLE
            }
        }

        playEntrance(score, total, percent, starCount)
    }

    private fun playEntrance(score: Int, total: Int, percent: Int, starCount: Int) {
        val celebrate = percent >= 75
        val buddy = findViewById<PronunciationBookView>(R.id.buddy)
        val scoreCard = findViewById<View>(R.id.scoreCard)
        val tvScore = findViewById<TextView>(R.id.tvScore)
        val ring = findViewById<CircularProgressIndicator>(R.id.scoreRing)
        val confetti = findViewById<ConfettiView>(R.id.confettiView)
        val stars = listOf<ImageView>(findViewById(R.id.star1), findViewById(R.id.star2), findViewById(R.id.star3))
        val density = resources.displayMetrics.density

        buddy.setMood(if (celebrate) PronunciationBookView.Mood.HAPPY else PronunciationBookView.Mood.CHEER)

        scoreCard.alpha = 0f
        scoreCard.translationY = 40f * density
        scoreCard.animate().alpha(1f).translationY(0f).setStartDelay(150).setDuration(450)
            .setInterpolator(DecelerateInterpolator()).start()

        // Score counts up while the ring fills to the percentage
        tvScore.text = getString(R.string.qz_score, 0, total)
        tvScore.contentDescription = getString(R.string.qz_score, score, total)
        ring.setProgressCompat(0, false)
        countAnimator = ValueAnimator.ofInt(0, score).apply {
            startDelay = 500
            duration = (400L + score * 120L).coerceAtMost(1500L)
            interpolator = DecelerateInterpolator()
            addUpdateListener { tvScore.text = getString(R.string.qz_score, it.animatedValue as Int, total) }
            start()
        }
        ring.postDelayed({ ring.setProgressCompat(percent, true) }, 500)

        stars.forEachIndexed { i, star ->
            star.scaleX = 0f
            star.scaleY = 0f
            val earned = i < starCount
            if (earned) star.setColorFilter(ContextCompat.getColor(this, R.color.qz_gold))
            star.animate()
                .scaleX(1f).scaleY(1f)
                .setStartDelay(800L + i * 260L)
                .setDuration(420)
                .setInterpolator(OvershootInterpolator(if (earned) 3.5f else 1.5f))
                .withStartAction { if (earned) star.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
                .start()
        }

        if (celebrate) {
            confetti.postDelayed({ confetti.rain(150) }, 700)
        }
    }

    override fun onDestroy() {
        countAnimator?.cancel()
        super.onDestroy()
    }
}
