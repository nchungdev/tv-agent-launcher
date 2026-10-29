package com.gathin.tvhome.feature.player

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.gathin.tvhome.R
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.feature.agent.data.CatalogEpisode

class EpisodePicker(
    context: Context,
    private val request: PlayRequest,
    private val onPick: (CatalogEpisode) -> Unit
) : LinearLayout(context) {

    private val density = context.resources.displayMetrics.density
    private val primary = Palette.dark(Palette.currentOrBaseline()).primary
    private val servers = LinearLayout(context)
    private val grid = GridLayout(context)
    private var server = request.current?.group ?: request.servers.firstOrNull().orEmpty()

    init {
        orientation = VERTICAL
        addView(label(context.getString(R.string.player_servers)))
        servers.clipChildren = false
        servers.setPadding(dp(4), dp(6), dp(4), dp(6))
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            addView(servers)
        })
        if (request.series) {
            addView(label(context.getString(R.string.player_episodes)).apply {
                (layoutParams as? LayoutParams)?.topMargin = dp(12)
            })
            grid.columnCount = COLUMNS
            grid.clipChildren = false
            grid.setPadding(dp(4), dp(6), dp(4), dp(6))
            addView(ScrollView(context).apply {
                clipChildren = false
                addView(grid)
            }, LayoutParams(LayoutParams.MATCH_PARENT, dp(GRID_HEIGHT)))
        }
        renderServers()
        renderEpisodes()
    }

    fun focusCurrent() {
        val target = if (request.series) currentEpisodeView() else currentServerView()
        (target ?: servers.getChildAt(0))?.requestFocus()
    }

    private fun renderServers() {
        servers.removeAllViews()
        for (name in request.servers) {
            servers.addView(chip(name.ifBlank { "Server" }, name == server) {
                if (request.series) {
                    server = name
                    renderServers()
                    renderEpisodes()
                    currentServerView()?.requestFocus()
                } else request.episodesOn(name).firstOrNull()?.let(onPick)
            }.apply { tag = name })
        }
    }

    private fun renderEpisodes() {
        if (!request.series) return
        grid.removeAllViews()
        for (episode in request.episodesOn(server)) {
            val current = episode.id == request.episodeId
            grid.addView(chip(episode.label.ifBlank { "${episode.order}" }, current) {
                onPick(episode)
            }.apply { tag = episode.id })
        }
    }

    private fun currentServerView(): View? =
        (0 until servers.childCount).map { servers.getChildAt(it) }.firstOrNull { it.tag == server }

    private fun currentEpisodeView(): View? =
        (0 until grid.childCount).map { grid.getChildAt(it) }
            .firstOrNull { it.tag == request.episodeId } ?: grid.getChildAt(0)

    private fun label(text: String) = TextView(context).apply {
        this.text = text
        setTextColor(0xB3FFFFFF.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    }

    private fun chip(text: String, selected: Boolean, onClick: () -> Unit) = TextView(context).apply {
        this.text = text
        gravity = Gravity.CENTER
        minWidth = dp(64)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setPadding(dp(16), dp(9), dp(16), dp(9))
        isFocusable = true
        isClickable = true
        paint(this, selected, false)
        setOnFocusChangeListener { view, focused -> paint(view as TextView, selected, focused) }
        setOnClickListener { onClick() }
        layoutParams = MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            .apply { setMargins(0, 0, dp(10), dp(10)) }
    }

    private fun paint(view: TextView, selected: Boolean, focused: Boolean) {
        view.background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            when {
                focused -> setColor(FOCUSED)
                selected -> {
                    setColor(Palette.alpha(primary, 0x33))
                    setStroke(dp(1), Palette.alpha(primary, 0x99))
                }
                else -> setColor(RESTING)
            }
        }
        view.setTextColor(when {
            focused -> 0xFF1C1B1F.toInt()
            selected -> primary
            else -> 0xFFFFFFFF.toInt()
        })
    }

    private fun dp(value: Int): Int = Math.round(value * density)

    private companion object {
        const val COLUMNS = 6
        const val GRID_HEIGHT = 220
        const val RESTING = 0xFF262B33.toInt()
        const val FOCUSED = 0xFFE6E1EC.toInt()
    }
}
