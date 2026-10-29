package com.gathin.tvhome.feature.agent.coordinator

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.gathin.tvhome.core.design.Fonts
import com.gathin.tvhome.core.design.Palette

class MediaActionView(context: Context, label: String, primary: Boolean = false) :
    TextView(context) {
    init {
        val palette = Palette.currentOrBaseline()
        val density = resources.displayMetrics.density
        val states = arrayOf(intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_focused), intArrayOf(android.R.attr.state_selected),
            intArrayOf())
        val normal = if (primary) Color.WHITE else palette.surfaceContainerHigh
        val foreground = if (primary) Color.rgb(16, 19, 26) else palette.onSurface
        text = label
        textSize = 14f
        typeface = Fonts.MEDIUM
        gravity = Gravity.CENTER
        minHeight = (38 * density).toInt()
        minWidth = (38 * density).toInt()
        setPadding((18 * density).toInt(), (8 * density).toInt(),
            (18 * density).toInt(), (8 * density).toInt())
        isFocusable = true
        isClickable = true
        setTextColor(ColorStateList(states, intArrayOf(palette.onSurfaceVariant,
            foreground, palette.primary, foreground)))
        background = StateListDrawable().apply {
            addState(states[0], shape(Palette.alpha(normal, 60), Color.TRANSPARENT, density))
            addState(states[1], shape(normal, palette.primary, density))
            addState(states[2], shape(Palette.alpha(palette.primary, 32), palette.primary, density))
            addState(states[3], shape(normal, Color.TRANSPARENT, density))
        }
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
            marginEnd = (8 * density).toInt()
        }
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    private fun shape(fill: Int, stroke: Int, density: Float) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = 20 * density
        setStroke((2 * density).toInt(), stroke)
    }
}
