package com.gathin.tvhome.feature.agent.coordinator

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.gathin.tvhome.core.design.Fonts
import com.gathin.tvhome.core.design.Palette

class MediaActionView(context: Context, label: String, private val primary: Boolean = false) :
    TextView(context) {

    init {
        val density = resources.displayMetrics.density
        text = label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Fonts.MEDIUM
        gravity = Gravity.CENTER
        minHeight = (38 * density).toInt()
        minWidth = (38 * density).toInt()
        val padH = (18 * density).toInt()
        val padV = (8 * density).toInt()
        setPadding(padH, padV, padH, padV)
        isFocusable = true
        isClickable = true

        paint(false)
        setOnFocusChangeListener { _, focused ->
            paint(focused)
            animate().scaleX(if (focused) 1.05f else 1f)
                .scaleY(if (focused) 1.05f else 1f).setDuration(150).start()
        }

        layoutParams = LinearLayout.LayoutParams(-2, -2)
    }

    override fun setSelected(selected: Boolean) {
        super.setSelected(selected)
        paint(hasFocus())
    }

    private fun paint(focused: Boolean) {
        val density = resources.displayMetrics.density
        val palette = Palette.currentOrBaseline()
        val accent = palette.primary
        val normalBg = when {
            isSelected -> palette.primaryContainer
            primary -> Color.WHITE
            else -> 0x22FFFFFF.toInt()
        }
        val focusedBg = if (primary) Color.WHITE else 0xFF0284C7.toInt()
        val normalText = when {
            isSelected -> palette.onPrimaryContainer
            primary -> Color.BLACK
            else -> Color.WHITE
        }
        val focusedText = if (primary) Color.BLACK else Color.WHITE

        setTextColor(if (focused) focusedText else normalText)
        background = GradientDrawable().apply {
            cornerRadius = 12 * density
            setColor(if (focused) focusedBg else normalBg)
            setStroke((2 * density).toInt(), if (focused || isSelected) accent else 0x33FFFFFF)
        }
    }
}
