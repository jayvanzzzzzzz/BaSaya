package com.example.basaya.ui.game

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import kotlin.math.cos
import kotlin.math.sin
import android.animation.ObjectAnimator
import android.content.Intent
import android.view.animation.LinearInterpolator
import android.app.ActivityOptions
import androidx.core.animation.doOnEnd
import androidx.lifecycle.lifecycleScope
import com.example.basaya.R
import com.example.basaya.data.mock.CrosswordMockData
import com.example.basaya.data.entity.CrosswordProgress
import com.example.basaya.data.database.AppDatabase
import com.example.basaya.data.repository.CrosswordRepository
import com.example.basaya.model.CrosswordGameLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CrosswordActivity : AppCompatActivity() {

    private var allGameLevels: List<CrosswordGameLevel> = emptyList()
    private lateinit var lessonId: String

    private val db by lazy {
        AppDatabase.getDatabase(this@CrosswordActivity)
    }

    private var findWord = mutableListOf<String>()

    private var isSelecting = false

    private val letterViews = mutableListOf<TextView>()
    private val selectedLetters = mutableListOf<TextView>()

    private lateinit var lineView: LineView
    private lateinit var tempTv: TextView
    private lateinit var wordContainer: LinearLayout

    val wordLetterBoxes = mutableListOf<List<TextView>>()

    private var currentLevel: CrosswordGameLevel? = null
    private var currentLevelWords: List<String>? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_crossword)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val container =
            findViewById<ConstraintLayout>(R.id.letterContainer)

        lessonId = intent.getStringExtra("LESSON_ID") ?: ""

        //fetch the current level words
        lifecycleScope.launch {
            // sync and load from Firestore to Room
            val repo = CrosswordRepository(this@CrosswordActivity)
            repo.syncLevels(lessonId)
            allGameLevels = repo.getLevels(lessonId)

            val progress = db.gameProgressDao().getProgress()
            val currentLevelNumber = progress?.currentLevel ?: 1

            currentLevel = allGameLevels.firstOrNull { it.level == currentLevelNumber }
            currentLevelWords = currentLevel?.words

            wordContainer = findViewById(R.id.wordContainer)

            //create boxes for word
            currentLevelWords?.forEach { word ->

                val row = LinearLayout(this@CrosswordActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    clipChildren = false
                    clipToPadding = false
                }

                val letterBoxes = mutableListOf<TextView>()

                repeat(word.length) {

                    val tv = TextView(this@CrosswordActivity).apply {

                        val size = (40 * resources.displayMetrics.density).toInt()

                        text = ""
                        gravity = Gravity.CENTER

                        textSize = 24f

                        includeFontPadding = false

                        elevation = 12f

                        layoutParams = LinearLayout.LayoutParams(
                            size,
                            size
                        ).apply {
                            setMargins(0, 0, 5, 15)
                        }

                        background = ContextCompat.getDrawable(
                            this@CrosswordActivity,
                            R.drawable.letter_box_background
                        )
                    }

                    row.addView(tv)
                    letterBoxes.add(tv)

                }

                wordLetterBoxes.add(letterBoxes)
                wordContainer.addView(row)

            }

            // restore found words
            val savedFoundWords = progress?.foundWords ?: ""
            if (savedFoundWords.isNotEmpty()) {
                findWord.addAll(savedFoundWords.split(","))
            }

            // restore UI for already found words
            findWord.forEach { foundWord ->
                val wordIndex = currentLevelWords?.indexOf(foundWord) ?: -1
                if (wordIndex != -1) {
                    val boxes = wordLetterBoxes[wordIndex]
                    boxes.forEachIndexed { i, box ->
                        box.text = foundWord[i].toString()
                        box.background = ContextCompat.getDrawable(
                            this@CrosswordActivity,
                            R.drawable.correct_word_tv_bg
                        )
                        box.typeface =
                            ResourcesCompat.getFont(this@CrosswordActivity, R.font.lexend)
                        box.setTextColor(android.graphics.Color.WHITE)
                    }
                }
            }

            //circle touchpad for letters
            val letters =
                currentLevelWords?.maxByOrNull { it.length }?.map { it.toString() }.orEmpty()
                    .shuffled()

            container.post {
                lineView = LineView(this@CrosswordActivity)
                container.addView(
                    lineView,
                    ConstraintLayout.LayoutParams(
                        ConstraintLayout.LayoutParams.MATCH_PARENT,
                        ConstraintLayout.LayoutParams.MATCH_PARENT
                    )
                )

                val centerX = container.width / 2f
                val centerY = container.height / 2f

                val density = resources.displayMetrics.density
                val letterSizePx = (56 * density).toInt() // pick a dp size you like

                // radius = half the smaller container dimension, minus half a letter bubble
                // so bubbles don't clip outside the container edge
                val inset = 10f * resources.displayMetrics.density
                val radius = (minOf(container.width, container.height) / 2f) - (letterSizePx / 2f) - inset

                val angleStep = 360.0 / letters.size

                for (i in letters.indices) {
                    val angleRad = Math.toRadians(i * angleStep - 90)

                    val x = centerX + (radius * cos(angleRad)).toFloat()
                    val y = centerY + (radius * sin(angleRad)).toFloat()

                    val letterView = TextView(this@CrosswordActivity).apply {
                        text = letters[i]
                        textSize = 40f
                        layoutParams = ViewGroup.LayoutParams(letterSizePx, letterSizePx)
                        gravity = Gravity.CENTER
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setTextColor(android.graphics.Color.BLACK)
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    }

                    container.addView(letterView)
                    letterViews.add(letterView)

                    letterView.post {
                        letterView.x = x - letterView.width / 2f
                        letterView.y = y - letterView.height / 2f
                    }
                }
            }
        }

        tempTv = findViewById(R.id.tempTv)
        tempTv.visibility = View.INVISIBLE

        container.setOnTouchListener { _, event ->

            val touchX = event.x
            val touchY = event.y

            when (event.action) {

                MotionEvent.ACTION_DOWN -> {

                    val touchLetter = findTouchLetter(touchX, touchY)

                    if (touchLetter != null) {

                        isSelecting = true

                        selectedLetters.clear()
                        selectedLetters.add(touchLetter)

                        lineView.selectedPoints.clear()

                        val centerX = touchLetter.x + touchLetter.width / 2f
                        val centerY = touchLetter.y + touchLetter.height / 2f

                        lineView.selectedPoints.add(centerX to centerY)

                        lineView.invalidate()

                        touchLetter.isSelected = true

                        touchLetter.background =
                            ContextCompat.getDrawable(this, R.drawable.blue_circle_background)

                        val word = selectedLetters.joinToString("") {
                            it.text.toString()
                        }

                        tempTv.text = word

                        tempTv.visibility = View.VISIBLE
                    }

                }

                MotionEvent.ACTION_MOVE -> {

                    if (!isSelecting) return@setOnTouchListener true

                    lineView.fingerX = touchX
                    lineView.fingerY = touchY
                    lineView.invalidate()

                    val touchedLetter = findTouchLetter(touchX, touchY)

                    if (touchedLetter != null &&
                        !selectedLetters.contains(touchedLetter)
                    ) {

                        selectedLetters.add(touchedLetter)

                        val centerX = touchedLetter.x + touchedLetter.width / 2f
                        val centerY = touchedLetter.y + touchedLetter.height / 2f

                        lineView.selectedPoints.add(centerX to centerY)

                        val word = selectedLetters.joinToString("") {
                            it.text.toString()
                        }

                        tempTv.text = word

                        touchedLetter.isSelected = true

                        touchedLetter.background =
                            ContextCompat.getDrawable(this, R.drawable.blue_circle_background)
                    }
                }

                MotionEvent.ACTION_UP -> {

                    val word = selectedLetters.joinToString("") {
                        it.text.toString()
                    }

                    // Validate word here
                    // Toast.makeText(this, word, Toast.LENGTH_SHORT).show()

                    //words {"ALAWS", "WALA"}
                    //wordLetterBox {{"A,L,A,W,S"},{"W,A,L,A"}}

                    //check the word
                    if (currentLevelWords?.contains(word) == true) {
                        val wordIndex = currentLevelWords?.indexOf(word) ?: -1
                        if (wordIndex == -1) return@setOnTouchListener false
                        val boxes = wordLetterBoxes[wordIndex]

                        val handler = android.os.Handler(mainLooper)

                        //box animation
                        boxes.forEachIndexed { i, box ->

                            handler.postDelayed({

                                //box animation to put the word in the box
                                if (box.text == null || box.text == "") {
                                    box.animate()
                                        .scaleX(1.4f)
                                        .scaleY(1.4f)
                                        .setDuration(120)
                                        .withEndAction {
                                            box.animate()
                                                .scaleX(0.9f)
                                                .scaleY(0.9f)
                                                .setDuration(80)
                                                .withEndAction {
                                                    box.animate()
                                                        .scaleX(1f)
                                                        .scaleY(1f)
                                                        .setDuration(80)
                                                        .start()
                                                }
                                                .start()
                                        }
                                        .start()
                                }

                                box.text = word[i].toString()
                                box.background = ContextCompat.getDrawable(
                                    this@CrosswordActivity,
                                    R.drawable.correct_word_tv_bg
                                )
                                box.setTextColor(android.graphics.Color.WHITE)
                                box.typeface =
                                    ResourcesCompat.getFont(this@CrosswordActivity, R.font.lexend)


                            }, i * 100L) // delay increases per box

                            //box animation if the word already exist
                            if (!box.text.isNullOrEmpty()) {

                                repeat(2) { index ->
                                    box.postDelayed({
                                        box.animate()
                                            .scaleX(1.4f)
                                            .scaleY(1.4f)
                                            .setDuration(120)
                                            .withEndAction {
                                                box.animate()
                                                    .scaleX(0.9f)
                                                    .scaleY(0.9f)
                                                    .setDuration(80)
                                                    .withEndAction {
                                                        box.animate()
                                                            .scaleX(1f)
                                                            .scaleY(1f)
                                                            .setDuration(80)
                                                            .start()
                                                    }
                                                    .start()
                                            }
                                            .start()
                                    }, index * 300L)
                                }
                            }

                            tempTv.animate()
                                .scaleX(1.4f)
                                .scaleY(1.4f)
                                .setDuration(120).withStartAction {
                                    tempTv.setTextColor(
                                        ContextCompat.getColor(
                                            this@CrosswordActivity,
                                            R.color.green
                                        )
                                    )
                                }
                                .withEndAction {
                                    tempTv.animate()
                                        .scaleX(0.9f)
                                        .scaleY(0.9f)
                                        .setDuration(80)
                                        .withEndAction {
                                            tempTv.animate()
                                                .scaleX(1f)
                                                .scaleY(1f)
                                                .setDuration(80)
                                                .start()
                                            tempTv.visibility = View.INVISIBLE
                                            tempTv.text = ""
                                            tempTv.setTextColor(
                                                ContextCompat.getColor(
                                                    this@CrosswordActivity,
                                                    R.color.black
                                                )
                                            )
                                        }
                                        .start()
                                }
                                .start()
                        }

                    } else {

                        tempTv.setTextColor(ContextCompat.getColor(this@CrosswordActivity, R.color.red))

                        ObjectAnimator.ofFloat(
                            tempTv,
                            "translationX",
                            0f, -20f, 20f, -15f, 15f, -8f, 8f, 0f
                        ).apply {
                            duration = 350
                            interpolator = LinearInterpolator()
                            doOnEnd {
                                tempTv.setTextColor(
                                    ContextCompat.getColor(
                                        this@CrosswordActivity,
                                        R.color.black
                                    )
                                )
                                tempTv.visibility = View.INVISIBLE
                                tempTv.text = ""
                            }
                            start()
                        }

                    }

                    //save word
                    if (currentLevelWords?.contains(word) == true) {
                        if (!findWord.contains(word)) { // check word if already in the list
                            findWord.add(word)

                            // only save to DB when it's a new word
                            lifecycleScope.launch {
                                val progress = db.gameProgressDao().getProgress()
                                val currentFound = progress?.foundWords ?: ""
                                val foundList = if (currentFound.isEmpty()) mutableListOf()
                                else currentFound.split(",").toMutableList()

                                if (!foundList.contains(word)) {
                                    foundList.add(word)
                                }

                                db.gameProgressDao().saveProgress(
                                    CrosswordProgress(
                                        currentLevel = progress?.currentLevel ?: 1,
                                        completedLevel = progress?.completedLevel ?: 0,
                                        foundWords = foundList.joinToString(",")
                                    )
                                )
                            }
                        }
                    }

                    //check if complete
                    val completed = findWord.sorted() == currentLevelWords?.sorted()

                    if (completed) {

                        lifecycleScope.launch {
                            val progress = db.gameProgressDao().getProgress()
                            val currentLevelNumber = progress?.currentLevel ?: 1
                            val nextLevel = currentLevelNumber + 1

                            //save progress
                            db.gameProgressDao().saveProgress(
                                CrosswordProgress(
                                    currentLevel = nextLevel,
                                    completedLevel = 1,
                                    foundWords = ""
                                )
                            )
                            delay(2000)
                            val intent = Intent(this@CrosswordActivity, CrosswordCompleteScreen::class.java).apply {
                                putExtra("NEXT_LEVEL", currentLevelNumber)
                                putExtra("LESSON_ID", lessonId)
                            }
                            val options =
                                ActivityOptions.makeCustomAnimation(this@CrosswordActivity, 0, 0)
                            startActivity(intent, options.toBundle())
                            finish()
                        }

                    }

                    selectedLetters.forEach {
                        it.isSelected = false

                        it.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }

                    lineView.selectedPoints.clear()
                    lineView.invalidate()

                    selectedLetters.clear()
                    isSelecting = false

                }

            }
            true
        }

    }

    private fun findTouchLetter(
        touchX: Float,
        touchY: Float
    ): TextView? {

        for (letter in letterViews) {

            val centerX = letter.x + letter.width / 2f
            val centerY = letter.y + letter.height / 2f

            val radius = letter.width / 2f

            val distance = kotlin.math.sqrt(
                (touchX - centerX) * (touchX - centerX) +
                        (touchY - centerY) * (touchY - centerY)
            )

            if (distance <= radius) {
                return letter
            }
        }

        return null
    }

}