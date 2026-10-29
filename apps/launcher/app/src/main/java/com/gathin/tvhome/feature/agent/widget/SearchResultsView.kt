package com.gathin.tvhome.feature.agent.widget

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.core.view.AgentCardData
import com.gathin.tvhome.feature.agent.data.FlowCards

class SearchResultsView(context: Context) : LinearLayout(context) {
    private val tabs = LinearLayout(context)
    private val sections = LinkedHashMap<String, LinearLayout>()
    private val rows = LinkedHashMap<String, Pair<AgentCardData, View>>()
    private val headings = LinkedHashMap<String, TextView>()
    private val buttons = LinkedHashMap<String, TextView>()
    private var cards = emptyList<AgentCardData>()
    private var selected = "All"
    private val reconcile = Runnable { submit(cards) }
    private val focusChanged = android.view.ViewTreeObserver.OnGlobalFocusChangeListener { _, _ ->
        removeCallbacks(reconcile)
        post(reconcile)
    }

    init {
        orientation = VERTICAL
        addView(tabs)
        val sources = TextView(context).apply {
            setText(com.gathin.tvhome.R.string.search_sources)
            setTextColor(Palette.currentOrBaseline().primary)
            textSize = 15f
            setPadding(0, 12, 0, 12)
            isFocusable = true
            isClickable = true
            setOnClickListener { chooseSources() }
        }
        addView(sources)
        for (label in listOf("All", "Movie", "Series", "Other")) {
            val button = TextView(context).apply {
                text = label
                textSize = 16f
                isFocusable = true
                isClickable = true
                setPadding(20, 16, 20, 16)
                setOnClickListener { selected = label; filter() }
                setOnFocusChangeListener { _, focused ->
                    setBackgroundColor(if (focused) 0x444488CC else 0x00000000)
                }
            }
            buttons[label] = button
            tabs.addView(button, LayoutParams(0, -2, 1f))
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalFocusChangeListener(focusChanged)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnGlobalFocusChangeListener(focusChanged)
        removeCallbacks(reconcile)
        super.onDetachedFromWindow()
    }

    fun submit(items: List<AgentCardData>) {
        cards = items
        val keys = HashSet<String>()
        for (card in cards) {
            val source = card.sourceId.ifBlank { "Results" }
            com.gathin.tvhome.feature.playback.PlaybackStore(context).remember(source)
            val section = sections.getOrPut(source) {
                LinearLayout(context).apply {
                    orientation = VERTICAL
                    val heading = TextView(context).apply { text = source; textSize = 16f }
                    heading.setPadding(0, 24, 0, 12)
                    heading.setTextColor(Palette.currentOrBaseline().onSurface)
                    headings[source] = heading
                    addView(heading)
                    this@SearchResultsView.addView(this)
                }
            }
            val key = source + "|" + card.id.ifBlank { card.link.ifBlank { card.title } }
            keys.add(key)
            val earlier = rows[key]
            if (earlier?.first == card || earlier?.second?.hasFocus() == true) continue
            val row = if (card.type == "status") TextView(context).apply {
                text = card.title
                setTextColor(Palette.currentOrBaseline().onSurfaceVariant)
                setPadding(12, 12, 12, 12)
            } else AgentCardView(context, card)
            row.tag = key
            if (earlier != null) {
                val index = section.indexOfChild(earlier.second)
                section.removeView(earlier.second)
                section.addView(row, index)
            } else section.addView(row)
            rows[key] = card to row
        }
        for (key in rows.keys.toList()) {
            val row = rows.getValue(key).second
            if (key !in keys && !row.hasFocus()) {
                (row.parent as? LinearLayout)?.removeView(row)
                rows.remove(key)
            }
        }
        filter()
    }

    private fun filter() {
        val palette = Palette.currentOrBaseline()
        buttons.forEach { (label, button) ->
            button.setTextColor(if (label == selected) palette.primary else palette.onSurface)
            button.isSelected = label == selected
        }
        for ((_, entry) in rows) {
            val card = entry.first
            entry.second.visibility = if (selected == "All" || card.type == "status" ||
                FlowCards.contentType(card.type) == selected) VISIBLE else GONE
        }
        sections.forEach { (_, section) ->
            val visible = (1 until section.childCount).any {
                section.getChildAt(it).visibility == VISIBLE
            }
            section.visibility = if (visible) VISIBLE else GONE
        }
        visibility = if (cards.isEmpty()) GONE else VISIBLE
    }

    private fun chooseSources() {
        val selection = com.gathin.tvhome.feature.agent.data.SearchSelection(context)
        val sources = sections.keys.filter { it != "Results" }.sorted()
        val selected = selection.sources().toMutableSet()
        val labels = sources.toTypedArray()
        android.app.AlertDialog.Builder(context)
            .setTitle(com.gathin.tvhome.R.string.search_sources)
            .setMultiChoiceItems(labels, sources.map { it in selected }.toBooleanArray()) {
                _, index, checked ->
                if (checked) selected.add(sources[index]) else selected.remove(sources[index])
            }.setPositiveButton(android.R.string.ok) { _, _ -> selection.select(selected) }
            .setNeutralButton(com.gathin.tvhome.R.string.search_sources_auto) { _, _ ->
                selection.select(emptySet())
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    fun clear() {
        sections.values.forEach { removeView(it) }
        sections.clear()
        rows.clear()
        headings.clear()
        selected = "All"
        cards = emptyList()
        filter()
    }
}
