package com.gathin.tvhome.feature.agent.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.gathin.tvhome.feature.agent.adapter.AgentRecognitionAdapter
import java.util.Locale

class AgentSpeechInput(private val activity: Activity, private val listener: Listener) {

    interface Listener {
        fun onReady()
        fun onSpeechStarted()
        fun onPartial(text: String)
        fun onResult(text: String)
        fun onSilence()
        fun onFailure()
    }

    enum class Start { STARTED, NEEDS_PERMISSION, UNAVAILABLE }

    private var recognizer: SpeechRecognizer? = null

    fun start(): Start {
        if (!hasMicrophonePermission()) {
            activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MIC_REQUEST)
            return Start.NEEDS_PERMISSION
        }
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) return Start.UNAVAILABLE

        release()
        recognizer = SpeechRecognizer.createSpeechRecognizer(activity).apply {
            setRecognitionListener(recognitionListener)
        }
        return try {
            recognizer?.startListening(recognizerIntent())
            Start.STARTED
        } catch (_: Exception) {
            Start.UNAVAILABLE
        }
    }

    fun cancel() {
        recognizer?.cancel()
    }

    fun stopListening() {
        recognizer?.stopListening()
    }

    fun release() {
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {
        }
        recognizer = null
    }

    private fun hasMicrophonePermission(): Boolean {
        return activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun recognizerIntent(): Intent {
        val localeTag = Locale.getDefault().toLanguageTag()
        val language = if (localeTag.isNullOrBlank()) LANGUAGE else localeTag
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, activity.packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                2500L
            )
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
        }
    }

    private fun firstResult(bundle: Bundle?): String {
        val results = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        return if (results.isNullOrEmpty()) "" else results[0].trim()
    }

    private val recognitionListener = object : AgentRecognitionAdapter() {
        override fun onReadyForSpeech(params: Bundle?) = listener.onReady()

        override fun onBeginningOfSpeech() = listener.onSpeechStarted()

        override fun onError(error: Int) {
            val silence = error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            if (silence) listener.onSilence() else listener.onFailure()
        }

        override fun onResults(results: Bundle?) {
            val heard = firstResult(results)
            if (heard.isEmpty()) listener.onSilence() else listener.onResult(heard)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val heard = firstResult(partialResults)
            if (heard.isNotEmpty()) listener.onPartial(heard)
        }
    }

    companion object {
        const val MIC_REQUEST = 7
        private const val LANGUAGE = "vi-VN"
    }
}
