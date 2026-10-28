package com.gathin.tvhome.feature.main.coordinator

import android.app.Activity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.main.widget.TypingGreetingView
import com.gathin.tvhome.feature.notification.data.TvNotification
import com.gathin.tvhome.feature.agent.ui.DuinchAiGreeting
import com.gathin.tvhome.feature.agent.widget.DuinchView


class HomeAgentCoordinator(
    private val activity: Activity,
    private val onBotClick: () -> Unit
) {
    private var duinchBot: DuinchView? = null
    private var greetingView: TypingGreetingView? = null
    private var botActionHint: TextView? = null

    fun setup(root: View) {
        duinchBot = root.findViewById(R.id.duinchBot)
        greetingView = root.findViewById(R.id.greeting)
        botActionHint = root.findViewById(R.id.botActionHint)

        val bot = duinchBot ?: return
        val greeting = greetingView
        greeting?.maxWidth = greetingMaxWidth()

        greeting?.setBot(bot)
        bot.setSleepListener { isSleeping ->
            if (isSleeping) {
                greeting?.showMessage(
                    activity.getString(R.string.duinch_sleeping),
                    animate = false,
                    simulateThinking = false
                )
            }
        }

        val clickListener = View.OnClickListener {
            bot.pulsePlayful(DuinchView.EXCITED, 1200)
            onBotClick()
        }
        bot.setOnClickListener(clickListener)
        greeting?.setOnClickListener(clickListener)
        botActionHint?.setOnClickListener(clickListener)

        bot.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                val thought = DuinchAiGreeting.interactiveThought("", 0)
                greeting?.showMessage(thought)
            }
        }

        bot.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                val topButtons = root.findViewById<ViewGroup>(R.id.topButtons)
                if (topButtons != null && topButtons.isShown) {
                    for (i in 0 until topButtons.childCount) {
                        val child = topButtons.getChildAt(i)
                        if (child != null && child.isShown && child.isFocusable) {
                            if (child.requestFocus()) return@setOnKeyListener true
                        }
                    }
                }
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                val tileTemp = root.findViewById<View>(R.id.tileTemp)
                if (tileTemp != null && tileTemp.isShown && tileTemp.isFocusable) {
                    if (tileTemp.requestFocus()) return@setOnKeyListener true
                }
                val widgetClock = root.findViewById<View>(R.id.widgetClock)
                if (widgetClock != null && widgetClock.isShown && widgetClock.isFocusable) {
                    if (widgetClock.requestFocus()) return@setOnKeyListener true
                }
            }
            false
        }
    }

    fun showActionableNotification(notification: TvNotification?) {
        if (notification == null) return
        val text = notification.message.ifEmpty { notification.title }
        if (text.isNotEmpty()) {
            greetingView?.showMessage(text, animate = false, simulateThinking = false)
        }
    }

    fun showPushedGreeting(text: String, mood: Int) {
        duinchBot?.pulsePlayful(mood, PUSH_MOOD_MS)
        updateGreetingText(text)
    }

    fun updateGreetingText(text: String) {
        greetingView?.showMessage(text, animate = true, simulateThinking = false)
    }

    private fun greetingMaxWidth(): Int {
        val screenWidth = activity.resources.displayMetrics.widthPixels
        return (screenWidth * GREETING_WIDTH_RATIO).toInt()
    }

    companion object {
        private const val GREETING_WIDTH_RATIO = 0.42f
        private const val PUSH_MOOD_MS = 2000L
    }
}
