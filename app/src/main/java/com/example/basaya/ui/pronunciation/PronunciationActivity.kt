package com.example.basaya.ui.pronunciation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.basaya.R
import com.example.basaya.data.model.PronunciationActivityData
import com.example.basaya.data.model.PronunciationWord
import com.example.basaya.data.repository.PronunciationRepository
import com.example.basaya.speech.PronunciationChecker
import com.example.basaya.speech.PronunciationScorer
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.Locale
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import android.animation.AnimatorSet

class PronunciationActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private lateinit var pulseRing1: View
    private lateinit var pulseRing2: View
    private var pulseAnimatorSet: AnimatorSet? = null

    companion object {
        const val EXTRA_LESSON_ID = "LESSON_ID"
        const val EXTRA_SCORE = "PRONUNCIATION_SCORE"
        const val EXTRA_TOTAL = "PRONUNCIATION_TOTAL"
        private const val MAX_LIVES = 5
        private const val ADVANCE_DELAY_MS = 2000L
    }

    private lateinit var btnClose: ImageButton
    private lateinit var progressBarWords: ProgressBar
    private lateinit var tvLivesCount: TextView
    private lateinit var ivHeartIcon: ImageView
    private lateinit var tvTargetWord: TextView
    private lateinit var btnSpeaker: ImageButton
    private lateinit var resultStickyNote: View
    private lateinit var tvResultBand: TextView
    private lateinit var tvRecognizedWord: TextView
    private lateinit var btnMic: ImageButton
    private lateinit var micProcessingSpinner: ProgressBar

    private lateinit var pronunciationChecker: PronunciationChecker
    private var textToSpeech: TextToSpeech? = null

    private lateinit var pronunciationRepository: PronunciationRepository
    private lateinit var pronunciationData: PronunciationActivityData
    private var lessonId: String = ""
    private var wordList: List<PronunciationWord> = emptyList()
    private var currentWordIndex = 0
    private var livesRemaining = MAX_LIVES
    private var correctCount = 0
    private var isProcessing = false

    private val handler = Handler(Looper.getMainLooper())

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            beginListening()
        } else {
            showTemporaryMessage("Kailangan ng microphone permission para magamit ito.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pronunciation)
        enableEdgeToEdge()

        val statusBarBg = findViewById<View>(R.id.statusBarBg)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            statusBarBg.layoutParams.height = statusBarHeight
            statusBarBg.requestLayout()
            insets
        }

        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        lessonId = intent.getStringExtra(EXTRA_LESSON_ID) ?: ""

        bindViews()
        setupClickListeners()

        pronunciationChecker = PronunciationChecker(this)
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                setPreferredTtsLocale()
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
        tvTargetWord = findViewById(R.id.tvTargetWord)
        btnSpeaker = findViewById(R.id.btnSpeaker)
        resultStickyNote = findViewById(R.id.resultStickyNote)
        tvResultBand = findViewById(R.id.tvResultBand)
        tvRecognizedWord = findViewById(R.id.tvRecognizedWord)
        btnMic = findViewById(R.id.btnMic)
        pulseRing1 = findViewById(R.id.pulseRing1)
        pulseRing2 = findViewById(R.id.pulseRing2)
        micProcessingSpinner = findViewById(R.id.micProcessingSpinner)
    }

    private fun setupClickListeners() {
        btnClose.setOnClickListener { finish() }

        btnSpeaker.setOnClickListener {
            speakCurrentWord()
        }

        btnMic.setOnClickListener {
            if (isProcessing) return@setOnClickListener
            checkPermissionAndListen()
        }
    }

    private fun loadWords() {
        if (lessonId.isEmpty()) {
            showTemporaryMessage("Walang lesson na natagpuan.")
            finish()
            return
        }

        lifecycleScope.launch {
            val fetched = pronunciationRepository.getActivity(lessonId)

            if (fetched == null || fetched.words.isEmpty()) {
                showTemporaryMessage("Walang pronunciation activity para sa lesson na ito.")
                finish()
                return@launch
            }

            pronunciationData = fetched
            wordList = fetched.words.mapIndexed { index, word ->
                PronunciationWord(
                    id = "$index",
                    word = word,
                    order = index,
                    audioRef = fetched.audioRefs.getOrNull(index) ?: ""
                )
            }

            showCurrentWord()
        }
    }

    private fun showCurrentWord() {
        val currentWord = wordList[currentWordIndex]
        tvTargetWord.text = currentWord.word
        livesRemaining = MAX_LIVES
        updateLivesDisplay()
        updateProgressBar()
        resultStickyNote.visibility = View.GONE
    }

    private fun updateProgressBar() {
        val percent = ((currentWordIndex.toFloat() / wordList.size) * 100).toInt()
        progressBarWords.progress = percent
    }

    private fun updateLivesDisplay() {
        tvLivesCount.text = livesRemaining.toString()
    }

    private fun speakCurrentWord() {
        val currentWord = wordList.getOrNull(currentWordIndex) ?: return
        playPronunciationAudio(currentWord.audioRef, currentWord.word)
    }

    private fun setPreferredTtsLocale() {
        val filipino = Locale("fil", "PH")
        val result = textToSpeech?.setLanguage(filipino)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Fall back to Tagalog locale tag if fil-PH voice isn't installed
            textToSpeech?.setLanguage(Locale("tl", "PH"))
        }
    }

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
        isProcessing = true
        startPulseAnimation()
        micProcessingSpinner.visibility = View.VISIBLE

        val targetWord = wordList[currentWordIndex].word

        pronunciationChecker.startListening(targetWord, object : PronunciationChecker.PronunciationResultListener {
            override fun onResult(candidates: List<String>, confidenceScores: FloatArray?) {
                val result = PronunciationScorer.score(targetWord, candidates, confidenceScores)
                handleScoreResult(result)
            }

            override fun onError(errorMessage: String) {
                stopListeningUi()
                handleFailedAttempt(errorMessage)
            }

            override fun onUnavailable() {
                stopListeningUi()
                showTemporaryMessage("Hindi available ang speech recognition sa device na ito.")
            }

            override fun onLanguageDownloadTriggered() {
                stopListeningUi()
                showTemporaryMessage("Nagda-download ng wikang Filipino para sa speech recognition. Subukan ulit pagkatapos.")
            }
        })
    }

    private fun stopListeningUi() {
        isProcessing = false
        stopPulseAnimation()
        micProcessingSpinner.visibility = View.GONE
    }

    private fun handleScoreResult(result: PronunciationScorer.PronunciationResult) {
        stopListeningUi()

        when (result.scoreBand) {
            PronunciationScorer.ScoreBand.GREAT, PronunciationScorer.ScoreBand.OKAY -> {
                correctCount++
                showResultNote(
                    band = result.scoreBand,
                    recognizedText = result.matchedText ?: ""
                )
                handler.postDelayed({ advanceToNextWord() }, ADVANCE_DELAY_MS)
            }
            PronunciationScorer.ScoreBand.TRY_AGAIN -> {
                handleFailedAttempt(result.matchedText ?: "")
            }
        }
    }

    private fun handleFailedAttempt(recognizedText: String) {
        livesRemaining--
        updateLivesDisplay()

        if (livesRemaining <= 0) {
            showResultNote(
                band = PronunciationScorer.ScoreBand.TRY_AGAIN,
                recognizedText = recognizedText,
                usedUpLivesMessage = true
            )
            handler.postDelayed({ advanceToNextWord() }, ADVANCE_DELAY_MS)
        } else {
            showResultNote(
                band = PronunciationScorer.ScoreBand.TRY_AGAIN,
                recognizedText = recognizedText
            )
        }
    }

    private fun showResultNote(
        band: PronunciationScorer.ScoreBand,
        recognizedText: String,
        usedUpLivesMessage: Boolean = false
    ) {
        resultStickyNote.visibility = View.VISIBLE

        tvResultBand.text = when {
            usedUpLivesMessage -> "Ubos na ang buhay mo sa salitang ito. Lilipat sa susunod na salita..."
            band == PronunciationScorer.ScoreBand.GREAT -> "Ang galing!"
            band == PronunciationScorer.ScoreBand.OKAY -> "Malapit na, tama!"
            else -> "Hindi tama. Subukan ulit."
        }

        tvRecognizedWord.text = if (recognizedText.isNotEmpty()) {
            "Narinig: $recognizedText"
        } else {
            ""
        }

        // TODO: swap resultStickyNote background drawable based on band
        // (bg_sticky_note_green / bg_sticky_note_yellow / bg_sticky_note_red)
        // once those variants are created.
    }

    private fun advanceToNextWord() {
        currentWordIndex++

        if (currentWordIndex >= wordList.size) {
            finishActivityWithScore()
        } else {
            showCurrentWord()
        }
    }

    private fun finishActivityWithScore() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid

        lifecycleScope.launch {
            if (uid != null) {
                try {
                    pronunciationRepository.markActivityFinished(uid, lessonId, correctCount, wordList.size)
                } catch (e: Exception) {
                    Log.e("PronunciationActivity", "Failed to save pronunciation result", e)
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

    private fun showTemporaryMessage(message: String) {
        tvResultBand.text = message
        tvRecognizedWord.text = ""
        resultStickyNote.visibility = View.VISIBLE
    }

    private fun playPronunciationAudio(audioRef: String, wordText: String) {
        mediaPlayer?.release()
        mediaPlayer = null

        if (audioRef.startsWith("http://") || audioRef.startsWith("https://")) {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioRef)
                setOnPreparedListener { start() }
                setOnErrorListener { _, _, _ ->
                    fallbackToTts(wordText)
                    true
                }
                prepareAsync()
            }
        } else {
            val resId = resources.getIdentifier(audioRef, "raw", packageName)
            if (resId != 0) {
                mediaPlayer = MediaPlayer.create(this, resId)
                mediaPlayer?.setOnCompletionListener {
                    it.release()
                    mediaPlayer = null
                }
                mediaPlayer?.start()
            } else {
                fallbackToTts(wordText)
            }
        }
    }

    private fun fallbackToTts(word: String) {
        textToSpeech?.speak(word, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun startPulseAnimation() {
        val ring1 = createRingAnimator(pulseRing1, startDelay = 0L)
        val ring2 = createRingAnimator(pulseRing2, startDelay = 750L)

        pulseAnimatorSet = AnimatorSet().apply {
            playTogether(ring1, ring2)
            start()
        }
    }

    private fun createRingAnimator(ring: View, startDelay: Long): ObjectAnimator {
        ring.visibility = View.VISIBLE
        ring.alpha = 1f
        ring.scaleX = 1f
        ring.scaleY = 1f

        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 2.2f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 2.2f)
        val alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0.9f, 0f)

        return ObjectAnimator.ofPropertyValuesHolder(ring, scaleX, scaleY, alpha).apply {
            duration = 1500
            this.startDelay = startDelay
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimatorSet?.cancel()
        pulseAnimatorSet = null

        listOf(pulseRing1, pulseRing2).forEach { ring ->
            ring.visibility = View.INVISIBLE
            ring.alpha = 1f
            ring.scaleX = 1f
            ring.scaleY = 1f
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pronunciationChecker.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        mediaPlayer?.release()
        mediaPlayer = null
        pulseAnimatorSet?.cancel()
        handler.removeCallbacksAndMessages(null)
    }

}