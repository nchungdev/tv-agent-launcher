package com.gathin.tvhome.feature.main.widget

import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.gathin.tvhome.core.design.Fonts
import com.gathin.tvhome.core.design.Palette

class DockTooltip(private val parent: FrameLayout) {
    private val density = parent.resources.displayMetrics.density
    private val background = GradientDrawable().apply { cornerRadius = dp(14).toFloat() }
    private val label = TextView(parent.context).apply {
        background = this@DockTooltip.background
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = Fonts.MEDIUM
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setPadding(dp(12), dp(5), dp(12), dp(5))
        alpha = 0f
    }

    init {
        parent.addView(label, FrameLayout.LayoutParams(-2, -2))
    }

    fun setPalette(palette: Palette) {
        background.setColor(Palette.alpha(palette.inverseSurface, 0xF2))
        label.setTextColor(palette.inverseOnSurface)
    }

    fun show(slot: View, title: String, animations: Boolean) {
        label.text = title
        label.measure(View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.AT_MOST), 0)
        val bounds = Rect()
        slot.getDrawingRect(bounds)
        parent.offsetDescendantRectToMyCoords(slot, bounds)
        val maxLeft = (parent.width - label.measuredWidth).coerceAtLeast(0).toFloat()
        val left = bounds.exactCenterX() - label.measuredWidth / 2f
        label.translationX = left.coerceIn(0f, maxLeft)
        fade(1f, animations)
    }

    fun hide(animations: Boolean) = fade(0f, animations)

    private fun fade(alpha: Float, animations: Boolean) {
        label.animate().cancel()
        if (animations) label.animate().alpha(alpha).setDuration(140).start()
        else label.alpha = alpha
    }

    private fun dp(value: Int): Int = Math.round(value * density)
}
