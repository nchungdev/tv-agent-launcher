package com.gathin.tvhome.feature.agent.coordinator
import com.gathin.tvhome.feature.agent.data.*
import com.gathin.tvhome.feature.agent.ui.*
import com.gathin.tvhome.feature.agent.widget.*

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.gathin.tvhome.feature.agent.adapter.AgentRecognitionAdapter


class VoiceInputController(
    private val context: Context,
    private val listener: Listener
) {

    interface Listener {
        fun onSpeechPartial(text: String)
        fun onSpeechResult(text: String)
        fun onSpeechSilence()
        fun onSpeechError(error: Int)
    }

    private var recognizer: SpeechRecognizer? = null

    fun initialize() {
        if (recognizer == null && SpeechRecognizer.isRecognitionAvailable(context)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(recognitionCallback)
            }
        }
    }

    fun startListening(language: String = "vi-VN") {
        initialize()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        recognizer?.startListening(intent)
    }

    fun stopListening() {
        recognizer?.stopListening()
    }

    fun cancel() {
        recognizer?.cancel()
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private val recognitionCallback = object : AgentRecognitionAdapter() {
        override fun onError(error: Int) {
            val silence = error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            if (silence) {
                listener.onSpeechSilence()
            } else {
                listener.onSpeechError(error)
            }
        }

        override fun onResults(results: Bundle?) {
            val heard = firstResult(results)
            if (heard.isEmpty()) {
                listener.onSpeechSilence()
            } else {
                listener.onSpeechResult(heard)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val heard = firstResult(partialResults)
            if (heard.isNotEmpty()) {
                listener.onSpeechPartial(heard)
            }
        }
    }

    private fun firstResult(bundle: Bundle?): String {
        val results = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        return if (results.isNullOrEmpty()) "" else results[0].trim()
    }
}
