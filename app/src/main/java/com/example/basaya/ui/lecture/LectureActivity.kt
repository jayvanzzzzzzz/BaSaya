package com.example.basaya.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.basaya.R
import com.example.basaya.data.audio.LectureAudioCache
import com.example.basaya.data.audio.LectureAudioStore
import com.example.basaya.data.repository.LectureRepository
import com.example.basaya.data.model.Lecture
import com.example.basaya.data.model.LecturePage
import com.example.basaya.data.progress.LessonResume
import com.example.basaya.ui.lecture.TalkingBookView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import androidx.activity.OnBackPressedCallback

class LectureActivity : AppCompatActivity() {

    companion object {
        /** Open with the speaker off. */
        const val EXTRA_MUTED = "MUTED"
    }

    private lateinit var lessonId: String
    private lateinit var sentenceContainer: LinearLayout
    private lateinit var scrollContent: ScrollView
    private lateinit var btnNextPage: Button
    private lateinit var ibSpeaker: ImageButton
    private lateinit var ibClose: ImageButton
    private lateinit var llProgressBars: LinearLayout
    private lateinit var tvContinueHint: TextView
    private lateinit var tvLectureTitle: TextView
    private lateinit var tvPageLabel: TextView
    private lateinit var talkingBook: TalkingBookView
    private var activeSentenceView: TextView? = null

    private var speakerOn: Boolean = true
    private var mediaPlayer: MediaPlayer? = null

    private lateinit var lecture: Lecture
    private var currentPageIndex = 0
    private var currentSentenceIndex = 0
    /** False while rereading a finished lecture: then the place isn't saved. */
    private var tracking = false

    private lateinit var lectureRepository: LectureRepository
    private lateinit var gestureDetector: GestureDetector
    private val progressSegments = mutableListOf<View>()

    private val colorIncomplete by lazy { ContextCompat.getColor(this, R.color.lecture_progress_track) }
    private val colorComplete by lazy { ContextCompat.getColor(this, R.color.lecture_primary) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lecture)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showConfirmDialog()
            }
        })

        lessonId = intent.getStringExtra("LESSON_ID") ?: ""

        tvContinueHint = findViewById(R.id.tvContinueHint)

        if (lessonId.isBlank()) {
            Toast.makeText(this, "Missing lesson data.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        ibSpeaker = findViewById(R.id.ibSpeaker)
        // The child chose "Buksan nang walang tunog" when the audio wouldn't load.
        speakerOn = !intent.getBooleanExtra(EXTRA_MUTED, false)
        ibSpeaker.setImageResource(if (speakerOn) R.drawable.ic_speaker_on else R.drawable.ic_speaker_mute)
        ibSpeaker.setOnClickListener { toggleSpeaker() }

        sentenceContainer = findViewById(R.id.allSentenceContainer)
        scrollContent = findViewById(R.id.scrollContent)
        btnNextPage = findViewById(R.id.btnNextPage)
        llProgressBars = findViewById(R.id.llProgressBars)
        tvLectureTitle = findViewById(R.id.tvLectureTitle)
        tvPageLabel = findViewById(R.id.tvPageLabel)
        talkingBook = findViewById(R.id.talkingBook)

        // Tapping the book replays the sentence just read (and turns sound back on if muted).
        talkingBook.setOnClickListener {
            if (!::lecture.isInitialized || currentSentenceIndex == 0) return@setOnClickListener
            if (!speakerOn) toggleSpeaker()
            playSentenceAudio(lecture.pages[currentPageIndex], currentSentenceIndex - 1)
        }

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
            tvLectureTitle.text = lecture.title.ifBlank { getString(R.string.lecture_default_title) }

            // Continue on the next unread page (here or from the website). Rereading a finished
            // lecture always starts at page 1 and saves nothing.
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            val progress = uid?.let { LessonResume.loadProgress(it, lessonId) }
            tracking = uid != null && progress?.getBoolean("lectureFinished") != true
            currentPageIndex = LessonResume.lectureStartPage(progress, lecture.pages.size)

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
        val gapPx = (8 * resources.displayMetrics.density).toInt()
        val barHeightPx = (8 * resources.displayMetrics.density).toInt()

        for (i in 0 until pageCount) {
            val segment = View(this)
            val params = LinearLayout.LayoutParams(0, barHeightPx, 1f).apply {
                setMargins(if (i != 0) gapPx else 0, 0, 0, 0)
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
            cornerRadius = 8 * resources.displayMetrics.density
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
        activeSentenceView = null
        currentSentenceIndex = 0
        tvPageLabel.text = getString(R.string.lecture_page_label, pageIndex + 1, lecture.pages.size)
        btnNextPage.visibility = View.GONE
        tvContinueHint.visibility = View.VISIBLE
        // Download the next page's audio while this one is read.
        lecture.pages.getOrNull(pageIndex + 1)?.let { LectureAudioCache.preparePage(this, lessonId, it) }
        revealNextSentence()
    }

    private fun revealNextSentence() {
        val page = lecture.pages[currentPageIndex]

        if (currentSentenceIndex < page.sentences.size) {
            val sentence = page.sentences[currentSentenceIndex]
            addSentenceView(sentence)

            playSentenceAudio(page, currentSentenceIndex)

            currentSentenceIndex++

            if (currentSentenceIndex == page.sentences.size) {
                btnNextPage.text = getString(
                    if (currentPageIndex == lecture.pages.lastIndex) R.string.lecture_finish else R.string.next
                )
                tvContinueHint.visibility = View.GONE
                showNextButton()
            }

            updateProgressBars()

            scrollContent.post {
                scrollContent.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    /**
     * Adds the sentence as the new "being read" line: it gets the highlighter, the
     * previous line fades back to normal, and the new one slides up into place.
     */
    private fun addSentenceView(sentence: String) {
        val density = resources.displayMetrics.density

        activeSentenceView?.let { previous ->
            previous.background = null
            previous.setTextColor(ContextCompat.getColor(this, R.color.lecture_ink_soft))
        }

        val tv = TextView(this).apply {
            text = HtmlCompat.fromHtml(sentence, HtmlCompat.FROM_HTML_MODE_LEGACY)
            typeface = ResourcesCompat.getFont(this@LectureActivity, R.font.lexend)
            setTextColor(ContextCompat.getColor(this@LectureActivity, R.color.lecture_ink))
            textSize = 20f
            setLineSpacing(6 * density, 1f)
            gravity = Gravity.START
            background = ContextCompat.getDrawable(this@LectureActivity, R.drawable.bg_lecture_sentence_active)
            setPadding((22 * density).toInt(), (12 * density).toInt(), (14 * density).toInt(), (12 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10 * density).toInt() }
            alpha = 0f
            translationY = 18 * density
        }
        sentenceContainer.addView(tv)
        activeSentenceView = tv

        tv.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(320)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun showNextButton() {
        btnNextPage.visibility = View.VISIBLE
        btnNextPage.alpha = 0f
        btnNextPage.scaleX = 0.85f
        btnNextPage.scaleY = 0.85f
        btnNextPage.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(360)
            .setInterpolator(OvershootInterpolator(2.2f))
            .start()
    }

    private fun toggleSpeaker() {
        speakerOn = !speakerOn
        if (!speakerOn) stopAudio()
        ibSpeaker.setImageResource(if (speakerOn) R.drawable.ic_speaker_on else R.drawable.ic_speaker_mute)
    }

    private fun goToNextPage() {
        if (currentPageIndex < lecture.pages.lastIndex) {
            currentPageIndex++
            FirebaseAuth.getInstance().currentUser?.uid?.takeIf { tracking }?.let { uid ->
                LessonResume.save(
                    uid, lessonId, LessonResume.LECTURE,
                    mapOf("done" to currentPageIndex, "total" to lecture.pages.size)
                )
            }
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
                    if (tracking) LessonResume.clear(uid, lessonId, LessonResume.LECTURE)
                    finish()
                }
            } else {
                finish()
            }
        }
    }

    /**
     * Audio source priority: downloaded lesson file (offline) -> cached copy fetched before the
     * lecture opened (LectureAudioCache) -> Cloudflare R2 stream -> bundled res/raw.
     */
    private fun playSentenceAudio(page: LecturePage, sentenceIndex: Int) {
        stopAudio()
        if (!speakerOn) return

        val localFile = LectureAudioStore.localFile(this, lessonId, page.pageNumber, sentenceIndex)
        val cloudUrl = page.audioUrls?.getOrNull(sentenceIndex).orEmpty()
        val cachedFile = cloudUrl.takeIf { it.isNotBlank() }?.let { LectureAudioCache.file(this, it) }

        when {
            localFile.exists() -> playFromSource(localFile.absolutePath)
            cachedFile != null -> playFromSource(cachedFile.absolutePath)
            cloudUrl.isNotBlank() -> playFromSource(cloudUrl)
            else -> playRawResource(page.audioResNames.getOrNull(sentenceIndex).orEmpty())
        }
    }

    private fun playFromSource(source: String) {
        val player = MediaPlayer()
        mediaPlayer = player
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            player.setDataSource(source)
            // Ignore late callbacks from a player that was replaced while still buffering.
            player.setOnPreparedListener {
                if (mediaPlayer === it && speakerOn) {
                    it.start()
                    talkingBook.setTalking(true)
                }
            }
            player.setOnCompletionListener { if (mediaPlayer === it) talkingBook.setTalking(false) }
            player.setOnErrorListener { mp, what, extra ->
                Log.w("LectureActivity", "Audio failed ($what/$extra): $source")
                if (mediaPlayer === mp) talkingBook.setTalking(false)
                true
            }
            player.prepareAsync()
        } catch (e: Exception) {
            Log.w("LectureActivity", "Could not play $source", e)
            stopAudio()
        }
    }

    private fun playRawResource(resName: String) {
        if (resName.isBlank()) return

        val resId = resources.getIdentifier(resName, "raw", packageName)
        if (resId == 0) {
            return
        }

        val player = MediaPlayer.create(this, resId) ?: return
        mediaPlayer = player
        player.setOnCompletionListener { if (mediaPlayer === it) talkingBook.setTalking(false) }
        player.start()
        talkingBook.setTalking(true)
    }

    private fun stopAudio() {
        // release() is valid in every state, including while a stream is still preparing.
        mediaPlayer?.release()
        mediaPlayer = null
        if (::talkingBook.isInitialized) talkingBook.setTalking(false)
    }

    private fun showConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("Lumabas sa Aralin")
            .setMessage(
                if (tracking) "Sigurado ka bang gusto mong lumabas? Itutuloy mo ang aralin sa pahinang ito sa susunod."
                else "Sigurado ka bang gusto mong lumabas?"
            )
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