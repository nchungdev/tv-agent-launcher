package com.gathin.tvhome.feature.agent.widget
import com.gathin.tvhome.feature.agent.data.*
import com.gathin.tvhome.feature.agent.coordinator.*
import com.gathin.tvhome.feature.agent.ui.*

import com.gathin.tvhome.core.view.AgentCardData
import com.gathin.tvhome.core.view.NativeMarkdown
import android.content.Context
import android.graphics.Color
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView


class AgentAnswerView(context: Context) : FrameLayout(context) {

    private val scroll: ScrollView
    private val contentContainer: LinearLayout
    private val plainText: TextView
    private val cardsContainer: LinearLayout
    private var destroyed = false
    private var structuredCards: List<AgentCardData> = emptyList()
    private var lastText = ""

    init {
        setBackgroundColor(Color.rgb(8, 11, 16))
        val density = context.resources.displayMetrics.density
        val padHorizontal = (56 * density).toInt()
        val padVertical = (16 * density).toInt()

        contentContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padHorizontal, padVertical, padHorizontal, padVertical)
        }

        plainText = TextView(context).apply {
            setTextColor(0xFFE6E6E6.toInt())
            setLinkTextColor(0xFFD0BCFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            setLineSpacing(0f, 1.35f)
            movementMethod = LinkMovementMethod.getInstance()
        }
        contentContainer.addView(plainText)

        cardsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (16 * density).toInt()
            }
        }
        contentContainer.addView(cardsContainer)

        scroll = ScrollView(context).apply {
            isFillViewport = true
            addView(contentContainer)
        }
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun setCards(cards: List<AgentCardData>) {
        if (destroyed) return
        structuredCards = cards
        render()
    }

    fun setText(text: String) {
        if (destroyed) return
        lastText = text
        render()
    }

    fun clear() {
        if (destroyed) return
        lastText = ""
        structuredCards = emptyList()
        render()
    }

    private fun render() {
        cardsContainer.removeAllViews()
        if (lastText.isBlank() && structuredCards.isEmpty()) {
            plainText.text = ""
            return
        }

        val result = NativeMarkdown.parse(lastText)
        plainText.text = result.formattedText

        val density = context.resources.displayMetrics.density
        for (card in result.cards + structuredCards) {
            val cardView = AgentCardView(context, card).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (10 * density).toInt()
                }
            }
            cardsContainer.addView(cardView)
        }
    }

    fun showLoading(message: String) {
        setText(message)
    }

    fun onPause() {
        // Native views require no webview pause
    }

    fun onResume() {
        // Native views require no webview resume
    }

    fun destroy() {
        destroyed = true
        plainText.text = ""
        cardsContainer.removeAllViews()
    }
}
