package com.example.basaya.ui.game

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.TextViewCompat
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.basaya.R
import com.example.basaya.data.database.AppDatabase
import com.example.basaya.data.repository.CrosswordRepository
import kotlinx.coroutines.launch
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await


class CrosswordCompleteScreen : AppCompatActivity() {
    private lateinit var btnNext: Button
    private lateinit var lessonId: String
    private lateinit var tvLessonTitle: TextView
    private lateinit var tvLevel: TextView
    private var buttonPulse: ObjectAnimator? = null

    private val db by lazy {
        AppDatabase.getDatabase(this)
    }

    private val firestore by lazy { FirebaseFirestore.getInstance() }

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
        tvLevel = findViewById(R.id.tvLevel)
        btnNext = findViewById(R.id.btnNext)

        title.text = ""

        title.alpha = 0f
        title.translationY = -40f

        levelBox.alpha = 0f
        levelBox.scaleX = 0.85f
        levelBox.scaleY = 0.85f

        // Hidden until the level loads; popInButton() brings it in.
        btnNext.alpha = 0f

        lifecycleScope.launch {

            val lessonDocument = firestore
                .collection("lessons")
                .document(lessonId)
                .get()
                .await()

            title.text = lessonDocument.getString("title") ?: "BaSaya"

            val repo = CrosswordRepository(this@CrosswordCompleteScreen)
            val levels = repo.getLevels(lessonId)
            val progress = db.crosswordProgressDao().getProgress(lessonId)

            val allLevels = levels.size

            // NEXT_LEVEL is actually the level the player just finished
            val currentLevel = intent.getIntExtra("NEXT_LEVEL", 1)

            progressText.text = "$currentLevel / $allLevels"
            tvLevel.text = currentLevel.toString()

            progressBar.max = allLevels
            progressBar.progress = currentLevel

            if (currentLevel < allLevels) {

                styleButton(R.string.cw_next_level, R.drawable.cw_btn_next, R.drawable.ic_lesson_chevron)

                btnNext.setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

                    startActivity(
                        Intent(
                            this@CrosswordCompleteScreen,
                            CrosswordActivity::class.java
                        ).apply {
                            putExtra("LESSON_ID", lessonId)
                        },
                        ActivityOptions.makeCustomAnimation(
                            this@CrosswordCompleteScreen,
                            0,
                            0
                        ).toBundle()
                    )

                    finish()
                }

            } else {

                progressBar.progress = allLevels
                progressText.text = "$allLevels / $allLevels"

                styleButton(R.string.cw_all_done, R.drawable.cw_btn_done, R.drawable.cw_ic_star)

                btnNext.setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    finish()
                }
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

            popInButton()
        }

    }

    override fun onDestroy() {
        buttonPulse?.cancel()
        super.onDestroy()
    }

    private fun styleButton(@StringRes text: Int, @DrawableRes background: Int, @DrawableRes icon: Int) {
        btnNext.setText(text)
        btnNext.setBackgroundResource(background)
        btnNext.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, icon, 0)
        TextViewCompat.setCompoundDrawableTintList(btnNext, ColorStateList.valueOf(Color.WHITE))
    }

    /** The button bounces in after the level card, then gently "breathes" to invite a tap. */
    private fun popInButton() {
        btnNext.scaleX = 0.6f
        btnNext.scaleY = 0.6f
        btnNext.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(750)
            .setDuration(450)
            .setInterpolator(OvershootInterpolator(2.2f))
            .withEndAction {
                buttonPulse = ObjectAnimator.ofPropertyValuesHolder(
                    btnNext,
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.06f, 1f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.06f, 1f)
                ).apply {
                    duration = 1400
                    startDelay = 400
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                    start()
                }
            }
            .start()
    }
}