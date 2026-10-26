package com.gathin.tvhome.feature.smarthome.widget
import com.gathin.tvhome.feature.smarthome.data.*
import com.gathin.tvhome.feature.smarthome.presenter.*
import com.gathin.tvhome.feature.smarthome.ui.*

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.gathin.tvhome.core.design.Palette


object HassControls {
    fun action(context: Context, label: String, listener: () -> Unit): TextView {
        val density = context.resources.displayMetrics.density
        return TextView(context).apply {
            text = label
            textSize = 18f
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            setPadding((20 * density).toInt(), 0, (20 * density).toInt(), 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (52 * density).toInt()
            ).apply { bottomMargin = (8 * density).toInt() }
            setOnClickListener { listener() }
            style(this)
        }
    }

    fun style(view: TextView) {
        val palette = Palette.currentOrBaseline()
        val radius = 18 * view.resources.displayMetrics.density
        val disabled = intArrayOf(-android.R.attr.state_enabled)
        val focused = intArrayOf(android.R.attr.state_focused)
        val pressed = intArrayOf(android.R.attr.state_pressed)
        fun shape(color: Int) = GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }
        view.background = StateListDrawable().apply {
            addState(disabled, shape(palette.surfaceContainer))
            addState(focused, shape(palette.primaryContainer))
            addState(pressed, shape(palette.primaryContainer))
            addState(intArrayOf(), shape(palette.surfaceContainerHighest))
        }
        view.setTextColor(ColorStateList(
            arrayOf(disabled, focused, pressed, intArrayOf()),
            intArrayOf(
                Palette.alpha(palette.onSurface, 0x66), palette.onPrimaryContainer,
                palette.onPrimaryContainer, palette.onSurface
            )
        ))
    }
}
