package com.gathin.tvhome.feature.main.coordinator

import android.app.Activity
import android.view.View
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
    }

    fun showActionableNotification(notification: TvNotification?) {
        if (notification == null) return
        val text = notification.message.ifEmpty { notification.title }
        if (text.isNotEmpty()) {
            greetingView?.showMessage(text, animate = false, simulateThinking = false)
        }
    }

    fun updateGreetingText(text: String) {
        greetingView?.showMessage(text, animate = true, simulateThinking = false)
    }
}
