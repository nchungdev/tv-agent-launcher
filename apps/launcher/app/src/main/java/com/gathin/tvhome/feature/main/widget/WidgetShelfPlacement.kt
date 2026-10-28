package com.gathin.tvhome.feature.main.widget

import android.view.View
import android.view.ViewGroup
import android.util.TypedValue
import android.widget.TextView
import com.gathin.tvhome.R

object WidgetShelfPlacement {
    private const val LOWER_DP = 12

    data class Bounds(val top: Int, val height: Int)

    fun fit(headerBottom: Int, dockTop: Int, naturalHeight: Int, gap: Int): Bounds {
        val start = headerBottom + gap
        val available = (dockTop - gap - start).coerceAtLeast(0)
        val height = minOf(naturalHeight.coerceAtLeast(0), available)
        val top = start + (available - height) / 2

        return Bounds(top, height)
    }

    fun apply(
        shelf: View, headerBottom: Int, dockTop: Int, density: Float, expanded: Boolean = false
    ) {
        val layout = shelf.layoutParams as ViewGroup.MarginLayoutParams
        val desiredHeight = (if (expanded) 260 else 180) * density
        val gap = (12 * density).toInt()
        val centered = fit(headerBottom, dockTop, desiredHeight.toInt(), gap)
        val lowered = minOf(centered.top + (LOWER_DP * density).toInt(),
            dockTop - gap - centered.height)
        val bounds = centered.copy(top = maxOf(centered.top, lowered))
        shelf.scaleX = 1f
        shelf.scaleY = 1f
        if (layout.topMargin != bounds.top || layout.height != bounds.height) {
            layout.topMargin = bounds.top
            layout.height = bounds.height
            shelf.layoutParams = layout
        }
        val parent = shelf as ViewGroup
        for (index in 0 until parent.childCount) {
            val card = parent.getChildAt(index)
            if (card !is WidgetCard) continue
            val params = card.layoutParams
            val height = (bounds.height - 8 * density).toInt().coerceAtLeast(0)
            if (params.height != height) {
                params.height = height
                card.layoutParams = params
            }
        }

        val contentHeight = (bounds.height - 16 * density).coerceAtLeast(0f)
        val clockRow = shelf.findViewById<ClockTimeRow>(R.id.clockTimeRow)
        val rowHeight = if (expanded) minOf(90 * density, contentHeight * 0.40f).toInt()
        else (contentHeight * 0.58f).toInt()
        clockRow.setNominalHeight(rowHeight)
        shelf.findViewById<TextView>(R.id.clockDay).setTextSize(
            TypedValue.COMPLEX_UNIT_PX, (contentHeight * 0.14f).coerceAtMost(22 * density)
        )
        shelf.findViewById<TextView>(R.id.clockLunar).setTextSize(
            TypedValue.COMPLEX_UNIT_PX, (contentHeight * 0.11f).coerceAtMost(16 * density)
        )
    }
}
