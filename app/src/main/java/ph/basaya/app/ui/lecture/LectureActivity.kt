package ph.basaya.app.ui

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import ph.basaya.app.R
import ph.basaya.app.data.audio.LectureAudioCache
import ph.basaya.app.data.audio.LectureAudioStore
import ph.basaya.app.data.repository.LectureRepository
import ph.basaya.app.data.model.Lecture
import ph.basaya.app.data.model.LecturePage
import ph.basaya.app.data.progress.LessonResume
import ph.basaya.app.data.progress.PracticeLessons
import ph.basaya.app.ui.lecture.ReadAlongTextView
import ph.basaya.app.ui.lecture.TalkingBookView
import ph.basaya.app.ui.pronunciation.PronunciationBookView
import ph.basaya.app.utils.Network
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import androidx.activity.OnBackPressedCallback

class LectureActivity : AppCompatActivity() {

    companion object {
        /** Open with the speaker off. */
        const val EXTRA_MUTED = "MUTED"

        /** Auto-play's breath after a sentence, and before turning the page. */
        private const val AUTO_SENTENCE_GAP_MS = 700L
        private const val AUTO_PAGE_GAP_MS = 1000L

        /** How long auto-play shows a sentence that has no audio (or whose audio failed). */
        private fun readingTime(sentence: String): Long {
            val text = HtmlCompat.fromHtml(sentence, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
            return 1200L + text.trim().split(Regex("\\s+")).size * 350L
        }
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
    private lateinit var ibAutoPlay: ImageButton
    /**
     * The play button reads on by itself: each sentence after the last one ends, turning pages,
     * until the last page, where the child still taps TAPUSIN. Off each time a lecture opens.
     */
    private var autoPlay = false
    private val autoHandler = Handler(Looper.getMainLooper())
    private val autoStep = Runnable { advance() }
    private val autoTurnPage = Runnable { goToNextPage() }
    private var mediaPlayer: MediaPlayer? = null
    /** The sentence the bubble is following while its audio plays, and when each word starts. */
    private var readAlongView: ReadAlongTextView? = null
    private var readAlongStarts: List<Double> = emptyList()
    private val readAlongTick = object : Runnable {
        override fun run() {
            val view = readAlongView ?: return
            val player = mediaPlayer ?: return
            val seconds = try {
                if (player.isPlaying) player.currentPosition / 1000.0 else -1.0
            } catch (e: IllegalStateException) {
                -1.0
            }
            if (seconds > 0 && readAlongStarts.isNotEmpty()) {
                var index = 0
                while (index + 1 < readAlongStarts.size && readAlongStarts[index + 1] <= seconds) index++
                view.showWord(index)
            }
            view.postOnAnimation(this)
        }
    }
    /** The page whose "no internet, no sound" note was already shown. */
    private var audioOfflineNotedPage = -1

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

        // Turning auto-play on turns the sound on; a sentence still being read finishes first.
        ibAutoPlay = findViewById(R.id.ibAutoPlay)
        ibAutoPlay.setOnClickListener {
            if (!::lecture.isInitialized) return@setOnClickListener
            if (autoPlay) return@setOnClickListener setAutoPlay(false)
            if (!speakerOn) toggleSpeaker()
            setAutoPlay(true)
            val reading = try {
                mediaPlayer?.isPlaying == true
            } catch (e: IllegalStateException) {
                false
            }
            if (!reading) advance()
        }

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
                Toast.makeText(this@LectureActivity, "Walang lektura para sa araling ito.", Toast.LENGTH_SHORT).show()
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
            // Fetch every page's audio now, so the pages keep their sound if the connection drops.
            LectureAudioCache.prepareAll(this@LectureActivity, lessonId, lecture.pages, currentPageIndex)

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

        val tv = ReadAlongTextView(this).apply {
            setSentence(HtmlCompat.fromHtml(sentence, HtmlCompat.FROM_HTML_MODE_LEGACY))
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
        if (!speakerOn) {
            setAutoPlay(false) // muting stops auto-play
            stopAudio()
        }
        ibSpeaker.setImageResource(if (speakerOn) R.drawable.ic_speaker_on else R.drawable.ic_speaker_mute)
    }

    private fun setAutoPlay(on: Boolean) {
        autoPlay = on
        if (!on) cancelAutoNext()
        ibAutoPlay.setImageResource(if (on) R.drawable.ic_lecture_pause else R.drawable.ic_lecture_play)
        ibAutoPlay.contentDescription =
            getString(if (on) R.string.lecture_auto_stop_cd else R.string.lecture_auto_play_cd)
    }

    /** While auto-play is on, reads the next sentence (or turns the page) after [delayMs]. */
    private fun autoNext(delayMs: Long) {
        cancelAutoNext()
        if (autoPlay) autoHandler.postDelayed(autoStep, delayMs)
    }

    private fun cancelAutoNext() {
        autoHandler.removeCallbacks(autoStep)
        autoHandler.removeCallbacks(autoTurnPage)
    }

    /** Auto-play's next step: the next sentence, else the next page. The last page waits for TAPUSIN. */
    private fun advance() {
        if (!autoPlay) return
        when {
            currentSentenceIndex < lecture.pages[currentPageIndex].sentences.size -> revealNextSentence()
            currentPageIndex < lecture.pages.lastIndex -> autoHandler.postDelayed(autoTurnPage, AUTO_PAGE_GAP_MS)
            else -> setAutoPlay(false)
        }
    }

    /** A sentence's audio didn't play: auto-play still gives the child time to read it. */
    private fun autoNextAfterReading(page: LecturePage, sentenceIndex: Int) {
        autoNext(readingTime(page.sentences.getOrNull(sentenceIndex).orEmpty()))
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
                lectureRepository.markLectureFinished(uid, lessonId)
                if (tracking) LessonResume.clear(uid, lessonId, LessonResume.LECTURE)
                // Offline: the lecture was saved on the phone and is sent once online.
                if (tracking && !PracticeLessons.isPractice(lessonId) && !Network.isOnline(this)) {
                    Toast.makeText(this, R.string.saved_offline_note, Toast.LENGTH_LONG).show()
                }
            }
            finish()
        }
    }

    /**
     * Audio source priority: downloaded lesson file (offline) -> cached copy fetched before the
     * lecture opened (LectureAudioCache) -> Cloudflare R2 stream -> bundled res/raw.
     */
    private fun playSentenceAudio(page: LecturePage, sentenceIndex: Int) {
        cancelAutoNext()
        stopAudio()
        if (!speakerOn) return autoNextAfterReading(page, sentenceIndex)

        val localFile = LectureAudioStore.localFile(this, lessonId, page.pageNumber, sentenceIndex)
        val cloudUrl = page.audioUrls?.getOrNull(sentenceIndex).orEmpty()
        val cachedFile = cloudUrl.takeIf { it.isNotBlank() }?.let { LectureAudioCache.file(this, it) }

        val onStarted = { player: MediaPlayer -> startReadAlong(player, page, sentenceIndex) }
        val onFailed = { autoNextAfterReading(page, sentenceIndex) }
        when {
            localFile.exists() -> playFromSource(localFile.absolutePath, onStarted, onFailed)
            cachedFile != null -> playFromSource(cachedFile.absolutePath, onStarted, onFailed)
            cloudUrl.isNotBlank() && !Network.isOnline(this) -> noteAudioOffline()
            cloudUrl.isNotBlank() -> playFromSource(cloudUrl, onStarted, onFailed)
            else -> playRawResource(page.audioResNames.getOrNull(sentenceIndex).orEmpty(), onStarted)
        }
        // Nothing to play (offline, no audio, or it failed to start).
        if (mediaPlayer == null) onFailed()
    }

    /**
     * Starts the bubble on the sentence being played: saved word timings when the audio has
     * them, otherwise timings estimated from its length.
     */
    private fun startReadAlong(player: MediaPlayer, page: LecturePage, sentenceIndex: Int) {
        val view = sentenceContainer.getChildAt(sentenceIndex) as? ReadAlongTextView ?: return
        val words = view.words
        val saved = page.wordStarts?.getOrNull(sentenceIndex).orEmpty()
        val duration = player.duration / 1000.0
        readAlongStarts = when {
            saved.size == words.size -> saved
            duration > 0 -> ReadAlongTextView.estimateStarts(words, duration)
            else -> return
        }
        readAlongView = view
        view.beginReading()
        view.postOnAnimation(readAlongTick)
    }

    private fun stopReadAlong() {
        readAlongView?.let {
            it.removeCallbacks(readAlongTick)
            it.endReading()
        }
        readAlongView = null
        readAlongStarts = emptyList()
    }

    /** Says once per page that its sound needs the internet, instead of staying silent. */
    private fun noteAudioOffline() {
        if (audioOfflineNotedPage == currentPageIndex) return
        audioOfflineNotedPage = currentPageIndex
        Toast.makeText(this, R.string.lecture_audio_offline, Toast.LENGTH_LONG).show()
    }

    private fun playFromSource(source: String, onStarted: (MediaPlayer) -> Unit, onFailed: () -> Unit) {
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
                    onStarted(it)
                }
            }
            player.setOnCompletionListener {
                if (mediaPlayer === it) {
                    talkingBook.setTalking(false)
                    stopReadAlong()
                    autoNext(AUTO_SENTENCE_GAP_MS)
                }
            }
            player.setOnErrorListener { mp, what, extra ->
                Log.w("LectureActivity", "Audio failed ($what/$extra): $source")
                if (mediaPlayer === mp) {
                    talkingBook.setTalking(false)
                    stopReadAlong()
                    // The connection dropped while the sentence was streaming.
                    if (source.startsWith("http") && !Network.isOnline(this)) noteAudioOffline()
                    onFailed()
                }
                true
            }
            player.prepareAsync()
        } catch (e: Exception) {
            Log.w("LectureActivity", "Could not play $source", e)
            stopAudio()
        }
    }

    private fun playRawResource(resName: String, onStarted: (MediaPlayer) -> Unit) {
        if (resName.isBlank()) return

        val resId = resources.getIdentifier(resName, "raw", packageName)
        if (resId == 0) {
            return
        }

        val player = MediaPlayer.create(this, resId) ?: return
        mediaPlayer = player
        player.setOnCompletionListener {
            if (mediaPlayer === it) {
                talkingBook.setTalking(false)
                stopReadAlong()
                autoNext(AUTO_SENTENCE_GAP_MS)
            }
        }
        player.start()
        talkingBook.setTalking(true)
        onStarted(player)
    }

    private fun stopAudio() {
        stopReadAlong()
        // release() is valid in every state, including while a stream is still preparing.
        mediaPlayer?.release()
        mediaPlayer = null
        if (::talkingBook.isInitialized) talkingBook.setTalking(false)
    }

    /** "Aalis ka na?": the book buddy peeks over a sticker card and cheers the child to stay. */
    private fun showConfirmDialog() {
        if (::ibAutoPlay.isInitialized) setAutoPlay(false) // no page turns behind the dialog
        val dialog = Dialog(this)
        val content = layoutInflater.inflate(R.layout.dialog_lecture_exit, null)
        val card = content.findViewById<View>(R.id.lexCard)
        val buddy = content.findViewById<PronunciationBookView>(R.id.lexBuddy)
        val tvPage = content.findViewById<TextView>(R.id.lexPage)
        val dots = content.findViewById<LinearLayout>(R.id.lexDots)
        val page = currentPageIndex + 1

        if (::lecture.isInitialized && lecture.pages.isNotEmpty()) {
            tvPage.text = getString(R.string.lex_page, page, lecture.pages.size)
            addPageDots(dots)
        } else {
            tvPage.visibility = View.GONE
            dots.visibility = View.GONE
        }
        content.findViewById<TextView>(R.id.lexMessage).text =
            if (tracking) getString(R.string.lex_message_saved, page) else getString(R.string.lex_message)

        content.findViewById<View>(R.id.lexStay).setOnClickListener {
            // A happy little hop before going back to the story.
            buddy.setMood(PronunciationBookView.Mood.HAPPY)
            buddy.animate().translationY(-14 * resources.displayMetrics.density).setDuration(140)
                .withEndAction { dialog.dismiss() }.start()
        }
        content.findViewById<View>(R.id.lexLeave).setOnClickListener {
            dialog.dismiss()
            stopAudio()
            finish()
        }

        dialog.setContentView(content)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        // Pop in: the card springs up, then the buddy drops onto it and starts thinking.
        card.scaleX = 0.8f
        card.scaleY = 0.8f
        card.alpha = 0f
        card.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(320)
            .setInterpolator(OvershootInterpolator(1.6f)).start()
        buddy.translationY = -40 * resources.displayMetrics.density
        buddy.alpha = 0f
        buddy.animate().translationY(0f).alpha(1f).setStartDelay(120).setDuration(420)
            .setInterpolator(OvershootInterpolator(2.2f))
            .withEndAction { buddy.setMood(PronunciationBookView.Mood.THINKING) }.start()
        for (id in intArrayOf(R.id.lexSparkleLeft, R.id.lexSparkleRight)) {
            val sparkle = content.findViewById<View>(id)
            sparkle.scaleX = 0f
            sparkle.scaleY = 0f
            sparkle.animate().scaleX(1f).scaleY(1f).rotation(90f).setStartDelay(380).setDuration(360)
                .setInterpolator(OvershootInterpolator(3f)).start()
        }
    }

    /** One dot per page on the exit dialog: read, here now (bigger, gold), still to read. */
    private fun addPageDots(row: LinearLayout) {
        val density = resources.displayMetrics.density
        val read = ContextCompat.getColor(this, R.color.lecture_primary)
        val now = ContextCompat.getColor(this, R.color.lex_dot_now)
        val todo = ContextCompat.getColor(this, R.color.lecture_progress_track)
        for (i in lecture.pages.indices) {
            val size = ((if (i == currentPageIndex) 12 else 8) * density).toInt()
            row.addView(View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(
                        when {
                            i < currentPageIndex -> read
                            i == currentPageIndex -> now
                            else -> todo
                        }
                    )
                }
            }, LinearLayout.LayoutParams(size, size).apply {
                if (i != 0) marginStart = (6 * density).toInt()
            })
        }
    }

    /** Leaving the app stops auto-play, so pages don't turn while nobody is reading. */
    override fun onStop() {
        super.onStop()
        if (::ibAutoPlay.isInitialized) setAutoPlay(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelAutoNext()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}