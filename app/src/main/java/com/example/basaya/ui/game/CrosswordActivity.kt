package com.example.basaya.ui.game

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
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
import android.widget.Toast
import androidx.core.animation.doOnEnd
import androidx.lifecycle.lifecycleScope
import com.example.basaya.R
import com.example.basaya.data.entity.CrosswordProgress
import com.example.basaya.data.database.AppDatabase
import com.example.basaya.data.repository.CrosswordRepository
import com.example.basaya.data.model.CrosswordGameLevel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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

        lifecycleScope.launch {
            val repo = CrosswordRepository(this@CrosswordActivity)

            // Room-only read first — this lesson may already be downloaded.
            allGameLevels = repo.getLevels(lessonId)

            if (allGameLevels.isEmpty()) {
                // Not downloaded yet — fetch directly from Firestore for THIS
                // session only. Does not write to Room; only the Download
                // button (LessonRepository.downloadLesson -> syncLevels) does.
                allGameLevels = repo.fetchLevelsRemote(lessonId)
            }

            // Student's own progress state (not lesson content) — still fine
            // to persist locally regardless of download status.
            repo.syncProgress(lessonId)

            if (allGameLevels.isEmpty()) {
                Log.e("CrosswordActivity", "No game levels found for lessonId=$lessonId — check Firestore 'game' subcollection")
                Toast.makeText(this@CrosswordActivity, "Walang laro para sa araling ito.", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            val progress = db.crosswordProgressDao().getProgress(lessonId)
            var currentLevelNumber = progress?.currentLevel ?: 1

            val maxLevel = allGameLevels.maxOf { it.level }
            if (currentLevelNumber > maxLevel) {
                Log.w("CrosswordActivity", "currentLevel ($currentLevelNumber) exceeds max level ($maxLevel) — clamping")
                currentLevelNumber = maxLevel
            }

            currentLevel = allGameLevels.firstOrNull { it.level == currentLevelNumber }
            currentLevelWords = currentLevel?.words

            if (currentLevel == null) {
                Log.e("CrosswordActivity", "No matching level entity for level=$currentLevelNumber among ${allGameLevels.map { it.level }}")
                Toast.makeText(this@CrosswordActivity, "May problema sa pag-load ng laro.", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            wordContainer = findViewById(R.id.wordContainer)

            //box generation
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

                        val size = (30 * resources.displayMetrics.density).toInt()

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

            val savedFoundWords = progress?.foundWords ?: ""
            if (savedFoundWords.isNotEmpty()) {
                findWord.addAll(savedFoundWords.split(","))
            }

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

            val letters =
                currentLevelWords?.maxByOrNull { it.length }?.map { it.toString() }.orEmpty()
                    .shuffled()

            //line view and letter generation
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
                val letterSizePx = (56 * density).toInt()

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


        //touch controls
        container.setOnTouchListener { _, event ->

            if (!::lineView.isInitialized) return@setOnTouchListener false

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
                        lineView.fingerX = touchX
                        lineView.fingerY = touchY
                        lineView.invalidate()

                        touchLetter.isSelected = true
                        touchLetter.background =
                            ContextCompat.getDrawable(this, R.drawable.blue_circle_background)

                        val word = selectedLetters.joinToString("") { it.text.toString() }
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

                    if (touchedLetter != null && !selectedLetters.contains(touchedLetter)) {

                        selectedLetters.add(touchedLetter)

                        val centerX = touchedLetter.x + touchedLetter.width / 2f
                        val centerY = touchedLetter.y + touchedLetter.height / 2f

                        lineView.selectedPoints.add(centerX to centerY)

                        val word = selectedLetters.joinToString("") { it.text.toString() }
                        tempTv.text = word

                        touchedLetter.isSelected = true
                        touchedLetter.background =
                            ContextCompat.getDrawable(this, R.drawable.blue_circle_background)
                    }
                }

                MotionEvent.ACTION_UP -> {

                    val word = selectedLetters.joinToString("") { it.text.toString() }

                    if (currentLevelWords?.contains(word) == true) {
                        val wordIndex = currentLevelWords?.indexOf(word) ?: -1
                        if (wordIndex == -1) return@setOnTouchListener false
                        val boxes = wordLetterBoxes[wordIndex]

                        val handler = android.os.Handler(mainLooper)

                        boxes.forEachIndexed { i, box ->

                            handler.postDelayed({

                                if (box.text == null || box.text == "") {
                                    box.animate()
                                        .scaleX(1.4f).scaleY(1.4f).setDuration(120)
                                        .withEndAction {
                                            box.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80)
                                                .withEndAction {
                                                    box.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                                                }.start()
                                        }.start()
                                }

                                box.text = word[i].toString()
                                box.background = ContextCompat.getDrawable(
                                    this@CrosswordActivity,
                                    R.drawable.correct_word_tv_bg
                                )
                                box.setTextColor(android.graphics.Color.WHITE)
                                box.typeface =
                                    ResourcesCompat.getFont(this@CrosswordActivity, R.font.lexend)

                            }, i * 100L)

                            if (!box.text.isNullOrEmpty()) {
                                repeat(2) { index ->
                                    box.postDelayed({
                                        box.animate()
                                            .scaleX(1.4f).scaleY(1.4f).setDuration(120)
                                            .withEndAction {
                                                box.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80)
                                                    .withEndAction {
                                                        box.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                                                    }.start()
                                            }.start()
                                    }, index * 300L)
                                }
                            }

                            tempTv.animate()
                                .scaleX(1.4f).scaleY(1.4f).setDuration(120)
                                .withStartAction {
                                    tempTv.setTextColor(
                                        ContextCompat.getColor(this@CrosswordActivity, R.color.green)
                                    )
                                }
                                .withEndAction {
                                    tempTv.animate()
                                        .scaleX(0.9f).scaleY(0.9f).setDuration(80)
                                        .withEndAction {
                                            tempTv.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                                            tempTv.visibility = View.INVISIBLE
                                            tempTv.text = ""
                                            tempTv.setTextColor(
                                                ContextCompat.getColor(this@CrosswordActivity, R.color.black)
                                            )
                                        }.start()
                                }.start()
                        }

                    } else {

                        tempTv.setTextColor(ContextCompat.getColor(this@CrosswordActivity, R.color.red))

                        ObjectAnimator.ofFloat(
                            tempTv, "translationX",
                            0f, -20f, 20f, -15f, 15f, -8f, 8f, 0f
                        ).apply {
                            duration = 350
                            interpolator = LinearInterpolator()
                            doOnEnd {
                                tempTv.setTextColor(
                                    ContextCompat.getColor(this@CrosswordActivity, R.color.black)
                                )
                                tempTv.visibility = View.INVISIBLE
                                tempTv.text = ""
                            }
                            start()
                        }
                    }

                    // save newly found word
                    if (currentLevelWords?.contains(word) == true && !findWord.contains(word)) {
                        findWord.add(word)

                        lifecycleScope.launch {
                            val progress = db.crosswordProgressDao().getProgress(lessonId)
                            val currentFound = progress?.foundWords ?: ""
                            val foundList = if (currentFound.isEmpty()) mutableListOf()
                            else currentFound.split(",").toMutableList()

                            if (!foundList.contains(word)) {
                                foundList.add(word)
                            }

                            db.crosswordProgressDao().saveProgress(
                                CrosswordProgress(
                                    lessonId = lessonId,
                                    currentLevel = progress?.currentLevel ?: 1,
                                    completedLevel = progress?.completedLevel ?: 0,
                                    foundWords = foundList.joinToString(",")
                                )
                            )

                            // check completion AFTER this word is saved
                            val completed = findWord.sorted() == currentLevelWords?.sorted()

                            if (completed) {
                                val currentLevelNumber = progress?.currentLevel ?: 1
                                val nextLevel = currentLevelNumber + 1
                                val totalLevels = allGameLevels.size

                                db.crosswordProgressDao().saveProgress(
                                    CrosswordProgress(
                                        lessonId = lessonId,
                                        currentLevel = nextLevel,
                                        completedLevel = 1,
                                        foundWords = ""
                                    )
                                )

                                val uid = FirebaseAuth.getInstance().currentUser?.uid
                                if (uid != null) {
                                    try {
                                        FirebaseFirestore.getInstance()
                                            .collection("users")
                                            .document(uid)
                                            .collection("assignedLessons")
                                            .document(lessonId)
                                            .set(
                                                mapOf(
                                                    "gameScore" to currentLevelNumber,
                                                    "gameTotal" to totalLevels,
                                                    "gameFinished" to (currentLevelNumber >= totalLevels),
                                                    "gameCompletedAt" to FieldValue.serverTimestamp()
                                                ),
                                                com.google.firebase.firestore.SetOptions.merge()
                                            )
                                            .await()
                                    } catch (e: Exception) {
                                        Log.e("CrosswordActivity", "Failed to sync game progress", e)
                                    }
                                }

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