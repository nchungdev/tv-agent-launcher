package com.gathin.tvhome.feature.main.widget

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.widget.TextView
import com.gathin.tvhome.R
import com.gathin.tvhome.core.design.Palette

object HomeTileAppearance {
    fun style(tiles: List<View>, palette: Palette) {
        for (tile in tiles) {
            val density = tile.resources.displayMetrics.density
            val resting = GradientDrawable().apply {
                cornerRadius = 14 * density
                setColor(0x14FFFFFF)
                setStroke(density.toInt().coerceAtLeast(1), 0x24FFFFFF)
            }
            val focused = GradientDrawable().apply {
                cornerRadius = 14 * density
                setColor(Palette.alpha(palette.primary, 0x38))
                setStroke((2 * density).toInt(), palette.primary)
            }
            tile.background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), focused)
                addState(intArrayOf(), resting)
            }
        }
    }

    fun icon(view: TextView, symbol: String, color: Int) {
        val resource = when (symbol.replace("\uFE0F", "")) {
            "🌡" -> R.drawable.ic_thermostat
            "❄" -> R.drawable.ic_snowflake
            "💡" -> R.drawable.ic_light
            "🎬", "🎥", "🍿" -> R.drawable.ic_movie
            "🍚", "🍜", "🍽", "🍲" -> R.drawable.ic_restaurant
            else -> 0
        }
        if (resource == 0) {
            view.setCompoundDrawables(null, null, null, null)
            view.text = symbol
            return
        }
        val drawable = view.context.getDrawable(resource)?.mutate()
        val size = (20 * view.resources.displayMetrics.density).toInt()
        drawable?.setBounds(0, 0, size, size)
        drawable?.setTint(color)
        view.text = ""
        view.setCompoundDrawables(drawable, null, null, null)
    }
}
