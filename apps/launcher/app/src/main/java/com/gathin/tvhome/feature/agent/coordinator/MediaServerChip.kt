package com.gathin.tvhome.feature.agent.coordinator

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.gathin.tvhome.core.design.Fonts
import com.gathin.tvhome.core.design.Palette

class MediaServerChip(context: Context, label: String) : TextView(context) {
    private val density = resources.displayMetrics.density

    init {
        text = label
        textSize = 12f
        typeface = Fonts.MEDIUM
        gravity = Gravity.CENTER
        isFocusable = true
        isClickable = true
        minHeight = dp(30)
        setPadding(dp(12), dp(5), dp(12), dp(5))
        compoundDrawablePadding = dp(6)
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
            marginEnd = dp(8)
            bottomMargin = dp(6)
        }
        setOnFocusChangeListener { _, _ -> paint() }
        paint()
    }

    override fun setSelected(selected: Boolean) {
        super.setSelected(selected)
        paint()
    }

    private fun paint() {
        val palette = Palette.currentOrBaseline()
        val accent = if (isSelected || hasFocus()) palette.primary else palette.outlineVariant
        setTextColor(if (isSelected) palette.primary else palette.onSurfaceVariant)
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(if (hasFocus()) palette.surfaceContainerHighest else Color.TRANSPARENT)
            setStroke(dp(if (hasFocus()) 2 else 1), accent)
        }
        val indicator = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (isSelected) palette.primary else Color.TRANSPARENT)
            setStroke(dp(1), accent)
            setBounds(0, 0, dp(6), dp(6))
        }
        setCompoundDrawablesRelative(indicator, null, null, null)
    }

    private fun dp(value: Int) = (value * density).toInt()
}
