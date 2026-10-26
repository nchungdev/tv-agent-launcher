package com.gathin.tvhome.core.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.gathin.tvhome.core.design.Palette

class M3Progress @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val active = Paint(Paint.ANTI_ALIAS_FLAG)
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density: Float = resources.displayMetrics.density
    private var progress = 0f

    init {
        setPalette(Palette.currentOrBaseline())
    }

    fun setPalette(p: Palette) {
        active.color = p.primary
        track.color = p.secondaryContainer
        invalidate()
    }

    fun setProgress(value: Float) {
        val v = value.coerceIn(0f, 1f)
        if (v == progress) return
        progress = v
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f
        val gap = GAP_DP * density
        val end = w * progress
        if (end > 0f) c.drawRoundRect(0f, 0f, maxOf(h, end), h, r, r, active)
        val trackStart = if (end > 0f) maxOf(h, end) + gap else 0f
        if (trackStart < w) {
            c.drawRoundRect(trackStart, 0f, w, h, r, r, track)
            c.drawCircle(w - r, r, minOf(r, STOP_DP * density / 2f), active)
        }
    }

    companion object {
        private const val GAP_DP = 4f
        private const val STOP_DP = 4f
    }
}
