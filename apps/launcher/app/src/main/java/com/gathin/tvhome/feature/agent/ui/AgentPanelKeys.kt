package com.gathin.tvhome.feature.agent.ui

import android.view.KeyEvent

class AgentPanelKeys(private val views: AgentPanelViews, private val target: Target) {

    interface Target {
        fun isIdle(): Boolean
        fun isListening(): Boolean
        fun isSpeaking(): Boolean
        fun hasHeardSpeech(): Boolean
        fun closePanel()
        fun closeInput()
        fun glanceAway()
        fun stopSpeaking()
        fun finishListening()
    }

    fun owns(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_BACK || isHomeKey(keyCode)
    }

    fun consume(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_UP) return true

        if (isHomeKey(event.keyCode)) {
            target.closePanel()
        } else if (views.isTyping() && target.isIdle()) {
            target.closeInput()
        } else {
            target.closePanel()
        }
        return true
    }

    fun look(keyCode: Int) {
        views.bot.wake()
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> glanceSideways(-1f)
            KeyEvent.KEYCODE_DPAD_RIGHT -> glanceSideways(1f)
            KeyEvent.KEYCODE_DPAD_UP -> views.bot.lookAt(0f, -0.8f)
            KeyEvent.KEYCODE_DPAD_DOWN -> views.bot.lookAt(0f, 0.8f)
            else -> views.bot.lookAt(0f, 0f)
        }
    }

    fun fallback(keyCode: Int): Boolean {
        val confirm = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
        if (confirm && target.isSpeaking()) {
            target.stopSpeaking()
            return true
        }
        if ((confirm || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) && target.isListening()) {
            target.finishListening()
            return true
        }
        return false
    }

    private fun glanceSideways(direction: Float) {
        views.bot.lookAt(direction, 0.2f)
        if (target.isListening() && !target.hasHeardSpeech()) target.glanceAway()
    }

    private fun isHomeKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_HOME || keyCode == KeyEvent.KEYCODE_ALL_APPS
    }
}
