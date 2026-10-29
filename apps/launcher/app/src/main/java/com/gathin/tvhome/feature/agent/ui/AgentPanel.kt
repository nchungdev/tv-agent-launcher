package com.gathin.tvhome.feature.agent.ui

import android.app.Activity
import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.gathin.tvhome.R
import com.gathin.tvhome.core.view.AgentCardData
import com.gathin.tvhome.feature.agent.data.SearchTab
import com.gathin.tvhome.feature.agent.coordinator.DuinchBrain
import com.gathin.tvhome.feature.agent.widget.DuinchView

class AgentPanel(private val activity: Activity, private val host: Host) :
    FrameLayout(activity),
    AgentPanelViews.Actions,
    AgentSpeechInput.Listener,
    AgentRequestRunner.Callbacks {

    interface Host {
        fun closePanel()
    }

    private val views = AgentPanelViews(activity, this)
    private val speech = AgentSpeechInput(activity, this)
    private val runner = AgentRequestRunner(activity, this)
    private val replyPreference = AgentReplyPreference(activity)
    private val keys = AgentPanelKeys(views, keyTarget())
    private var voiceReply: AgentVoiceReply? = null
    private var state = IDLE
    private var requestDone = false
    private var heardSpeech = false
    private var active = false
    private var released = false

    init {
        background = AgentPanelViews.scrim()
        addView(views.build(replyPreference.speakReplies), LayoutParams(MATCH, MATCH))
        voiceReply = AgentVoiceReply(activity) { onSpeechFinished() }.also { it.setBot(views.bot) }
    }

    fun start() {
        resume()
        restart()
    }

    fun resume() {
        active = true
        views.answer.onResume()
    }

    fun restart() {
        resetState()
        showIdle(DuinchBrain.agentGreeting(activity))
    }

    fun pause() {
        active = false
        runner.cancel()
        speech.cancel()
        voiceReply?.stop()
        views.line.animate()?.cancel()
        views.answer.onPause()
        setState(IDLE)
    }

    fun release() {
        if (released) return
        released = true
        pause()
        speech.release()
        runner.destroy()
        views.answer.destroy()
        voiceReply?.shutdown()
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) startListening() else showIdle(activity.getString(R.string.agent_mic_denied))
    }

    override fun focusSearch(focused: View, direction: Int): View {
        return FocusFinder.getInstance().findNextFocus(this, focused, direction) ?: focused
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (keys.owns(event.keyCode)) return keys.consume(event)

        val pressed = event.action == KeyEvent.ACTION_DOWN
        if (pressed) keys.look(event.keyCode)
        if (super.dispatchKeyEvent(event)) return true
        return pressed && keys.fallback(event.keyCode)
    }

    private fun resetState() {
        runner.cancel()
        heardSpeech = false
        voiceReply?.stop()
        speech.cancel()
        views.resetLine()
        views.answer.clear()
        views.hideInput()
        views.chips.visibility = VISIBLE
        setState(IDLE)
    }

    private fun setState(next: Int) {
        state = next
        val answering = next == THINKING || next == SPEAKING
        views.bot.setMood(moodFor(next))
        views.showChips(!answering)
        if (answering) {
            views.hideInput()
        } else if (!views.isTyping() && !views.hasChipFocus() && !views.answer.hasResults) {
            views.focusSpeak()
        }
    }

    private fun moodFor(next: Int): Int = when (next) {
        LISTENING -> DuinchView.EXCITED
        THINKING -> DuinchView.THINKING
        SPEAKING -> DuinchView.HAPPY
        else -> DuinchView.IDLE
    }

    private fun showIdle(message: CharSequence) {
        setState(IDLE)
        views.showLine(message, AgentPanelViews.BOT_ACCENT)
        views.bot.setMood(DuinchView.IDLE)
    }

    private fun startListening() {
        if (state == THINKING || state == SPEAKING) return

        voiceReply?.stop()
        val started = speech.start()
        if (started == AgentSpeechInput.Start.NEEDS_PERMISSION) return
        if (started == AgentSpeechInput.Start.UNAVAILABLE) return onFailureIdle()
        heardSpeech = false
        views.answer.clear()
        val listening = activity.getString(R.string.agent_listening_steady)
        views.showLine(listening, AgentPanelViews.BOT_ACCENT)
        setState(LISTENING)
    }

    private fun send(request: String) {
        if (state == THINKING || state == SPEAKING) return

        heardSpeech = true
        speech.cancel()
        voiceReply?.stop()
        voiceReply?.startAnswer()
        requestDone = false
        setState(THINKING)
        views.showLine(request, AgentPanelViews.TEXT_COLOR)
        views.answer.clear()
        views.answer.showLoading(DuinchBrain.thinkingLine(activity))
        runner.send(request)
    }

    override fun onPartialAnswer(soFar: String) {
        if (released || !active) return
        views.answer.setText(soFar)
        speakIfEnabled(soFar, false)
    }

    override fun onCards(cards: List<AgentCardData>) {
        if (!released && active) views.answer.setCards(cards)
    }

    override fun onSearchResults(tabs: List<SearchTab>) = views.answer.setSearch(tabs)

    override fun onFinished(error: String?, answer: String) {
        if (released || !active) return
        requestDone = true
        if (error != null) {
            views.answer.setText(error)
            views.bot.pulsePlayful(DuinchView.SAD, 2000)
        } else {
            views.answer.setText(answer)
            speakIfEnabled(answer, true)
            if (voiceReply?.isSpeaking() != true) views.bot.pulsePlayful(DuinchView.HAPPY, 2500)
        }
        setState(if (voiceReply?.isSpeaking() == true) SPEAKING else IDLE)
    }

    private fun speakIfEnabled(text: String, complete: Boolean) {
        if (replyPreference.speakReplies) voiceReply?.feed(text, complete)
    }

    private fun onSpeechFinished() {
        if (requestDone && state == SPEAKING) setState(IDLE)
    }

    override fun onReady() {
        if (state == LISTENING) views.bot.setMood(DuinchView.HAPPY)
    }

    override fun onSpeechStarted() {
        if (state != LISTENING) return
        heardSpeech = true
        views.bot.setMood(DuinchView.EXCITED)
        val hearing = activity.getString(R.string.agent_hearing_speech)
        views.showLine(hearing, AgentPanelViews.TEXT_COLOR)
    }

    override fun onPartial(text: String) {
        if (state != LISTENING) return
        heardSpeech = true
        views.showLine(text, AgentPanelViews.TEXT_COLOR)
    }

    override fun onResult(text: String) {
        if (state != LISTENING) return
        val detected = DuinchBrain.detectViewerFromSpeech(text)
        if (detected >= 0) {
            DuinchBrain.setViewer(activity, detected)
            views.setViewerLabel()
            views.populateSuggestions()
            views.bot.pulsePlayful(DuinchView.LOVE, 1800)
        }
        send(text)
    }

    override fun onSilence() {
        if (state == LISTENING) showIdle(activity.getString(R.string.agent_idle_silence))
    }

    override fun onFailure() {
        if (state == LISTENING) onFailureIdle()
    }

    private fun onFailureIdle() = showIdle(activity.getString(R.string.agent_voice_unavailable))

    override fun typeRequested() {
        speech.cancel()
        state = IDLE
        views.bot.setMood(DuinchView.IDLE)
        views.showInput()
    }

    override fun speakRequested() = startListening()
    override fun voiceToggled() {
        val enabled = !replyPreference.speakReplies
        replyPreference.speakReplies = enabled
        views.setVoiceLabel(enabled)
        if (!enabled) voiceReply?.stop()
    }

    override fun viewerToggled() {
        val next = (DuinchBrain.viewer(activity) + 1) % DuinchBrain.VIEWER_COUNT
        DuinchBrain.setViewer(activity, next)
        views.setViewerLabel()
        views.populateSuggestions()
        views.bot.pulsePlayful(DuinchView.LOVE, 1400)
        val greeting = DuinchBrain.agentGreeting(activity)
        views.fadeLine(greeting, AgentPanelViews.BOT_ACCENT)
        if (replyPreference.speakReplies && state == IDLE) voiceReply?.speakLine(greeting)
    }

    override fun suggestionChosen(text: String) = send(text)
    override fun typed(text: String) = send(text)

    private fun keyTarget() = object : AgentPanelKeys.Target {
        override fun isIdle() = state == IDLE
        override fun isListening() = state == LISTENING
        override fun isSpeaking() = state == SPEAKING
        override fun hasHeardSpeech() = heardSpeech
        override fun closePanel() = host.closePanel()
        override fun closeInput() = views.leaveInput()
        override fun glanceAway() {
            speech.cancel()
            showIdle(DuinchBrain.agentGreeting(activity))
        }

        override fun stopSpeaking() {
            voiceReply?.stop()
            setState(IDLE)
        }

        override fun finishListening() = finishHearing()
    }

    private fun finishHearing() {
        if (heardSpeech) {
            speech.stopListening()
            return
        }
        speech.cancel()
        showIdle(activity.getString(R.string.agent_idle_silence))
    }

    companion object {
        private const val IDLE = 0
        private const val LISTENING = 1
        private const val THINKING = 2
        private const val SPEAKING = 3
        private const val MATCH = LayoutParams.MATCH_PARENT
    }
}
