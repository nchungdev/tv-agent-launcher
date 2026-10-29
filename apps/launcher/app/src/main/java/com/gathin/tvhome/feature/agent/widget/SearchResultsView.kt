package com.gathin.tvhome.feature.agent.widget

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.agent.data.SearchTab

class SearchResultsView(context: Context) : LinearLayout(context) {

    private val density = context.resources.displayMetrics.density
    private val chips = LinearLayout(context)
    private val posters = LinearLayout(context)
    private val status = TextView(context)
    private var tabs: List<SearchTab> = emptyList()
    private var selected = ""
    private var picked = false
    private var focusingFirst = false
    var onLeaveTop: () -> Unit = {}

    init {
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false
        addView(scroller(chips), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        posters.clipChildren = false
        posters.clipToPadding = false
        posters.setPadding(dp(FOCUS_ROOM), dp(FOCUS_ROOM), dp(FOCUS_ROOM), dp(FOCUS_ROOM))
        addView(scroller(posters).apply { clipChildren = false },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(TABS_GAP)
                marginStart = -dp(FOCUS_ROOM)
                marginEnd = -dp(FOCUS_ROOM)
            })
        status.setTextColor(0xB3FFFFFF.toInt())
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        addView(status, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        })
    }

    fun setTabs(next: List<SearchTab>) {
        tabs = next
        val current = tabs.firstOrNull { it.source == selected }
        if (current == null || (!picked && current.cards.isEmpty())) selected = firstReady()
        if (tabs.isEmpty()) picked = false
        val focusedIndex = (0 until chips.childCount).firstOrNull { chips.getChildAt(it).hasFocus() }
        renderChips()
        focusedIndex?.let { chips.getChildAt(it.coerceAtMost(chips.childCount - 1))?.requestFocus() }
        renderPosters()
    }

    fun focusSelected() {
        val index = tabs.indexOfFirst { it.source == selected }.coerceAtLeast(0)
        focusingFirst = true
        chips.getChildAt(index)?.requestFocus()
        focusingFirst = false
    }

    private fun firstReady(): String =
        (tabs.firstOrNull { it.state == SearchTab.State.READY } ?: tabs.firstOrNull())?.source.orEmpty()

    private fun renderChips() {
        chips.removeAllViews()
        for (tab in tabs) {
            chips.addView(chip(tab), LayoutParams(LayoutParams.WRAP_CONTENT, dp(40)).apply {
                marginEnd = dp(10)
            })
        }
    }

    private fun renderPosters() {
        posters.removeAllViews()
        val tab = tabs.firstOrNull { it.source == selected }
        if (tab == null || tab.cards.isEmpty()) {
            status.visibility = if (tab == null) View.GONE else View.VISIBLE
            status.setText(if (tab?.state == SearchTab.State.LOADING) R.string.agent_search_loading
                else R.string.agent_search_empty_tab)
            return
        }
        status.visibility = View.GONE
        for (card in tab.cards) {
            posters.addView(SearchPosterCard(context, card), LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(18) })
        }
    }

    private fun chip(tab: SearchTab): TextView = TextView(context).apply {
        val count = when (tab.state) {
            SearchTab.State.LOADING -> "…"
            else -> tab.cards.size.toString()
        }
        text = "${label(tab.source)} ($count)"
        gravity = Gravity.CENTER
        setPadding(dp(18), 0, dp(18), 0)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        isFocusable = true
        isClickable = true
        paint(this, tab.source == selected, false)
        setOnKeyListener { _, keyCode, event ->
            val up = keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP
            if (up && event.action == android.view.KeyEvent.ACTION_DOWN) onLeaveTop()
            up
        }
        setOnFocusChangeListener { view, focused ->
            if (focused && selected != tab.source) {
                selected = tab.source
                if (!focusingFirst) picked = true
                for (index in 0 until chips.childCount) {
                    val other = chips.getChildAt(index) as TextView
                    paint(other, tabs.getOrNull(index)?.source == selected, other === view)
                }
                renderPosters()
            } else paint(this, tab.source == selected, focused)
        }
    }

    private fun paint(chip: TextView, active: Boolean, focused: Boolean) {
        chip.background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            when {
                focused -> setColor(FOCUSED)
                active -> setColor(SELECTED)
                else -> setColor(RESTING)
            }
        }
        chip.setTextColor(if (focused) 0xFF1C1B1F.toInt() else 0xFFFFFFFF.toInt())
    }

    private fun label(source: String): String = LABELS[source]
        ?: source.replaceFirstChar { it.uppercaseChar() }

    private fun scroller(content: View) = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        isFillViewport = false
        addView(content)
    }

    private fun dp(value: Int): Int = Math.round(value * density)

    private companion object {
        const val FOCUS_ROOM = 16
        const val TABS_GAP = 12
        const val RESTING = 0xFF262B33.toInt()
        const val FOCUSED = 0xFFE6E1EC.toInt()
        const val SELECTED = 0xFFD32F2F.toInt()
        val LABELS = mapOf(
            "plex" to "Plex", "jellyfin" to "Jellyfin", "phimapi" to "PhimAPI",
            "radarr" to "Radarr", "sonarr" to "Sonarr", "tmdb" to "TMDb", "internet" to "Internet"
        )
    }
}
