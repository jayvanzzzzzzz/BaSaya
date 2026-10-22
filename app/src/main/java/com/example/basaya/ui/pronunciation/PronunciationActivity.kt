package com.example.basaya.ui.pronunciation

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.basaya.R
import com.example.basaya.data.audio.PronunciationAudioStore
import com.example.basaya.data.dictionary.Dictionary
import com.example.basaya.data.dictionary.Syllables
import com.example.basaya.data.model.PronunciationActivityData
import com.example.basaya.data.model.PronunciationWord
import com.example.basaya.data.progress.LessonResume
import com.example.basaya.data.progress.PracticeLessons
import com.example.basaya.data.progress.Skill
import com.example.basaya.data.progress.SkillProgressRepository
import com.example.basaya.data.repository.PronunciationRepository
import com.example.basaya.speech.PronunciationChecker
import com.example.basaya.speech.PronunciationScorer
import com.example.basaya.speech.VoiceRecorder
import com.example.basaya.speech.WhisperSttClient
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.example.basaya.ui.game.ConfettiView
import com.example.basaya.ui.theme.useDarkStatusBarIconsInLightTheme
import com.example.basaya.utils.Network
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

class PronunciationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LESSON_ID = "LESSON_ID"
        const val EXTRA_SCORE = "PRONUNCIATION_SCORE"
        const val EXTRA_TOTAL = "PRONUNCIATION_TOTAL"
        private const val MAX_LIVES = 3
        private const val ADVANCE_DELAY_MS = 2400L
        private const val UTTERANCE_ID = "pronunciation_word"
    }

    private enum class ResultStyle(
        @param:DrawableRes val background: Int,
        @param:ColorRes val titleColor: Int
    ) {
        SUCCESS(R.drawable.pr_result_success, R.color.pr_success),
        OKAY(R.drawable.pr_result_okay, R.color.pr_okay),
        CHEER(R.drawable.pr_result_cheer, R.color.pr_cheer),
        INFO(R.drawable.pr_result_info, R.color.pr_info)
    }

    private lateinit var btnClose: ImageButton
    private lateinit var progressBarWords: ProgressBar
    private lateinit var tvLivesCount: TextView
    private lateinit var ivHeartIcon: ImageView
    private lateinit var livesIndicator: LinearLayout
    private lateinit var tvInstruction: TextView
    private lateinit var bookView: PronunciationBookView
    private lateinit var wordCard: View
    private lateinit var tvTargetWord: TextView
    private lateinit var tvTargetSyllables: TextView
    private lateinit var btnWordInfo: TextView
    private lateinit var btnSpeaker: ImageButton
    private lateinit var resultStickyNote: LinearLayout
    private lateinit var tvResultBand: TextView
    private lateinit var tvRecognizedWord: TextView
    private lateinit var btnMic: ImageButton
    private lateinit var pulseRing1: View
    private lateinit var pulseRing2: View
    private lateinit var micProcessingSpinner: ProgressBar
    private lateinit var tvMicHint: TextView
    private lateinit var confettiView: ConfettiView

    private lateinit var pronunciationChecker: PronunciationChecker
    private val whisperClient = WhisperSttClient()
    private val voiceRecorder = VoiceRecorder()
    private var textToSpeech: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null

    private lateinit var pronunciationRepository: PronunciationRepository
    private lateinit var pronunciationData: PronunciationActivityData
    private var lessonId: String = ""
    private var wordList: List<PronunciationWord> = emptyList()
    // Pantig under each word: split right away, then a teacher's fix from the Diksiyonaryo.
    private var syllables: MutableList<String> = mutableListOf()
    // Each word's Diksiyonaryo entry (null = not in it, so no "i" button).
    private var entries: MutableList<Dictionary.Entry?> = mutableListOf()
    private var currentWordIndex = 0
    private var livesRemaining = MAX_LIVES
    private var correctCount = 0

    // Pagbigkas skill tracking. "No speech heard" is not a miss; only scored attempts count.
    private var firstTryCorrect = 0
    private var missesOnWord = 0
    private val hardWords = linkedSetOf<String>()
    private var isProcessing = false

    /** Lives and misses of the word the child closed on, used once by showCurrentWord(). */
    private var restoredLives: Int? = null
    private var restoredMisses: Int? = null

    /** Set once Whisper is unavailable (e.g. free daily limit reached) so we stop retrying it. */
    private var useAndroidRecognizer = false
    private var listenJob: Job? = null

    private var ringAnimator: ValueAnimator? = null
    @Volatile private var voiceLevel = 0f
    private var shownLevel = 0f

    private val handler = Handler(Looper.getMainLooper())

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            beginListening()
        } else {
            showInfo(getString(R.string.pr_mic_permission))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_pronunciation)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        useDarkStatusBarIconsInLightTheme()

        lessonId = intent.getStringExtra(EXTRA_LESSON_ID) ?: ""

        bindViews()
        setupClickListeners()

        pronunciationChecker = PronunciationChecker(this)
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                setPreferredTtsLocale()
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        runOnUiThread { bookView.setMood(PronunciationBookView.Mood.TALKING) }
                    }

                    override fun onDone(utteranceId: String?) {
                        runOnUiThread { onPlaybackFinished() }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        runOnUiThread { onPlaybackFinished() }
                    }
                })
            }
        }

        pronunciationRepository = PronunciationRepository(this)
        loadWords()
    }

    private fun bindViews() {
        btnClose = findViewById(R.id.btnClose)
        progressBarWords = findViewById(R.id.progressBarWords)
        tvLivesCount = findViewById(R.id.tvLivesCount)
        ivHeartIcon = findViewById(R.id.ivHeartIcon)
        livesIndicator = findViewById(R.id.livesIndicator)
        tvInstruction = findViewById(R.id.tvInstruction)
        bookView = findViewById(R.id.bookView)
        wordCard = findViewById(R.id.wordCard)
        tvTargetWord = findViewById(R.id.tvTargetWord)
        tvTargetSyllables = findViewById(R.id.tvTargetSyllables)
        btnWordInfo = findViewById(R.id.btnWordInfo)
        btnWordInfo.setOnClickListener { showWordMeaning() }
        btnSpeaker = findViewById(R.id.btnSpeaker)
        resultStickyNote = findViewById(R.id.resultStickyNote)
        tvResultBand = findViewById(R.id.tvResultBand)
        tvRecognizedWord = findViewById(R.id.tvRecognizedWord)
        btnMic = findViewById(R.id.btnMic)
        pulseRing1 = findViewById(R.id.pulseRing1)
        pulseRing2 = findViewById(R.id.pulseRing2)
        micProcessingSpinner = findViewById(R.id.micProcessingSpinner)
        tvMicHint = findViewById(R.id.tvMicHint)
        confettiView = findViewById(R.id.confettiView)

        setButtonsEnabled(false)
    }

    private fun setupClickListeners() {
        btnClose.setOnClickListener { finish() }

        btnSpeaker.setOnClickListener {
            bounce(btnSpeaker)
            speakCurrentWord()
        }

        btnMic.setOnClickListener {
            if (isProcessing) return@setOnClickListener
            bounce(btnMic)
            stopPlayback()
            if (!isOnline()) {
                showOffline()
                return@setOnClickListener
            }
            checkPermissionAndListen()
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        btnMic.isEnabled = enabled
        btnSpeaker.isEnabled = enabled
        btnMic.alpha = if (enabled) 1f else 0.85f
    }

    // ───────────────────────────── Words ─────────────────────────────

    private fun loadWords() {
        if (lessonId.isEmpty()) {
            showInfo(getString(R.string.pr_no_lesson))
            finish()
            return
        }

        lifecycleScope.launch {
            val fetched = pronunciationRepository.getActivity(lessonId)

            if (fetched == null || fetched.words.isEmpty()) {
                showInfo(getString(R.string.pr_no_activity))
                finish()
                return@launch
            }

            pronunciationData = fetched
            if (fetched.instruction.isNotBlank()) tvInstruction.text = fetched.instruction
            wordList = fetched.words.mapIndexed { index, word ->
                PronunciationWord(
                    id = "$index",
                    word = word,
                    order = index,
                    audioRef = fetched.audioRefs.getOrNull(index) ?: ""
                )
            }

            syllables = wordList.map { Syllables.split(it.word) }.toMutableList()
            entries = MutableList(wordList.size) { null }
            wordList.forEachIndexed { index, item ->
                launch {
                    val entry = runCatching { Dictionary.lookup(this@PronunciationActivity, item.word) }.getOrNull()
                    entries[index] = entry
                    entry?.fixedSyllables?.takeIf { it.isNotBlank() }?.let { syllables[index] = it }
                    if (index == currentWordIndex && tvTargetWord.text.toString() == item.word) showSyllables()
                }
            }

            // Continue where the child closed Bigkas (here or on the website): the word, the
            // lives left on it and the score so far.
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            val progress = uid?.let { LessonResume.loadProgress(it, lessonId) }
            if (progress?.getBoolean("pronunciationFinished") == true) {
                finish()
                return@launch
            }
            LessonResume.of(progress, LessonResume.PRONUNCIATION, wordList.size)?.let { resume ->
                currentWordIndex = resume["done"] as Int
                correctCount = (resume["correct"] as? Number)?.toInt() ?: 0
                firstTryCorrect = (resume["firstTryCorrect"] as? Number)?.toInt() ?: 0
                (resume["hardWords"] as? List<*>)?.filterIsInstance<String>()?.let { hardWords.addAll(it) }
                restoredLives = ((resume["lives"] as? Number)?.toInt() ?: MAX_LIVES).coerceIn(1, MAX_LIVES)
                restoredMisses = (resume["misses"] as? Number)?.toInt() ?: 0
            }

            // Every word was done before closing: only the result is left to save.
            if (currentWordIndex >= wordList.size) {
                finishActivityWithScore()
                return@launch
            }
            showCurrentWord(animate = false)
        }
    }

    /**
     * Saves the place: `done` words finished, with the lives and misses of word `done` when
     * it is the current one (a fresh word starts with MAX_LIVES).
     */
    private fun savePlace(done: Int) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val current = done == currentWordIndex
        LessonResume.save(
            uid, lessonId, LessonResume.PRONUNCIATION,
            mapOf(
                "done" to done,
                "total" to wordList.size,
                "lives" to if (current) livesRemaining else MAX_LIVES,
                "misses" to if (current) missesOnWord else 0,
                "correct" to correctCount,
                "firstTryCorrect" to firstTryCorrect,
                "hardWords" to hardWords.toList()
            )
        )
    }

    /**
     * Pantig under the word and the "ⓘ Kahulugan" chip (display only; checking still uses the
     * word itself). The chip is gone for words the Diksiyonaryo doesn't have.
     */
    private fun showSyllables() {
        val text = syllables.getOrNull(currentWordIndex).orEmpty()
        tvTargetSyllables.text = text
        tvTargetSyllables.contentDescription = getString(R.string.dict_syllables_cd, text)
        tvTargetSyllables.visibility = if (Syllables.hasBreaks(text)) View.VISIBLE else View.GONE

        val hasMeaning = entries.getOrNull(currentWordIndex) != null
        val wasShown = btnWordInfo.visibility == View.VISIBLE
        btnWordInfo.visibility = if (hasMeaning) View.VISIBLE else View.GONE
        if (hasMeaning && !wasShown) wiggleWordInfo()
    }

    /** A little wiggle when the chip appears, so the student notices it. Kept gentle so the
     *  wide pill doesn't swing out of the card. */
    private fun wiggleWordInfo() {
        btnWordInfo.rotation = 0f
        ObjectAnimator.ofFloat(btnWordInfo, View.ROTATION, 0f, -6f, 5f, -3f, 0f).apply {
            startDelay = 400
            duration = 900
            start()
        }
    }

    /** The word's meaning from the Diksiyonaryo: first meaning group only, kept short. */
    private fun showWordMeaning() {
        val entry = entries.getOrNull(currentWordIndex) ?: return
        val sense = entry.senses.firstOrNull()
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_word_info, null)
        sheet.setContentView(view)

        view.findViewById<TextView>(R.id.tvInfoWord).text = entry.shown
        val syllableText = syllables.getOrNull(currentWordIndex).orEmpty()
        view.findViewById<TextView>(R.id.tvInfoSyllables).apply {
            text = syllableText
            contentDescription = getString(R.string.dict_syllables_cd, syllableText)
            visibility = if (Syllables.hasBreaks(syllableText)) View.VISIBLE else View.GONE
        }
        view.findViewById<TextView>(R.id.tvInfoPos).apply {
            text = sense?.pos.orEmpty()
            visibility = if (sense?.pos.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        view.findViewById<TextView>(R.id.tvInfoMeanings).text = sense?.definitions.orEmpty()
            .mapIndexed { i, definition -> "${i + 1}. $definition" }
            .joinToString("\n")
        view.findViewById<TextView>(R.id.tvInfoExamples).apply {
            val examples = sense?.examples.orEmpty()
            text = examples.joinToString("\n") { "“$it”" }
            visibility = if (examples.isEmpty()) View.GONE else View.VISIBLE
        }
        view.findViewById<View>(R.id.btnInfoClose).setOnClickListener { sheet.dismiss() }
        sheet.show()
    }

    private fun showCurrentWord(animate: Boolean) {
        val currentWord = wordList[currentWordIndex]
        livesRemaining = restoredLives ?: MAX_LIVES
        missesOnWord = restoredMisses ?: 0
        restoredLives = null
        restoredMisses = null
        updateLivesDisplay(animate = false)
        updateProgressBar()
        hideResult()
        bookView.setMood(PronunciationBookView.Mood.IDLE)
        tvMicHint.setText(R.string.pr_mic_hint_idle)

        btnWordInfo.visibility = View.GONE // so it wiggles again for the new word
        if (!animate) {
            tvTargetWord.text = currentWord.word
            showSyllables()
            setButtonsEnabled(true)
            return
        }

        // Slide the old word out and the new one in.
        val distance = wordCard.width.toFloat()
        wordCard.animate()
            .translationX(-distance * 0.6f).alpha(0f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                tvTargetWord.text = currentWord.word
                showSyllables()
                wordCard.translationX = distance * 0.6f
                wordCard.animate()
                    .translationX(0f).alpha(1f)
                    .setDuration(320)
                    .setInterpolator(OvershootInterpolator(1.2f))
                    .withEndAction { setButtonsEnabled(true) }
                    .start()
            }
            .start()
    }

    private fun updateProgressBar() {
        val value = (currentWordIndex * progressBarWords.max) / wordList.size.coerceAtLeast(1)
        progressBarWords.setProgress(value, true)
    }

    private fun updateLivesDisplay(animate: Boolean) {
        tvLivesCount.text = livesRemaining.toString()
        livesIndicator.contentDescription = getString(R.string.pr_lives_cd, livesRemaining)
        if (animate) {
            ivHeartIcon.scaleX = 1.5f
            ivHeartIcon.scaleY = 1.5f
            ivHeartIcon.animate().scaleX(1f).scaleY(1f).setDuration(400)
                .setInterpolator(OvershootInterpolator(3f)).start()
        }
    }

    // ───────────────────────────── Listening ─────────────────────────────

    private fun checkPermissionAndListen() {
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            beginListening()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun beginListening() {
        if (wordList.isEmpty()) return
        isProcessing = true
        btnSpeaker.isEnabled = false
        hideResult()

        if (whisperClient.isConfigured && !useAndroidRecognizer) {
            listenWithWhisper()
        } else {
            listenWithAndroid()
        }
    }

    private fun showListeningUi() {
        bookView.setMood(PronunciationBookView.Mood.LISTENING)
        tvMicHint.setText(R.string.pr_mic_hint_listening)
        micProcessingSpinner.visibility = View.GONE
        startPulseAnimation()
    }

    private fun showThinkingUi() {
        bookView.setMood(PronunciationBookView.Mood.THINKING)
        tvMicHint.setText(R.string.pr_mic_hint_thinking)
        stopPulseAnimation()
        micProcessingSpinner.visibility = View.VISIBLE
    }

    private fun listenWithWhisper() {
        val targetWord = wordList[currentWordIndex].word
        showListeningUi()

        listenJob = lifecycleScope.launch {
            val recording = voiceRecorder.record { level ->
                voiceLevel = level
                bookView.setVoiceLevel(level)
            }

            when (recording) {
                VoiceRecorder.Result.MicUnavailable -> {
                    stopListeningUi()
                    showInfo(getString(R.string.pr_mic_unavailable))
                }

                VoiceRecorder.Result.NoSpeech -> {
                    stopListeningUi()
                    showInfo(getString(R.string.pr_no_speech))
                }

                is VoiceRecorder.Result.Speech -> {
                    showThinkingUi()
                    when (val result = whisperClient.transcribe(recording.wav)) {
                        is WhisperSttClient.Result.Text -> {
                            if (result.text.isBlank()) {
                                stopListeningUi()
                                showInfo(getString(R.string.pr_no_speech))
                            } else {
                                val score = PronunciationScorer.score(targetWord, listOf(result.text), null)
                                handleScoreResult(score)
                            }
                        }

                        WhisperSttClient.Result.NetworkError -> {
                            stopListeningUi()
                            if (!isOnline()) showOffline() else switchToAndroidRecognizer()
                        }

                        WhisperSttClient.Result.Unavailable -> switchToAndroidRecognizer()
                    }
                }
            }
        }
    }

    /** Whisper failed while online (quota/server): ask again and let Android's recognizer listen. */
    private fun switchToAndroidRecognizer() {
        Log.w("PronunciationActivity", "Whisper unavailable, using Android speech recognition")
        useAndroidRecognizer = true
        showInfo(getString(R.string.pr_say_again))
        isProcessing = true
        listenWithAndroid()
    }

    private fun listenWithAndroid() {
        val targetWord = wordList[currentWordIndex].word
        showListeningUi()

        pronunciationChecker.startListening(targetWord, object : PronunciationChecker.PronunciationResultListener {
            override fun onResult(candidates: List<String>, confidenceScores: FloatArray?) {
                val result = PronunciationScorer.score(targetWord, candidates, confidenceScores)
                handleScoreResult(result)
            }

            override fun onError(errorMessage: String) {
                stopListeningUi()
                // Nothing usable was heard: let the child simply try again without losing a life.
                showInfo(getString(R.string.pr_no_speech))
            }

            override fun onUnavailable() {
                stopListeningUi()
                showInfo(getString(R.string.pr_stt_unavailable))
            }

            override fun onLanguageDownloadTriggered() {
                stopListeningUi()
                showInfo(getString(R.string.pr_language_download))
            }

            override fun onVoiceLevel(level: Float) {
                voiceLevel = level
                bookView.setVoiceLevel(level)
            }
        })
    }

    private fun stopListeningUi() {
        isProcessing = false
        stopPulseAnimation()
        micProcessingSpinner.visibility = View.GONE
        btnSpeaker.isEnabled = true
        tvMicHint.setText(R.string.pr_mic_hint_idle)
        if (bookView.mood == PronunciationBookView.Mood.LISTENING ||
            bookView.mood == PronunciationBookView.Mood.THINKING
        ) {
            bookView.setMood(PronunciationBookView.Mood.IDLE)
        }
    }

    // ───────────────────────────── Results ─────────────────────────────

    private fun handleScoreResult(result: PronunciationScorer.PronunciationResult) {
        stopListeningUi()

        when (result.scoreBand) {
            PronunciationScorer.ScoreBand.GREAT, PronunciationScorer.ScoreBand.OKAY -> {
                correctCount++
                if (missesOnWord == 0) firstTryCorrect++
                savePlace(currentWordIndex + 1)
                setButtonsEnabled(false)
                bookView.setMood(PronunciationBookView.Mood.HAPPY)
                celebrate()
                val great = result.scoreBand == PronunciationScorer.ScoreBand.GREAT
                showResult(
                    if (great) ResultStyle.SUCCESS else ResultStyle.OKAY,
                    getString(if (great) R.string.pr_great else R.string.pr_okay),
                    // Correct answers show the lesson word exactly as written, not the raw recognizer text
                    wordList[currentWordIndex].word
                )
                handler.postDelayed({ advanceToNextWord() }, ADVANCE_DELAY_MS)
            }

            PronunciationScorer.ScoreBand.TRY_AGAIN -> handleFailedAttempt()
        }
    }

    /**
     * Wrong answers only encourage; the misheard text is not shown. When the word's lives run
     * out it scores 0 and the next word is up.
     */
    private fun handleFailedAttempt() {
        livesRemaining--
        missesOnWord++
        hardWords.add(wordList[currentWordIndex].word)
        updateLivesDisplay(animate = true)
        bookView.setMood(PronunciationBookView.Mood.CHEER)
        savePlace(if (livesRemaining <= 0) currentWordIndex + 1 else currentWordIndex)

        if (livesRemaining <= 0) {
            setButtonsEnabled(false)
            showResult(ResultStyle.CHEER, getString(R.string.pr_out_of_lives), "")
            handler.postDelayed({ advanceToNextWord() }, ADVANCE_DELAY_MS)
        } else {
            showResult(ResultStyle.CHEER, getString(R.string.pr_try_again), "")
        }
    }

    private fun celebrate() {
        btnMic.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        val bookLoc = IntArray(2)
        val confettiLoc = IntArray(2)
        bookView.getLocationInWindow(bookLoc)
        confettiView.getLocationInWindow(confettiLoc)
        confettiView.burst(
            bookLoc[0] - confettiLoc[0] + bookView.width / 2f,
            bookLoc[1] - confettiLoc[1] + bookView.height * 0.45f,
            40
        )
        confettiView.rain(60)

        wordCard.animate().scaleX(1.06f).scaleY(1.06f).setDuration(160)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                wordCard.animate().scaleX(1f).scaleY(1f).setDuration(320)
                    .setInterpolator(OvershootInterpolator(3f)).start()
            }
            .start()
    }

    private fun showResult(style: ResultStyle, title: String, recognizedText: String) {
        resultStickyNote.setBackgroundResource(style.background)
        tvResultBand.setTextColor(ContextCompat.getColor(this, style.titleColor))
        tvResultBand.text = title
        if (recognizedText.isNotBlank()) {
            tvRecognizedWord.text = getString(R.string.pr_heard, recognizedText)
            tvRecognizedWord.visibility = View.VISIBLE
        } else {
            tvRecognizedWord.visibility = View.GONE
        }

        resultStickyNote.animate().cancel()
        resultStickyNote.visibility = View.VISIBLE
        resultStickyNote.alpha = 0f
        resultStickyNote.scaleX = 0.85f
        resultStickyNote.scaleY = 0.85f
        resultStickyNote.translationY = 12f * resources.displayMetrics.density
        resultStickyNote.animate()
            .alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
            .setDuration(300)
            .setInterpolator(OvershootInterpolator(2f))
            .start()
    }

    private fun showInfo(message: String, detail: String = "") {
        showResult(ResultStyle.INFO, message, "")
        if (detail.isNotBlank()) {
            tvRecognizedWord.text = detail
            tvRecognizedWord.visibility = View.VISIBLE
        }
    }

    private fun hideResult() {
        if (resultStickyNote.visibility != View.VISIBLE) return
        resultStickyNote.animate().cancel()
        resultStickyNote.animate()
            .alpha(0f).scaleX(0.92f).scaleY(0.92f)
            .setDuration(150)
            .withEndAction { resultStickyNote.visibility = View.INVISIBLE }
            .start()
    }

    private fun showOffline() {
        showInfo(getString(R.string.pr_need_internet), getString(R.string.pr_need_internet_detail))
        val d = resources.displayMetrics.density
        ObjectAnimator.ofFloat(btnMic, View.TRANSLATION_X, 0f, -10f * d, 10f * d, -6f * d, 6f * d, 0f).apply {
            duration = 380
            start()
        }
    }

    private fun advanceToNextWord() {
        currentWordIndex++

        if (currentWordIndex >= wordList.size) {
            finishActivityWithScore()
        } else {
            showCurrentWord(animate = true)
        }
    }

    private fun finishActivityWithScore() {
        progressBarWords.setProgress(progressBarWords.max, true)
        val uid = FirebaseAuth.getInstance().currentUser?.uid

        lifecycleScope.launch {
            if (uid != null) {
                try {
                    pronunciationRepository.markActivityFinished(uid, lessonId, correctCount, wordList.size)
                } catch (e: Exception) {
                    Log.e("PronunciationActivity", "Failed to save pronunciation result", e)
                }
                LessonResume.clear(uid, lessonId, LessonResume.PRONUNCIATION)

                // Pagbigkas skill: first-try correct words, plus the words that needed help
                try {
                    SkillProgressRepository().recordResult(
                        uid, lessonId, Skill.PAGBIGKAS,
                        correct = firstTryCorrect, attempts = wordList.size, items = wordList.size,
                        extras = mapOf(
                            "eventuallyCorrect" to correctCount,
                            "hardWords" to hardWords.toList()
                        )
                    )
                    SkillProgressRepository.refreshInBackground(uid)
                } catch (e: Exception) {
                    Log.e("PronunciationActivity", "Failed to save pronunciation progress", e)
                }

                // Offline: the result was saved on the phone and is sent once online.
                if (!PracticeLessons.isPractice(lessonId) && !Network.isOnline(this@PronunciationActivity)) {
                    Toast.makeText(this@PronunciationActivity, R.string.saved_offline_note, Toast.LENGTH_LONG).show()
                }
            }

            val resultIntent = Intent().apply {
                putExtra(EXTRA_SCORE, correctCount)
                putExtra(EXTRA_TOTAL, wordList.size)
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    // ───────────────────────────── Playback ─────────────────────────────

    /** Audio source priority: downloaded file (offline) -> Cloudflare R2 stream -> bundled res/raw -> TTS. */
    private fun speakCurrentWord() {
        if (isProcessing) return
        val currentWord = wordList.getOrNull(currentWordIndex) ?: return
        stopPlayback()

        val localFile = PronunciationAudioStore.localFile(this, lessonId, currentWord.order)
        val ref = currentWord.audioRef
        when {
            localFile.exists() -> playSource(localFile.absolutePath, currentWord.word)
            ref.startsWith("https://") && isOnline() -> playSource(ref, currentWord.word)
            ref.isNotBlank() && !ref.startsWith("http") -> playRawResource(ref, currentWord.word)
            else -> speakWithTts(currentWord.word)
        }
    }

    private fun playSource(source: String, wordText: String) {
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
            player.setOnPreparedListener {
                bookView.setMood(PronunciationBookView.Mood.TALKING)
                it.start()
            }
            player.setOnCompletionListener { onPlaybackFinished() }
            player.setOnErrorListener { _, what, extra ->
                Log.w("PronunciationActivity", "Audio failed ($what/$extra): $source")
                releasePlayer()
                speakWithTts(wordText)
                true
            }
            player.prepareAsync()
        } catch (e: Exception) {
            Log.w("PronunciationActivity", "Audio failed: $source", e)
            releasePlayer()
            speakWithTts(wordText)
        }
    }

    private fun playRawResource(name: String, wordText: String) {
        val resId = resources.getIdentifier(name, "raw", packageName)
        val player = if (resId != 0) MediaPlayer.create(this, resId) else null
        if (player == null) {
            speakWithTts(wordText)
            return
        }
        mediaPlayer = player
        player.setOnCompletionListener { onPlaybackFinished() }
        bookView.setMood(PronunciationBookView.Mood.TALKING)
        player.start()
    }

    private fun speakWithTts(word: String) {
        textToSpeech?.speak(word, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    private fun setPreferredTtsLocale() {
        val filipino = Locale("fil", "PH")
        val result = textToSpeech?.setLanguage(filipino)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Fall back to Tagalog locale tag if fil-PH voice isn't installed
            textToSpeech?.setLanguage(Locale("tl", "PH"))
        }
    }

    private fun onPlaybackFinished() {
        releasePlayer()
        if (bookView.mood == PronunciationBookView.Mood.TALKING) {
            bookView.setMood(PronunciationBookView.Mood.IDLE)
        }
    }

    private fun stopPlayback() {
        textToSpeech?.stop()
        onPlaybackFinished()
    }

    private fun releasePlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    // ───────────────────────────── Animations ─────────────────────────────

    /** Two ripples that grow bigger and brighter the louder the child speaks. */
    private fun startPulseAnimation() {
        stopPulseAnimation()
        pulseRing1.visibility = View.VISIBLE
        pulseRing2.visibility = View.VISIBLE
        val startMs = System.currentTimeMillis()

        ringAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                shownLevel += (voiceLevel - shownLevel) * 0.25f
                val elapsed = (System.currentTimeMillis() - startMs) / 1400f
                listOf(pulseRing1 to 0f, pulseRing2 to 0.5f).forEach { (ring, offset) ->
                    val phase = (elapsed + offset) % 1f
                    val scale = 1f + phase * (0.6f + shownLevel * 0.9f)
                    ring.scaleX = scale
                    ring.scaleY = scale
                    ring.alpha = (1f - phase) * (0.45f + shownLevel * 0.55f)
                }
                val micScale = 1f + shownLevel * 0.1f
                btnMic.scaleX = micScale
                btnMic.scaleY = micScale
            }
            start()
        }
    }

    private fun stopPulseAnimation() {
        ringAnimator?.cancel()
        ringAnimator = null
        voiceLevel = 0f
        shownLevel = 0f
        bookView.setVoiceLevel(0f)

        listOf(pulseRing1, pulseRing2).forEach { ring ->
            ring.visibility = View.INVISIBLE
            ring.alpha = 1f
            ring.scaleX = 1f
            ring.scaleY = 1f
        }
        btnMic.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
    }

    private fun bounce(view: View) {
        view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80)
            .withEndAction {
                view.animate().scaleX(1f).scaleY(1f).setDuration(260)
                    .setInterpolator(OvershootInterpolator(3f)).start()
            }
            .start()
    }

    private fun isOnline(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // ───────────────────────────── Lifecycle ─────────────────────────────

    override fun onStop() {
        super.onStop()
        // Never keep the mic or audio running in the background.
        if (isProcessing) {
            listenJob?.cancel()
            pronunciationChecker.destroy()
            stopListeningUi()
        }
        stopPlayback()
    }

    override fun onDestroy() {
        super.onDestroy()
        listenJob?.cancel()
        pronunciationChecker.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        releasePlayer()
        ringAnimator?.cancel()
        handler.removeCallbacksAndMessages(null)
    }
}
