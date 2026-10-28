package com.gathin.tvhome.core.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import com.gathin.tvhome.R
import com.gathin.tvhome.core.design.ExpressiveBackdrop
import com.gathin.tvhome.core.design.Palette

class ShapeBadge @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var icon: Drawable? = null
    private var iconColor = Palette.currentOrBaseline().onPrimaryContainer
    private var flatBadge = false

    init {
        fill.color = Palette.currentOrBaseline().primaryContainer
        icon = context.getDrawable(R.drawable.ic_home_settings)
    }

    fun setFlatBadge(flat: Boolean) {
        flatBadge = flat
        invalidate()
    }

    fun setColors(fillColor: Int, iconColor: Int) {
        fill.color = fillColor
        this.iconColor = if (flatBadge) 0xFFFFFFFF.toInt() else iconColor
        invalidate()
    }

    fun setIcon(d: Drawable?) {
        icon = d
        invalidate()
    }

    fun setPalette(p: Palette) {
        fill.color = p.primaryContainer
        iconColor = if (flatBadge) 0xFFFFFFFF.toInt() else p.onPrimaryContainer
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(cx, cy)
        if (!flatBadge) {
            ExpressiveBackdrop.shapePath(path, cx, cy, r, 9, 0.07f, 0f)
            c.drawPath(path, fill)
        }
        val ic = icon
        if (ic != null) {
            val s = Math.round(r * (if (flatBadge) 1.5f else 0.9f))
            ic.setBounds(
                Math.round(cx - s / 2f),
                Math.round(cy - s / 2f),
                Math.round(cx + s / 2f),
                Math.round(cy + s / 2f)
            )
            ic.setTint(if (flatBadge) 0xFFFFFFFF.toInt() else iconColor)
            ic.draw(c)
        }
    }
}
