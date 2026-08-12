package com.example.basaya.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.basaya.utils.PronunciationUtils
import android.os.Build
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import androidx.annotation.RequiresApi
import android.speech.ModelDownloadListener


class PronunciationChecker(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var currentLocaleAttempt = LOCALE_FIL
    private var targetWord: String = ""
    private var listener: PronunciationResultListener? = null
    private var languageCheckCompleted = false
    private var confirmedWorkingLocale: String? = null

    companion object {
        private const val LOCALE_FIL = "fil-PH"
        private const val LOCALE_TL = "tl-PH"
    }

    interface PronunciationResultListener {
        /** Called with the recognized text candidates and their confidence scores. */
        fun onResult(candidates: List<String>, confidenceScores: FloatArray?)

        /** Called when recognition fails (no speech, no match, engine error, etc). */
        fun onError(errorMessage: String)

        /** Called if speech recognition isn't available on this device at all. */
        fun onUnavailable()

        /**
         * Called when the language pack isn't downloaded yet (Android 13+ only).
         * The system download prompt has already been triggered — tell the user to wait/retry.
         */
        fun onLanguageDownloadTriggered()
    }

    fun startListening(targetWord: String, listener: PronunciationResultListener) {
        this.targetWord = targetWord
        this.listener = listener

        if (!PronunciationUtils.isSpeechRecognitionAvailable(context)) {
            listener.onUnavailable()
            return
        }

        currentLocaleAttempt = LOCALE_FIL

        // Skip the support check entirely if we already confirmed a working locale this session
        if (confirmedWorkingLocale != null) {
            beginRecognition(confirmedWorkingLocale!!)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !languageCheckCompleted) {
            checkLanguageSupportThenListen(LOCALE_FIL)
        } else {
            beginRecognition(LOCALE_FIL)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkLanguageSupportThenListen(locale: String) {
        val checkRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
        }

        checkRecognizer.checkRecognitionSupport(
            intent,
            context.mainExecutor,
            object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    checkRecognizer.destroy()

                    when {
                        support.installedOnDeviceLanguages.contains(locale) -> {
                            beginRecognition(locale)
                        }
                        support.supportedOnDeviceLanguages.contains(locale) -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                triggerDownloadForLocale(locale, intent)
                            } else {
                                // API 33 only: no locale-targeted download trigger exists.
                                // Just attempt recognition directly; OS may use network as fallback.
                                beginRecognition(locale)
                            }
                        }
                        else -> {
                            if (locale == LOCALE_FIL) {
                                currentLocaleAttempt = LOCALE_TL
                                checkLanguageSupportThenListen(LOCALE_TL)
                            } else {
                                beginRecognition(locale)
                            }
                        }
                    }
                }

                override fun onError(error: Int) {
                    checkRecognizer.destroy()
                    beginRecognition(locale)
                }
            }
        )
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun triggerDownloadForLocale(locale: String, recognizerIntent: Intent) {
        val downloadRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer = downloadRecognizer

        downloadRecognizer.triggerModelDownload(
            recognizerIntent,
            context.mainExecutor,
            object : ModelDownloadListener {
                override fun onSuccess() {
                    // Download finished successfully — model is now safe to use
                    listener?.onLanguageDownloadTriggered()
                }

                override fun onScheduled() {
                    // Download queued but can't be satisfied immediately example no wifi.
                    // No further updates will come on this listener.
                    listener?.onLanguageDownloadTriggered()
                }

                override fun onProgress(completedPercent: Int) {
                    // Optional: could surface % to the UI later if desired
                }

                override fun onError(error: Int) {
                    listener?.onError("Hindi ma-download ang wika. Subukan ulit.")
                }
            }
        )
    }

    private fun beginRecognition(locale: String) {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(createRecognitionListener())

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 300L)
        }

        speechRecognizer?.startListening(intent)

        languageCheckCompleted = true
        confirmedWorkingLocale = locale
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val candidates = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

                if (candidates.isNullOrEmpty()) {
                    handleNoMatch()
                } else {
                    listener?.onResult(candidates, scores)
                }
            }

            override fun onError(error: Int) {
                if (currentLocaleAttempt == LOCALE_FIL && isRetryableError(error)) {
                    currentLocaleAttempt = LOCALE_TL
                    beginRecognition(LOCALE_TL)
                } else {
                    listener?.onError(mapErrorToMessage(error))
                }
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun handleNoMatch() {
        if (currentLocaleAttempt == LOCALE_FIL) {
            currentLocaleAttempt = LOCALE_TL
            beginRecognition(LOCALE_TL)
        } else {
            listener?.onError("Hindi ma-recognize. Subukan ulit.")
        }
    }

    private fun isRetryableError(error: Int): Boolean {
        return error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                error == SpeechRecognizer.ERROR_CLIENT
    }

    private fun mapErrorToMessage(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> "Hindi ma-recognize ang sinabi mo. Subukan ulit."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Walang narinig na boses. Subukan ulit."
            SpeechRecognizer.ERROR_AUDIO -> "May problema sa audio recording."
            SpeechRecognizer.ERROR_NETWORK -> "May problema sa network."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Kailangan ng microphone permission."
            else -> "May naganap na error. Subukan ulit."
        }
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}