package com.gathin.tvhome.core.design

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.text.style.ReplacementSpan

class VpnBadge(lockDrawable: Drawable?, private val density: Float) : ReplacementSpan() {

    private val fill: Int = if (lockDrawable != null) GREEN else RED
    private val ink: Int = if (lockDrawable != null) INK else INK_OFF
    private val lock: Drawable? = lockDrawable?.mutate()?.apply { setTint(ink) }
    private val rect = RectF()

    private fun textSize(p: Paint): Float = p.textSize * 0.78f

    private fun lockSize(p: Paint): Float = if (lock != null) p.textSize * 0.8f else 0f

    private fun lockGap(): Float = if (lock != null) 4 * density else 2 * density

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val old = paint.textSize
        paint.textSize = textSize(paint)
        val w = paint.measureText(text, start, end)
        paint.textSize = old
        return (8 * density + 7 * density + lockSize(paint) + lockGap() + w + 9 * density).toInt()
    }

    override fun draw(
        c: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint
    ) {
        val base = paint.textSize
        val lockPx = lockSize(paint)
        val h = base * 1.3f
        val cy = (top + bottom) / 2f
        val left = x + 8 * density
        val right = x + getSize(paint, text, start, end, null)
        rect.set(left, cy - h / 2f, right, cy + h / 2f)

        val oldColor = paint.color
        paint.color = fill
        c.drawRoundRect(rect, h / 2f, h / 2f, paint)

        val lx = (left + 7 * density).toInt()
        if (lock != null) {
            lock.setBounds(lx, (cy - lockPx / 2f).toInt(), (lx + lockPx).toInt(), (cy + lockPx / 2f).toInt())
            lock.draw(c)
        }

        paint.color = ink
        paint.textSize = textSize(paint)
        val fontMetrics = paint.fontMetrics
        val ty = cy - (fontMetrics.ascent + fontMetrics.descent) / 2f
        c.drawText(text, start, end, lx + lockPx + lockGap(), ty, paint)
        paint.textSize = base
        paint.color = oldColor
    }

    companion object {
        private const val GREEN = -0xcf2ea8
        private const val INK = 0x06290F or -0x1000000
        private const val RED = -0xbaac
        private const val INK_OFF = -0x1
    }
}
