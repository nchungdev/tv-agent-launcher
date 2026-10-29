package com.gathin.tvhome.feature.agent.widget

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.gathin.tvhome.R
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.core.view.AgentCardData
import com.gathin.tvhome.feature.agent.data.SearchTab

class SearchResultsView(context: Context) : LinearLayout(context) {

    private val density = context.resources.displayMetrics.density
    private val sections = LinearLayout(context)
    private val searchFilters = SearchFiltersView(context) { renderSections() }
    private var tabs: List<SearchTab> = emptyList()
    private var pendingTabs: List<SearchTab>? = null
    private var firstPoster: View? = null
    private val focusChanged = ViewTreeObserver.OnGlobalFocusChangeListener { _, _ ->
        if (!sections.hasFocus()) pendingTabs?.let { next ->
            pendingTabs = null
            post { setTabs(next) }
        }
    }
    var onLeaveTop: () -> Unit = {}

    init {
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false
        searchFilters.onMoveDown = { firstPoster?.requestFocus() }
        searchFilters.onLeaveTop = { onLeaveTop() }
        addView(searchFilters, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        sections.orientation = VERTICAL
        sections.clipChildren = false
        sections.clipToPadding = false
        addView(sections, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalFocusChangeListener(focusChanged)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnGlobalFocusChangeListener(focusChanged)
        super.onDetachedFromWindow()
    }

    fun setTabs(next: List<SearchTab>) {
        if (next.isNotEmpty() && sections.hasFocus()) {
            pendingTabs = next
            return
        }
        pendingTabs = null
        tabs = next
        searchFilters.update(tabs)
        renderSections()
    }

    fun focusSelected() {
        searchFilters.focusSelected()
    }

    private fun renderSections() {
        sections.removeAllViews()
        firstPoster = null
        for (tab in tabs) {
            val cards = tab.cards.filter {
                it.type != "status" && searchFilters.matches(it.type)
            }
            if (cards.isEmpty() && tab.state == SearchTab.State.EMPTY && tabs.size > 1) continue
            renderSection(tab, cards)
        }
    }

    private fun renderSection(tab: SearchTab, cards: List<AgentCardData>) {
        val section = LinearLayout(context).apply {
            orientation = VERTICAL
            clipChildren = false
            clipToPadding = false
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(SECTION_GAP) }
        }
        if (!tab.source.equals("tmdb", ignoreCase = true)) {
            section.addView(TextView(context).apply {
                text = label(tab.source)
                setTextColor(Palette.dark(Palette.currentOrBaseline()).onSurface)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, 0, dp(CARD_GAP), dp(8))
            })
        }

        if (cards.isEmpty()) {
            section.addView(TextView(context).apply {
                text = when (tab.state) {
                    SearchTab.State.LOADING -> context.getString(R.string.agent_search_loading)
                    else -> tab.detail.ifBlank {
                        context.getString(R.string.agent_search_empty_tab)
                    }
                }
                setTextColor(0xB3FFFFFF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, 0, dp(CARD_GAP), dp(8))
            })
        } else {
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                clipChildren = false
                clipToPadding = false
                setPadding(0, dp(FOCUS_ROOM), dp(FOCUS_ROOM), dp(FOCUS_ROOM))
            }
            for (card in cards) {
                val poster = SearchPosterCard(context, card)
                val identity = card.id.ifBlank { card.link.ifBlank { card.title } }
                poster.tag = "${card.sourceId}|$identity"
                poster.setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP &&
                        event.action == KeyEvent.ACTION_DOWN
                    ) {
                        focusSelected()
                        true
                    } else {
                        false
                    }
                }
                if (firstPoster == null) firstPoster = poster
                val posterParams = poster.layoutParams as LayoutParams
                posterParams.marginEnd = dp(CARD_GAP)
                row.addView(poster, posterParams)
            }
            val scroller = HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                isFillViewport = false
                clipChildren = false
                addView(row)
            }
            section.addView(scroller, LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
            ))
        }

        sections.addView(section)
    }

    private fun label(source: String): String = LABELS[source]
        ?: source.replaceFirstChar { it.uppercaseChar() }

    private fun dp(value: Int): Int = Math.round(value * density)

    private companion object {
        const val CARD_GAP = 22
        const val FOCUS_ROOM = 16
        const val SECTION_GAP = 10
        val LABELS = mapOf(
            "plex" to "Plex", "jellyfin" to "Jellyfin", "phimapi" to "PhimAPI",
            "radarr" to "Radarr", "sonarr" to "Sonarr", "tmdb" to "TMDb",
            "internet" to "Internet"
        )
    }
}
