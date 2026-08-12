package com.example.basaya.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat

object PronunciationUtils {

    const val RECORD_AUDIO_PERMISSION_CODE = 1001

    /**
     * Checks if RECORD_AUDIO permission has already been granted.
     */
    fun hasRecordAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if speech recognition is available on this device at all.
     * Some devices (e.g. certain Android TV boxes, stripped custom ROMs) don't have it.
     */
    fun isSpeechRecognitionAvailable(context: Context): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }
}