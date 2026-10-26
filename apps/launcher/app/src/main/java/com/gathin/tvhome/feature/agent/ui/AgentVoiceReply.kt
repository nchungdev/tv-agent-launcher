package com.gathin.tvhome.feature.agent.ui
import com.gathin.tvhome.feature.agent.data.*
import com.gathin.tvhome.feature.agent.coordinator.*
import com.gathin.tvhome.feature.agent.widget.*
import com.gathin.tvhome.feature.agent.adapter.*

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Html
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.settings.data.Config
import com.gathin.tvhome.feature.agent.widget.DuinchView
import java.util.Locale


class AgentVoiceReply(
    private val context: Context,
    private val listener: Listener?
) {

    fun interface Listener {
        fun onSpeechFinished()
    }

    private val main = Handler(Looper.getMainLooper())
    private val engine = TextToSpeech(context) { status -> onEngineReady(status) }
    private var targetBot: DuinchView? = null
    private var ready = false
    private var spokenLength = 0
    private var pending = 0
    private var utteranceCount = 0
    private var queuedLine: String? = null

    private val stopTalkingRunnable = Runnable {
        if (targetBot != null && pending == 0) {
            targetBot?.setTalking(false)
        }
    }

    fun setBot(bot: DuinchView?) {
        targetBot = bot
    }

    fun isSpeaking(): Boolean = pending > 0

    fun startAnswer() {
        spokenLength = 0
    }

    fun feed(answerText: String, complete: Boolean) {
        val mode = voiceMode(context)
        if (mode == VOICE_OFF) return

        val answer = speechText(answerText)
        if (!ready || spokenLength >= answer.length) return

        val end = if (complete) answer.length else lastSentenceEnd(answer)
        if (end <= spokenLength) return

        val sentence = answer.substring(spokenLength, end).replace(Regex(MARKUP), "")
        spokenLength = end
        val speech = sentence.replace(Regex("\\s+"), " ").trim()
        if (speech.isEmpty()) return

        applyVoiceStyle(mode)
        pending++
        utteranceCount++
        triggerTalking(speech)
        engine.speak(speech, TextToSpeech.QUEUE_ADD, null, UTTERANCE_PREFIX + utteranceCount)
    }

    fun speakLine(text: String?) {
        val mode = voiceMode(context)
        if (mode == VOICE_OFF || text.isNullOrBlank()) return

        if (!ready) {
            queuedLine = text
            return
        }

        stop()
        applyVoiceStyle(mode)
        val speech = speechText(text).replace(Regex(MARKUP), "").replace(Regex("\\s+"), " ").trim()
        if (speech.isEmpty()) return

        pending = 1
        utteranceCount++
        triggerTalking(speech)
        engine.speak(speech, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_PREFIX + utteranceCount)
    }

    private fun triggerTalking(speech: String) {
        if (targetBot != null) {
            main.removeCallbacks(stopTalkingRunnable)
            targetBot?.setTalking(true)
            val readingTime = (speech.length * 65L / speedMultiplier()).toLong()
            val estimatedDuration = readingTime.coerceIn(1200L, 6000L)
            main.postDelayed(stopTalkingRunnable, estimatedDuration)
        }
    }

    private fun applyVoiceStyle(mode: Int) {
        val speed = speedMultiplier()
        when (mode) {
            VOICE_ROBOT_CUTE -> {
                engine.setPitch(1.48f)
                engine.setSpeechRate(1.18f * speed)
            }
            VOICE_ROBOT_DEEP -> {
                engine.setPitch(0.72f)
                engine.setSpeechRate(0.96f * speed)
            }
            VOICE_NATURAL -> {
                engine.setPitch(1.0f)
                engine.setSpeechRate(speed)
            }
        }
    }

    private fun speedMultiplier(): Float = SPEED_OPTIONS[speedIndex(context)]

    private fun speechText(answer: String): String {
        var plain = answer.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), "")
        plain = plain.replace(Regex("!\\[([^\\]]*)\\]\\([^)]*\\)"), "$1")
        plain = plain.replace(Regex("\\[([^\\]]+)\\]\\([^)]*\\)"), "$1")
        plain = plain.replace(Regex("(?m)^\\s*\\|?[ :|-]+\\|[ :|-]*$"), "")
        plain = plain.replace('|', ',')
        return Html.fromHtml(plain, Html.FROM_HTML_MODE_COMPACT).toString()
    }

    fun stop() {
        pending = 0
        main.removeCallbacks(stopTalkingRunnable)
        targetBot?.setTalking(false)
        engine.stop()
    }

    fun shutdown() {
        stop()
        engine.shutdown()
    }

    private fun onEngineReady(status: Int) {
        if (status != TextToSpeech.SUCCESS) return

        val result = engine.setLanguage(Locale("vi", "VN"))
        ready = result >= TextToSpeech.LANG_AVAILABLE
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                main.post {
                    if (targetBot != null) {
                        main.removeCallbacks(stopTalkingRunnable)
                        targetBot?.setTalking(true)
                    }
                }
            }

            override fun onDone(utteranceId: String?) {
                main.post { utteranceEnded() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                main.post { utteranceEnded() }
            }
        })

        if (ready && queuedLine != null) {
            val line = queuedLine
            queuedLine = null
            speakLine(line)
        }
    }

    private fun utteranceEnded() {
        pending = maxOf(0, pending - 1)
        if (pending == 0) {
            targetBot?.setTalking(false)
            listener?.onSpeechFinished()
        }
    }

    private fun lastSentenceEnd(answer: String): Int {
        for (index in answer.length - 2 downTo spokenLength) {
            val ending = SENTENCE_ENDINGS.indexOf(answer[index]) >= 0
            if (ending && Character.isWhitespace(answer[index + 1])) return index + 1
        }
        return spokenLength
    }

    companion object {
        const val VOICE_KEY = "duinch_voice"
        const val VOICE_ROBOT_CUTE = 0
        const val VOICE_ROBOT_DEEP = 1
        const val VOICE_NATURAL = 2
        const val VOICE_OFF = 3
        const val VOICE_COUNT = 4

        const val SPEED_KEY = "duinch_speed"
        @JvmField val SPEED_OPTIONS = floatArrayOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        const val SPEED_DEFAULT = 1

        private const val SENTENCE_ENDINGS = ".!?…\n"
        private const val MARKUP = "[*_`#>|\\[\\]]+"
        private const val UTTERANCE_PREFIX = "duinch-"

        @JvmStatic
        fun voiceMode(context: Context): Int {
            val stored = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .getInt(VOICE_KEY, VOICE_OFF)
            return if (stored < 0 || stored >= VOICE_COUNT) VOICE_OFF else stored
        }

        @JvmStatic
        fun setVoiceMode(context: Context, nextMode: Int) {
            val clamped = nextMode.coerceIn(0, VOICE_COUNT - 1)
            context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .edit().putInt(VOICE_KEY, clamped).apply()
        }

        @JvmStatic
        fun voiceModeLabel(context: Context, mode: Int): String {
            return when (mode) {
                VOICE_ROBOT_DEEP -> context.getString(R.string.duinch_voice_deep)
                VOICE_NATURAL -> context.getString(R.string.duinch_voice_natural)
                VOICE_OFF -> context.getString(R.string.duinch_voice_off)
                else -> context.getString(R.string.duinch_voice_cute)
            }
        }

        @JvmStatic
        fun speedIndex(context: Context): Int {
            val stored = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .getInt(SPEED_KEY, SPEED_DEFAULT)
            return if (stored < 0 || stored >= SPEED_OPTIONS.size) SPEED_DEFAULT else stored
        }

        @JvmStatic
        fun setSpeedIndex(context: Context, nextIndex: Int) {
            val clamped = nextIndex.coerceIn(0, SPEED_OPTIONS.size - 1)
            context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .edit().putInt(SPEED_KEY, clamped).apply()
        }

        @JvmStatic
        fun speedLabel(index: Int): String {
            return String.format(Locale.US, "%.2f", SPEED_OPTIONS[index])
                .replace(Regex("0+$"), "").replace(Regex("\\.$"), "") + "×"
        }
    }
}
