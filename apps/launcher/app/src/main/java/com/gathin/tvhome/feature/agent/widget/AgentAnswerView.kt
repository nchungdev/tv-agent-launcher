package com.gathin.tvhome.feature.agent.widget
import com.gathin.tvhome.feature.agent.data.*
import com.gathin.tvhome.feature.agent.coordinator.*
import com.gathin.tvhome.feature.agent.ui.*

import com.gathin.tvhome.core.view.AgentCardData
import com.gathin.tvhome.core.view.NativeMarkdown
import android.content.Context
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView


class AgentAnswerView(context: Context) : FrameLayout(context) {

    private val scroll: ScrollView
    private val contentContainer: LinearLayout
    private val plainText: TextView
    private val cardsContainer: LinearLayout
    private val searchResults: SearchResultsView
    private val spinner = ProgressBar(context).apply { visibility = GONE }
    private var resultsShown = false
    val hasResults: Boolean get() = resultsShown
    var onResultsShown: (Boolean) -> Unit = {}
    var onLeaveResults: () -> Unit
        get() = searchResults.onLeaveTop
        set(value) { searchResults.onLeaveTop = value }
    private var destroyed = false
    private var structuredCards: List<AgentCardData> = emptyList()
    private var lastText = ""

    init {
        val density = context.resources.displayMetrics.density
        val padHorizontal = (56 * density).toInt()
        val padVertical = (16 * density).toInt()

        contentContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipToPadding = false
            clipChildren = false
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

        searchResults = SearchResultsView(context).apply { visibility = GONE }
        contentContainer.addView(searchResults, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (12 * density).toInt() })

        scroll = ScrollView(context).apply {
            isFillViewport = true
            addView(contentContainer)
        }
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        spinner.indeterminateTintList = android.content.res.ColorStateList.valueOf(
            com.gathin.tvhome.core.design.Palette.dark(
                com.gathin.tvhome.core.design.Palette.currentOrBaseline()
            ).primary
        )
        val spinnerSize = (48 * density).toInt()
        addView(spinner, LayoutParams(spinnerSize, spinnerSize, android.view.Gravity.CENTER))
    }

    fun setCards(cards: List<AgentCardData>) {
        if (destroyed) return
        structuredCards = cards.take(24)
        render()
    }

    fun setSearch(tabs: List<SearchTab>) {
        if (destroyed) return
        searchResults.visibility = if (tabs.isEmpty()) GONE else VISIBLE
        searchResults.setTabs(tabs)
        val shown = tabs.any { it.cards.isNotEmpty() }
        if (shown) spinner.visibility = GONE
        if (shown == resultsShown) return
        resultsShown = shown
        onResultsShown(shown)
        if (shown) searchResults.focusSelected()
    }

    fun setText(text: String) {
        if (destroyed) return
        spinner.visibility = GONE
        lastText = text.take(32_768)
        render()
    }

    fun clear() {
        if (destroyed) return
        lastText = ""
        structuredCards = emptyList()
        spinner.visibility = GONE
        setSearch(emptyList())
        render()
    }

    private fun render() {
        cardsContainer.removeAllViews()
        plainText.visibility = if (lastText.isBlank()) GONE else VISIBLE
        cardsContainer.visibility = if (structuredCards.isEmpty()) GONE else VISIBLE
        if (lastText.isBlank() && structuredCards.isEmpty()) {
            plainText.text = ""
            return
        }

        val result = NativeMarkdown.parse(lastText)
        plainText.text = result.formattedText
        if (result.cards.isNotEmpty()) cardsContainer.visibility = VISIBLE

        val density = context.resources.displayMetrics.density
        for (card in (result.cards + structuredCards).take(24)) {
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

    fun showLoading(@Suppress("UNUSED_PARAMETER") message: String) {
        if (destroyed) return
        lastText = ""
        render()
        spinner.visibility = VISIBLE
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
