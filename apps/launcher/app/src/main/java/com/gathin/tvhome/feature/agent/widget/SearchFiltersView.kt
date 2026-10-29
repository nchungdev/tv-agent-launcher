package com.gathin.tvhome.feature.agent.widget

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.KeyEvent
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.feature.agent.data.FlowCards
import com.gathin.tvhome.feature.agent.data.SearchTab
import com.gathin.tvhome.feature.playback.PlaybackStore

class SearchFiltersView(context: Context, private val changed: () -> Unit) : LinearLayout(context) {
    private val buttons = LinkedHashMap<String, TextView>()
    private val row = LinearLayout(context).apply { orientation = HORIZONTAL }
    private var selected = "All"
    private var sources = emptyList<String>()
    var onMoveDown: () -> Unit = {}
    var onLeaveTop: () -> Unit = {}

    init {
        orientation = VERTICAL
        isFocusable = false
        val scroller = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            addView(row)
        }
        addView(scroller, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun update(tabs: List<SearchTab>) {
        sources = tabs.map { it.source }.filter(String::isNotBlank).distinct().sorted()
        val store = PlaybackStore(context)
        sources.forEach(store::remember)

        val density = resources.displayMetrics.density
        val cards = tabs.flatMap { it.cards }.filter { it.type != "status" }
        val counts = cards.groupingBy { FlowCards.contentType(it.type) }.eachCount()
        val activeFocus = buttons.entries.firstOrNull { it.value.hasFocus() }?.key
        buttons.clear()
        row.removeAllViews()
        val types = listOf("All", "Movie", "Series", "Other").filter {
            it == "All" || counts.getOrDefault(it, 0) > 0
        }
        for (type in types) {
            val label = "${typeLabel(type)} (${if (type == "All") cards.size else counts[type] ?: 0})"
            val button = control(label) {
                selected = type
                paint()
                changed()
            }.apply {
                tag = "search-filter:$type"
                setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN &&
                        event.action == KeyEvent.ACTION_DOWN
                    ) {
                        onMoveDown()
                        true
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP &&
                        event.action == KeyEvent.ACTION_DOWN
                    ) {
                        onLeaveTop()
                        true
                    } else {
                        false
                    }
                }
            }
            buttons[type] = button
            val params = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginEnd = (12 * density).toInt()
            }
            row.addView(button, params)
        }
        if (selected != "All" && selected !in types) selected = "All"
        paint()
        activeFocus?.let { buttons[it]?.requestFocus() }
    }

    fun matches(kind: String): Boolean =
        selected == "All" || FlowCards.contentType(kind) == selected

    fun focusSelected() {
        buttons[selected]?.requestFocus() ?: buttons["All"]?.requestFocus()
    }

    private fun typeLabel(type: String): String = when (type) {
        "All" -> "All"
        "Movie" -> "Movies"
        "Series" -> "Series"
        else -> "Other"
    }

    private fun paint() {
        val palette = Palette.dark(Palette.currentOrBaseline())
        buttons.forEach { (type, button) ->
            val active = selected == type
            button.setTextColor(if (active) palette.primary else palette.onSurface)
            button.background = GradientDrawable().apply {
                cornerRadius = 12 * resources.displayMetrics.density
                setColor(if (active) 0x443F79CC else 0x22FFFFFF)
                setStroke(
                    (resources.displayMetrics.density).toInt().coerceAtLeast(1),
                    if (active) palette.primary else 0x337F8A9B
                )
            }
        }
    }

    private fun control(label: String, action: () -> Unit) = TextView(context).apply {
        val density = resources.displayMetrics.density
        text = label
        textSize = 14f
        setTextColor(Palette.currentOrBaseline().primary)
        setPadding((14 * density).toInt(), (10 * density).toInt(),
            (14 * density).toInt(), (10 * density).toInt())
        isFocusable = true
        isClickable = true
        setOnClickListener { action() }
        setOnFocusChangeListener { _, focused ->
            if (tag?.toString()?.startsWith("search-filter:") != true) {
                background = GradientDrawable().apply {
                    cornerRadius = 12 * density
                    setColor(if (focused) 0x44516888 else 0x00000000)
                }
            }
        }
    }
}
