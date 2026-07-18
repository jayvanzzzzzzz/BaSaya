package com.example.basaya.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.basaya.R
import com.example.basaya.data.repository.LectureRepository
import com.example.basaya.model.Lecture
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class LectureActivity : AppCompatActivity() {

    private lateinit var lessonId: String
    private lateinit var sentenceContainer: LinearLayout
    private lateinit var scrollContent: ScrollView
    private lateinit var btnNextPage: Button
    private lateinit var ibSpeaker: ImageButton
    private lateinit var ibClose: ImageButton
    private lateinit var llProgressBars: LinearLayout
    private lateinit var tvContinueHint: TextView

    private var speakerOn: Boolean = true
    private var mediaPlayer: MediaPlayer? = null

    private lateinit var lecture: Lecture
    private var currentPageIndex = 0
    private var currentSentenceIndex = 0

    private lateinit var lectureRepository: LectureRepository
    private lateinit var gestureDetector: GestureDetector
    private val progressSegments = mutableListOf<View>()

    private val colorIncomplete = Color.parseColor("#D9D9D9")
    private val colorComplete = Color.parseColor("#2196F3")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lecture)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        lessonId = intent.getStringExtra("LESSON_ID") ?: ""

        tvContinueHint = findViewById(R.id.tvContinueHint)

        if (lessonId.isBlank()) {
            Toast.makeText(this, "Missing lesson data.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        ibSpeaker = findViewById(R.id.ibSpeaker)
        ibSpeaker.setImageResource(R.drawable.ic_speaker_on)
        ibSpeaker.setOnClickListener {
            if (speakerOn) {
                ibSpeaker.setImageResource(R.drawable.ic_speaker_mute)
                speakerOn = false
            } else {
                ibSpeaker.setImageResource(R.drawable.ic_speaker_on)
                speakerOn = true
            }
        }

        sentenceContainer = findViewById(R.id.allSentenceContainer)
        scrollContent = findViewById(R.id.scrollContent)
        btnNextPage = findViewById(R.id.btnNextPage)
        llProgressBars = findViewById(R.id.llProgressBars)

        ibClose = findViewById(R.id.ibClose)
        ibClose.setOnClickListener {
            showConfirmDialog()
        }

        lectureRepository = LectureRepository(this)

        lifecycleScope.launch {
            val fetchedLecture = lectureRepository.getLecture(lessonId)

            if (fetchedLecture == null || fetchedLecture.pages.isEmpty()) {
                Toast.makeText(this@LectureActivity, "Lecture content not found.", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            lecture = fetchedLecture

            setupProgressBars()
            showPage(currentPageIndex)

            gestureDetector = GestureDetector(this@LectureActivity, object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    revealNextSentence()
                    return true
                }

                override fun onDown(e: MotionEvent): Boolean {
                    return true
                }
            })

            scrollContent.setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                false
            }

            btnNextPage.setOnClickListener {
                goToNextPage()
            }
        }
    }

    private fun setupProgressBars() {
        llProgressBars.removeAllViews()
        progressSegments.clear()

        val pageCount = lecture.pages.size
        val gapPx = (4 * resources.displayMetrics.density).toInt()
        val barHeightPx = (6 * resources.displayMetrics.density).toInt()

        for (i in 0 until pageCount) {
            val segment = View(this)
            val topMarginPx = (35 * resources.displayMetrics.density).toInt()

            val params = LinearLayout.LayoutParams(0, barHeightPx, 1f).apply {
                setMargins(
                    if (i != 0) gapPx else 0,
                    topMarginPx,
                    0,
                    0
                )
            }

            segment.layoutParams = params
            segment.background = makeBarDrawable(colorIncomplete)
            llProgressBars.addView(segment)
            progressSegments.add(segment)
        }
    }

    private fun makeBarDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8f
            setColor(color)
        }
    }

    private fun updateProgressBars() {
        for (i in progressSegments.indices) {
            val isComplete = when {
                i < currentPageIndex -> true
                i == currentPageIndex -> currentSentenceIndex >= lecture.pages[i].sentences.size
                else -> false
            }
            progressSegments[i].background =
                makeBarDrawable(if (isComplete) colorComplete else colorIncomplete)
        }
    }

    private fun showPage(pageIndex: Int) {
        stopAudio()
        sentenceContainer.removeAllViews()
        currentSentenceIndex = 0
        btnNextPage.visibility = View.GONE
        tvContinueHint.visibility = View.VISIBLE
        revealNextSentence()
    }

    private fun revealNextSentence() {
        val page = lecture.pages[currentPageIndex]

        if (currentSentenceIndex < page.sentences.size) {
            val sentence = page.sentences[currentSentenceIndex]
            addBulletTextView(sentence)

            page.audioResNames.getOrNull(currentSentenceIndex)?.let { resName ->
                playSentenceAudio(resName)
            }

            currentSentenceIndex++

            if (currentSentenceIndex == page.sentences.size) {
                btnNextPage.visibility = View.VISIBLE
                tvContinueHint.visibility = View.GONE

                btnNextPage.text = if (currentPageIndex == lecture.pages.lastIndex) {
                    "TAPUSIN"
                } else {
                    "SUSUNOD"
                }
            }

            updateProgressBars()

            scrollContent.post {
                scrollContent.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    private fun addBulletTextView(sentence: String) {
        val tv = TextView(this).apply {
            text = HtmlCompat.fromHtml(
                "&#8226; $sentence",
                HtmlCompat.FROM_HTML_MODE_LEGACY
            )
            textSize = 18f
            gravity = Gravity.START
            setPadding(0, 0, 0, 24)
        }
        sentenceContainer.addView(tv)
    }

    private fun goToNextPage() {
        if (currentPageIndex < lecture.pages.lastIndex) {
            currentPageIndex++
            showPage(currentPageIndex)
        } else {
            stopAudio()

            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid != null) {
                lifecycleScope.launch {
                    try {
                        lectureRepository.markLectureFinished(uid, lessonId)
                    } catch (e: Exception) {
                        Log.e("LectureActivity", "Failed to mark lecture finished", e)
                    }
                    finish()
                }
            } else {
                finish()
            }
        }
    }

    private fun playSentenceAudio(resName: String) {
        stopAudio()

        if (!speakerOn || resName.isBlank()) return

        val resId = resources.getIdentifier(resName, "raw", packageName)
        if (resId == 0) {
            return
        }

        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(this, resId)
        mediaPlayer?.start()
    }

    private fun stopAudio() {
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
    }

    private fun showConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("Lumabas sa Aralin")
            .setMessage("Sigurado ka bang gusto mong lumabas? Mawawala ang iyong kasalukuyang progreso sa pahinang ito.")
            .setPositiveButton("Oo") { dialog, _ ->
                stopAudio()
                finish()
            }
            .setNegativeButton("Hindi") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}