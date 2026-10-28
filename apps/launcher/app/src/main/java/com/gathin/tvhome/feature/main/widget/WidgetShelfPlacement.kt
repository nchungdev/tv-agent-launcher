package com.gathin.tvhome.feature.main.widget

import android.view.View
import android.view.ViewGroup

object WidgetShelfPlacement {
    data class Bounds(val top: Int, val scale: Float)

    fun fit(headerBottom: Int, dockTop: Int, naturalHeight: Int, gap: Int): Bounds {
        val start = headerBottom + gap
        val available = (dockTop - gap - start).coerceAtLeast(0)
        val scale = if (naturalHeight > 0) minOf(1f, available.toFloat() / naturalHeight) else 0f
        val top = start + ((available - naturalHeight * scale) / 2).toInt()

        return Bounds(top, scale)
    }

    fun apply(shelf: View, headerBottom: Int, dockTop: Int, density: Float) {
        val layout = shelf.layoutParams as ViewGroup.MarginLayoutParams
        val bounds = fit(headerBottom, dockTop, layout.height, (12 * density).toInt())
        val parent = shelf.parent as View
        shelf.pivotX = parent.width / 2f - shelf.left
        shelf.pivotY = 0f
        shelf.scaleX = bounds.scale
        shelf.scaleY = bounds.scale
        if (layout.topMargin != bounds.top) {
            layout.topMargin = bounds.top
            shelf.layoutParams = layout
        }
    }
}
