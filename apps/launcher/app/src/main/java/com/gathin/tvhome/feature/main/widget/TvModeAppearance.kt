package com.gathin.tvhome.feature.main.widget

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.gathin.tvhome.core.design.Palette

object TvModeAppearance {
    private const val GROUP_FILL = 0x47000000
    private const val GROUP_STROKE = 0x1FFFFFFF
    private const val SEGMENT_FILL = 0x12FFFFFF
    private const val FOCUSED_FILL = 0x2EFFFFFF
    private const val FOCUSED_STROKE = 0xB3FFFFFF.toInt()
    private const val IDLE = 0x99FFFFFF.toInt()
    private const val BRIGHT = 0xFFFFFFFF.toInt()
    private const val ICON_WIDTH = 40
    private const val ICON_HEIGHT = 24
    private const val TILE_PAD_MODE = 6
    private const val TILE_PAD_PLAIN = 8
    private const val ICON_PLAIN = 20

    fun apply(group: View, tiles: List<View>, captions: List<View>, active: Int, palette: Palette) {
        val density = group.resources.displayMetrics.density
        group.background = GradientDrawable().apply {
            cornerRadius = 18 * density
            setColor(GROUP_FILL)
            setStroke(density.toInt().coerceAtLeast(1), GROUP_STROKE)
        }
        val inset = (6 * density).toInt()
        group.setPadding(inset, inset, inset, inset)
        captions.forEach { it.visibility = View.GONE }

        tiles.forEachIndexed { index, tile ->
            tile.background = segment(density)
            padVertically(tile, TILE_PAD_MODE, density)
            paintContent(tile, index == active, palette, density)
        }
    }

    fun reset(group: View, tiles: List<View>, captions: List<View>, palette: Palette) {
        group.background = null
        group.setPadding(0, 0, 0, 0)
        captions.forEach { it.visibility = View.VISIBLE }
        val density = group.resources.displayMetrics.density
        for (tile in tiles) {
            val content = tile as? ViewGroup ?: continue
            for (index in 0 until content.childCount) {
                val text = content.getChildAt(index) as? TextView ?: continue
                text.isDuplicateParentStateEnabled = false
                text.compoundDrawableTintList = null
            }
            padVertically(tile, TILE_PAD_PLAIN, density)
            val icon = content.getChildAt(0) as? TextView ?: continue
            icon.background = null
            icon.setPadding(0, 0, 0, 0)
            resize(icon, ICON_PLAIN, ICON_PLAIN, density)
        }
        HomeTileAppearance.style(tiles, palette)
    }

    private fun paintContent(tile: View, active: Boolean, palette: Palette, density: Float) {
        val content = tile as? ViewGroup ?: return
        val colors = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(BRIGHT, if (active) BRIGHT else IDLE)
        )
        for (index in 0 until content.childCount) {
            val text = content.getChildAt(index) as? TextView ?: continue
            text.isDuplicateParentStateEnabled = true
            text.setTextColor(colors)
            text.compoundDrawableTintList = colors
        }
        val icon = content.getChildAt(0) as? TextView ?: return
        resize(icon, ICON_WIDTH, ICON_HEIGHT, density)
        val horizontal = ((ICON_WIDTH - ICON_PLAIN) / 2 * density).toInt()
        val vertical = ((ICON_HEIGHT - ICON_PLAIN) / 2 * density).toInt()
        icon.setPadding(horizontal, vertical, horizontal, vertical)
        icon.background = if (active) GradientDrawable().apply {
            cornerRadius = ICON_HEIGHT / 2f * density
            setColor(palette.primary)
        } else null
    }

    private fun padVertically(view: View, paddingDp: Int, density: Float) {
        val padding = (paddingDp * density).toInt()
        view.setPadding(view.paddingLeft, padding, view.paddingRight, padding)
    }

    private fun resize(view: View, widthDp: Int, heightDp: Int, density: Float) {
        val params = view.layoutParams as? LinearLayout.LayoutParams ?: return
        params.width = (widthDp * density).toInt()
        params.height = (heightDp * density).toInt()
        view.layoutParams = params
    }

    private fun segment(density: Float): StateListDrawable {
        val radius = 14 * density
        val focused = GradientDrawable().apply {
            cornerRadius = radius
            setColor(FOCUSED_FILL)
            setStroke((1.5f * density).toInt().coerceAtLeast(1), FOCUSED_STROKE)
        }
        val resting = GradientDrawable().apply {
            cornerRadius = radius
            setColor(SEGMENT_FILL)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(), resting)
        }
    }
}
