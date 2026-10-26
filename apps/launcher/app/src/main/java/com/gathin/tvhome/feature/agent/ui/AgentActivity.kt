package com.gathin.tvhome.feature.agent.ui
import com.gathin.tvhome.feature.agent.data.*
import com.gathin.tvhome.feature.agent.coordinator.*
import com.gathin.tvhome.feature.agent.widget.*
import com.gathin.tvhome.feature.agent.adapter.*

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.agent.data.AgentClient
import com.gathin.tvhome.feature.agent.adapter.AgentRecognitionAdapter
import com.gathin.tvhome.feature.agent.widget.AgentAnswerView
import com.gathin.tvhome.feature.agent.coordinator.DuinchBrain
import com.gathin.tvhome.feature.agent.widget.DuinchView
import com.gathin.tvhome.feature.main.widget.TypingGreetingView
import com.gathin.tvhome.feature.agent.ui.AgentVoiceReply
import java.util.ArrayList
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class AgentActivity : Activity() {

    private val main = Handler(Looper.getMainLooper())
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val session = UUID.randomUUID().toString()
    private var recognizer: SpeechRecognizer? = null
    private var voiceReply: AgentVoiceReply? = null
    private var state = IDLE
    private var speakReplies = false
    private var requestDone = false
    private var requestGeneration = 0
    private var foreground = false
    private var activeRequest: AgentClient.Request? = null
    private var pendingAnswer: String? = null

    private var heardSpeech = false
    private var hasAnswer = false
    private var lastInteractionAt = android.os.SystemClock.uptimeMillis()

    private lateinit var panel: LinearLayout
    private var botView: DuinchView? = null
    private var line: TextView? = null
    private lateinit var answer: AgentAnswerView
    private lateinit var chips: HorizontalScrollView
    private var chipsRow: LinearLayout? = null
    private var speakChip: TextView? = null
    private var voiceChip: TextView? = null
    private var viewerChip: TextView? = null
    private lateinit var input: EditText

    private val renderAnswer = Runnable {
        if (foreground && pendingAnswer != null) showPartialAnswer(pendingAnswer!!)
        pendingAnswer = null
    }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        if (com.gathin.tvhome.service.ClockDream.isDreaming) {
            finish()
            return
        }
        if (!preferences().contains("speak_default_muted")) {
            preferences().edit().putBoolean(SPEAK_PREFERENCE, false)
                .putBoolean("speak_default_muted", true).apply()
        }
        speakReplies = preferences().getBoolean(SPEAK_PREFERENCE, false)

        val root = FrameLayout(this).apply { setBackgroundColor(DIM_COLOR) }
        panel = buildPanel()
        root.addView(panel, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)

        val vr = AgentVoiceReply(this) { onSpeechFinished() }
        vr.setBot(botView)
        voiceReply = vr
        resetState()
        startListening()
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        answer.onResume()
    }

    override fun onPause() {
        foreground = false
        cancelRequest()
        recognizer?.cancel()
        voiceReply?.stop()
        line?.animate()?.cancel()
        answer.onPause()
        setState(IDLE)
        super.onPause()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        lastInteractionAt = android.os.SystemClock.uptimeMillis()
        botView?.wake()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        resetState()
        startListening()
    }

    private fun cancelRequest() {
        requestGeneration++
        activeRequest?.cancel()
        activeRequest = null
        main.removeCallbacks(renderAnswer)
        pendingAnswer = null
    }

    private fun resetState() {
        cancelRequest()
        heardSpeech = false
        hasAnswer = false
        voiceReply?.stop()
        recognizer?.cancel()
        line?.let {
            it.animate()?.cancel()
            it.alpha = 1f
            it.setTextColor(TEXT_COLOR)
            it.text = ""
        }
        answer.setText("")
        input.setText("")
        input.visibility = View.GONE
        line?.visibility = View.VISIBLE
        hideKeyboard()
        chips.visibility = View.VISIBLE
        setState(IDLE)
    }

    private fun buildPanel(): LinearLayout {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(26), 0, 0)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        column.addView(buildTopRow())
        chips = buildChips()
        column.addView(chips, LinearLayout.LayoutParams(MATCH, WRAP))
        answer = AgentAnswerView(this)
        column.addView(answer, LinearLayout.LayoutParams(MATCH, 0, 1f))
        return column
    }

    private fun buildTopRow(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            minimumHeight = dp(48)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(CONTENT_PADDING_DP), 0, dp(CONTENT_PADDING_DP), 0)
        }
        val bot = DuinchView(this)
        botView = bot
        row.addView(bot, LinearLayout.LayoutParams(dp(54), dp(42)))

        val textSlot = FrameLayout(this).apply {
            setPadding(dp(LINE_PADDING_DP), 0, 0, 0)
        }

        val tv = TypingGreetingView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, LINE_SIZE_SP.toFloat())
            typeface = resources.getFont(R.font.sans_medium)
            letterSpacing = 0.01f
            setTextColor(TEXT_COLOR)
            maxLines = 2
        }
        tv.setBot(bot)
        line = tv
        textSlot.addView(tv, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER_VERTICAL))

        input = EditText(this).apply {
            setHint(R.string.agent_type_hint)
            setTextColor(TEXT_COLOR)
            setHintTextColor(HINT_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, LINE_SIZE_SP.toFloat())
            typeface = resources.getFont(R.font.sans_medium)
            background = null
            setPadding(0, 0, 0, 0)
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEND
            visibility = View.GONE
            setOnEditorActionListener { field, _, _ ->
                val typed = field.text.toString().trim()
                field.setText("")
                if (typed.isNotEmpty()) send(typed)
                true
            }
        }
        textSlot.addView(input, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER_VERTICAL))

        row.addView(textSlot, LinearLayout.LayoutParams(0, WRAP, 1f))
        return row
    }

    private fun buildChips(): HorizontalScrollView {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(CONTENT_PADDING_DP), dp(16), dp(CONTENT_PADDING_DP), dp(8))
        }
        chipsRow = row
        row.addView(chip(null, R.drawable.ic_keyboard) { showInput() })
        val sc = chip(getString(R.string.agent_chip_speak), 0) { startListening() }
        speakChip = sc
        row.addView(sc)
        val vc = chip(voiceLabel(), 0) { toggleSpeakReplies() }
        voiceChip = vc
        row.addView(vc)
        val viewC = chip(viewerLabel(), 0) { toggleViewer() }
        viewerChip = viewC
        row.addView(viewC)
        populateDynamicChips()

        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    private fun updateViewerChip() {
        viewerChip?.text = viewerLabel()
    }

    private fun viewerLabel(): String {
        val profile = DuinchBrain.viewer(this)
        return String.format(getString(R.string.duinch_chip_viewer), DuinchBrain.viewerLabel(this, profile))
    }

    private fun toggleViewer() {
        val next = (DuinchBrain.viewer(this) + 1) % DuinchBrain.VIEWER_COUNT
        DuinchBrain.setViewer(this, next)
        updateViewerChip()
        populateDynamicChips()
        botView?.pulsePlayful(DuinchView.LOVE, 1400)
        val greeting = DuinchBrain.agentGreeting(this)
        fadePrompt(greeting, BOT_ACCENT)
        if (speakReplies && state == IDLE) {
            voiceReply?.speakLine(greeting)
        }
    }

    private fun populateDynamicChips() {
        val row = chipsRow ?: return
        while (row.childCount > 4) row.removeViewAt(4)
        for (suggestion in DuinchBrain.suggestions(this)) {
            row.addView(chip(suggestion, 0) { send(suggestion) })
        }
    }

    private fun setState(next: Int) {
        state = next
        val answering = next == THINKING || next == SPEAKING
        val bot = botView
        if (bot != null) {
            when (next) {
                LISTENING -> bot.setMood(DuinchView.EXCITED)
                THINKING -> bot.setMood(DuinchView.THINKING)
                SPEAKING -> bot.setMood(DuinchView.HAPPY)
                else -> bot.setMood(DuinchView.IDLE)
            }
        }
        chips.visibility = if (answering) View.GONE else View.VISIBLE
        if (answering) {
            input.visibility = View.GONE
            line?.visibility = View.VISIBLE
            hideKeyboard()
        } else if (input.visibility != View.VISIBLE && !chips.hasFocus()) {
            speakChip?.requestFocus()
        }
    }

    private fun fadePrompt(text: CharSequence, color: Int) {
        val l = line ?: return
        l.animate().cancel()
        l.animate().alpha(0f).setDuration(160).withEndAction {
            l.setTextColor(color)
            l.alpha = 1f
            (l as? TypingGreetingView)?.showMessage(text, animate = false) ?: run { l.text = text }
        }.start()
    }

    private fun showIdle(message: CharSequence) {
        setState(IDLE)
        line?.let {
            it.animate().cancel()
            it.alpha = 1f
            it.setTextColor(BOT_ACCENT)
            (it as? TypingGreetingView)?.showMessage(message, animate = false) ?: run { it.text = message }
        }
        botView?.setMood(DuinchView.IDLE)
    }

    private fun startListening() {
        if (state == THINKING || state == SPEAKING) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MIC_REQUEST)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showIdle(getString(R.string.agent_voice_unavailable))
            return
        }

        voiceReply?.stop()
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {}
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(recognitionListener)
        }
        heardSpeech = false
        hasAnswer = false
        answer.setText("")
        line?.let {
            it.animate().cancel()
            it.alpha = 1f
            it.setTextColor(BOT_ACCENT)
            val msg = getString(R.string.agent_listening_steady)
            (it as? TypingGreetingView)?.showMessage(msg, animate = false) ?: run { it.text = msg }
        }
        setState(LISTENING)
        try {
            recognizer?.startListening(recognizerIntent())
        } catch (_: Exception) {
            showIdle(getString(R.string.agent_voice_unavailable))
        }
    }

    private fun recognizerIntent(): Intent {
        val localeTag = Locale.getDefault().toLanguageTag()
        val language = if (localeTag.isNullOrBlank()) LANGUAGE else localeTag
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
        }
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        if (code != MIC_REQUEST) return
        val granted = results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED
        if (granted) startListening() else showIdle(getString(R.string.agent_mic_denied))
    }

    private val recognitionListener = object : AgentRecognitionAdapter() {
        override fun onReadyForSpeech(params: Bundle?) {
            if (state != LISTENING) return
            botView?.setMood(DuinchView.HAPPY)
        }

        override fun onBeginningOfSpeech() {
            if (state != LISTENING) return
            heardSpeech = true
            botView?.setMood(DuinchView.EXCITED)
            line?.let {
                it.animate().cancel()
                it.alpha = 1f
                it.setTextColor(TEXT_COLOR)
                val msg = getString(R.string.agent_hearing_speech)
                (it as? TypingGreetingView)?.showMessage(msg, animate = false) ?: run { it.text = msg }
            }
        }

        override fun onError(error: Int) {
            if (state != LISTENING) return
            val silence = error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            val messageRes = if (silence) R.string.agent_idle_silence else R.string.agent_voice_unavailable
            showIdle(getString(messageRes))
        }

        override fun onResults(results: Bundle?) {
            if (state != LISTENING) return
            val heard = firstResult(results)
            if (heard.isEmpty()) {
                showIdle(getString(R.string.agent_idle_silence))
            } else {
                val detected = DuinchBrain.detectViewerFromSpeech(heard)
                if (detected >= 0) {
                    DuinchBrain.setViewer(this@AgentActivity, detected)
                    updateViewerChip()
                    populateDynamicChips()
                    botView?.pulsePlayful(DuinchView.LOVE, 1800)
                }
                send(heard)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (state != LISTENING) return
            val heard = firstResult(partialResults)
            if (heard.isNotEmpty()) {
                heardSpeech = true
                line?.let {
                    it.animate().cancel()
                    it.alpha = 1f
                    it.setTextColor(TEXT_COLOR)
                    (it as? TypingGreetingView)?.showMessage(heard, animate = false) ?: run { it.text = heard }
                }
            }
        }
    }

    private fun firstResult(bundle: Bundle?): String {
        val results = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        return if (results.isNullOrEmpty()) "" else results[0].trim()
    }

    private fun send(request: String) {
        if (state == THINKING || state == SPEAKING) return

        heardSpeech = true
        hasAnswer = true
        recognizer?.cancel()
        voiceReply?.stop()
        voiceReply?.startAnswer()
        requestDone = false
        setState(THINKING)
        line?.let {
            it.animate().cancel()
            it.alpha = 1f
            it.setTextColor(TEXT_COLOR)
            (it as? TypingGreetingView)?.showMessage(request, animate = false) ?: run { it.text = request }
        }
        answer.showLoading(DuinchBrain.thinkingLine(this))
        val received = StringBuilder()
        val generation = ++requestGeneration
        val connection = AgentClient.Request()
        activeRequest = connection
        worker.execute { askAgent(request, received, generation, connection) }
    }

    private fun askAgent(request: String, received: StringBuilder, generation: Int, connection: AgentClient.Request) {
        var failure: String? = null
        val lastDelivery = longArrayOf(0)
        try {
            AgentClient.ask(applicationContext, session, request, { piece ->
                if (received.length + piece.length > MAX_ANSWER_CHARS) {
                    connection.cancel()
                    throw IllegalStateException("Câu trả lời vượt giới hạn hiển thị")
                }
                received.append(piece)
                val now = android.os.SystemClock.uptimeMillis()
                if (now - lastDelivery[0] >= 150) {
                    lastDelivery[0] = now
                    val snapshot = received.toString()
                    main.post {
                        if (generation != requestGeneration || !foreground) return@post
                        val scheduled = pendingAnswer != null
                        pendingAnswer = snapshot
                        if (!scheduled) main.postDelayed(renderAnswer, 150)
                    }
                }
            }, connection)
        } catch (e: AgentClient.AgentException) {
            failure = getString(e.messageRes)
        } catch (e: Exception) {
            failure = e.message
        }
        val error = failure
        val finalAnswer = received.toString()
        main.post {
            if (generation != requestGeneration || !foreground) return@post
            main.removeCallbacks(renderAnswer)
            pendingAnswer = null
            if (error == null) answer.setText(finalAnswer)
            finishRequest(error, finalAnswer)
        }
    }

    private fun showPartialAnswer(soFar: String) {
        if (isDestroyed) return
        answer.setText(soFar)
        speakIfEnabled(soFar, false)
    }

    private fun finishRequest(error: String?, fullAnswer: String) {
        if (isDestroyed) return
        requestDone = true
        if (error != null) {
            answer.setText(error)
            botView?.pulsePlayful(DuinchView.SAD, 2000)
        } else {
            speakIfEnabled(fullAnswer, true)
            if (botView != null && voiceReply?.isSpeaking() != true) {
                botView?.pulsePlayful(DuinchView.HAPPY, 2500)
            }
        }
        setState(if (voiceReply?.isSpeaking() == true) SPEAKING else IDLE)
    }

    private fun speakIfEnabled(text: String, complete: Boolean) {
        if (speakReplies) voiceReply?.feed(text, complete)
    }

    private fun onSpeechFinished() {
        if (requestDone && state == SPEAKING) setState(IDLE)
    }

    private fun preferences(): SharedPreferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE)

    private fun voiceLabel(): String = getString(if (speakReplies) R.string.agent_voice_on else R.string.agent_voice_off)

    private fun toggleSpeakReplies() {
        speakReplies = !speakReplies
        preferences().edit().putBoolean(SPEAK_PREFERENCE, speakReplies).apply()
        voiceChip?.text = voiceLabel()
        if (!speakReplies) voiceReply?.stop()
    }

    private fun showInput() {
        recognizer?.cancel()
        state = IDLE
        botView?.setMood(DuinchView.IDLE)
        chips.visibility = View.GONE
        line?.visibility = View.GONE
        input.visibility = View.VISIBLE
        input.requestFocus()
        val keyboard = getSystemService(InputMethodManager::class.java)
        keyboard?.showSoftInput(input, 0)
    }

    private fun hideKeyboard() {
        val keyboard = getSystemService(InputMethodManager::class.java)
        keyboard?.hideSoftInputFromWindow(input.windowToken, 0)
        input.clearFocus()
        panel.requestFocus()
    }

    private fun closeInput() {
        input.setText("")
        input.visibility = View.GONE
        line?.visibility = View.VISIBLE
        hideKeyboard()
        chips.visibility = View.VISIBLE
        speakChip?.requestFocus()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_HOME || keyCode == KeyEvent.KEYCODE_ALL_APPS) {
            finish()
            return true
        }
        val confirm = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
        if (confirm && state == SPEAKING) {
            voiceReply?.stop()
            setState(IDLE)
            return true
        }
        if ((confirm || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) && state == LISTENING) {
            if (!heardSpeech) {
                recognizer?.cancel()
                showIdle(getString(R.string.agent_idle_silence))
            } else {
                recognizer?.stopListening()
            }
            return true
        }
        val typing = input.visibility == View.VISIBLE
        if (keyCode == KeyEvent.KEYCODE_BACK && typing && state == IDLE) {
            closeInput()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (keyCode == KeyEvent.KEYCODE_HOME || keyCode == KeyEvent.KEYCODE_ALL_APPS) {
            finish()
            return true
        }
        val bot = botView
        if (event.action == KeyEvent.ACTION_DOWN && bot != null) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    bot.lookAt(-1f, 0.2f)
                    if (state == LISTENING && !heardSpeech) {
                        recognizer?.cancel()
                        showIdle(DuinchBrain.agentGreeting(this@AgentActivity))
                    }
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    bot.lookAt(1f, 0.2f)
                    if (state == LISTENING && !heardSpeech) {
                        recognizer?.cancel()
                        showIdle(DuinchBrain.agentGreeting(this@AgentActivity))
                    }
                }
                KeyEvent.KEYCODE_DPAD_UP -> bot.lookAt(0f, -0.8f)
                KeyEvent.KEYCODE_DPAD_DOWN -> bot.lookAt(0f, 0.8f)
                else -> bot.lookAt(0f, 0f)
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        cancelRequest()
        recognizer?.destroy()
        answer.destroy()
        voiceReply?.shutdown()
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun chip(label: String?, icon: Int, onClick: Runnable): TextView {
        val chip = TextView(this)
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, CHIP_SIZE_SP.toFloat())
        chip.gravity = Gravity.CENTER
        chip.isSingleLine = true
        val sidePadding = dp(if (label == null) 12 else 20)
        chip.setPadding(sidePadding, dp(8), sidePadding, dp(8))
        if (label != null) chip.text = label
        if (icon != 0) chip.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0)
        chip.isFocusable = true
        chip.isClickable = true
        chip.setOnClickListener { onClick.run() }
        paintChip(chip, false)
        chip.setOnFocusChangeListener { _, focused -> paintChip(chip, focused) }
        val params = LinearLayout.LayoutParams(WRAP, WRAP)
        params.rightMargin = dp(10)
        chip.layoutParams = params
        return chip
    }

    private fun paintChip(chip: TextView, focused: Boolean) {
        val background = GradientDrawable()
        background.cornerRadius = dp(40).toFloat()
        background.setColor(if (focused) CHIP_FOCUSED_COLOR else CHIP_COLOR)
        chip.background = background

        val foreground = if (focused) CHIP_FOCUSED_TEXT_COLOR else TEXT_COLOR
        chip.setTextColor(foreground)
        for (icon in chip.compoundDrawables) {
            icon?.setColorFilter(foreground, PorterDuff.Mode.SRC_IN)
        }
    }

    private fun dp(value: Int): Int = Math.round(value * resources.displayMetrics.density)

    companion object {
        private const val MIC_REQUEST = 7
        private const val IDLE = 0
        private const val LISTENING = 1
        private const val THINKING = 2
        private const val SPEAKING = 3
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private const val PREFERENCES = "agent"
        private const val SPEAK_PREFERENCE = "speak"
        private const val LANGUAGE = "vi-VN"
        private const val DIM_COLOR = 0xFF080B10.toInt()
        private const val TEXT_COLOR = 0xFFE6E6E6.toInt()
        private const val HINT_COLOR = 0xFF7A838C.toInt()
        private const val CHIP_COLOR = 0xFF262B33.toInt()
        private const val CHIP_FOCUSED_COLOR = 0xFFE6E1EC.toInt()
        private const val CHIP_FOCUSED_TEXT_COLOR = 0xFF1B1B1F.toInt()
        private const val CONTENT_PADDING_DP = 56
        private const val LINE_PADDING_DP = 12
        private const val LINE_SIZE_SP = 20
        private const val CHIP_SIZE_SP = 15
        private const val BOT_ACCENT = 0xFF00E5FF.toInt()
        private const val MAX_ANSWER_CHARS = 32_768
    }
}
