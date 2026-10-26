package com.gathin.tvhome.core.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.core.design.Shapes

open class GlassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val density: Float = resources.displayMetrics.density
    private val inset: Float = 6f * density
    private var tileRadius: Float = Shapes.TILE_FOCUSED * density
    private var ringColor = Palette.currentOrBaseline().secondary
    private var accent = 0

    init {
        ring.style = Paint.Style.STROKE
        ring.strokeWidth = Shapes.FOCUS_RING_WIDTH * density
        ring.color = ringColor
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun setAccent(argb: Int) {
        accent = argb
        ring.color = if (accent != 0) accent or -0x1000000 else ringColor
        invalidate()
    }

    fun setRingColor(argb: Int) {
        ringColor = argb
        if (accent == 0) ring.color = ringColor
        invalidate()
    }

    fun setRadius(px: Float) {
        tileRadius = px
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val stroke = ring.strokeWidth
        val out = Shapes.FOCUS_RING_OFFSET * density + stroke / 2f
        rect.set(inset - out, inset - out, width - inset + out, height - inset + out)
        val r = tileRadius + out
        canvas.drawRoundRect(rect, r, r, ring)
    }
}
