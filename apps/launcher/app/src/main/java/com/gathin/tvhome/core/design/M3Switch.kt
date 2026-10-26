package com.gathin.tvhome.core.design

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.StateSet
import com.gathin.tvhome.core.motion.Motion

class M3Switch(
    private val density: Float,
    private var p: Palette,
    private val animations: Boolean
) : Drawable() {

    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
    }
    private val thumb = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val argb = ArgbEvaluator()
    private var checked = false
    private var focused = false
    private var progress = 0f
    private var anim: ValueAnimator? = null

    fun setPalette(palette: Palette) {
        p = palette
        invalidateSelf()
    }

    fun setChecked(on: Boolean) {
        if (on == checked && anim == null) {
            progress = if (on) 1f else 0f
            invalidateSelf()
            return
        }
        checked = on
        anim?.cancel()
        if (!animations) {
            progress = if (on) 1f else 0f
            invalidateSelf()
            return
        }
        anim = ValueAnimator.ofFloat(progress, if (on) 1f else 0f).apply {
            interpolator = Motion.FAST_SPATIAL.interpolator()
            duration = Motion.FAST_SPATIAL.durationMs()
            addUpdateListener { a ->
                progress = a.animatedValue as Float
                invalidateSelf()
            }
            start()
        }
    }

    override fun isStateful(): Boolean = true

    override fun onStateChange(state: IntArray): Boolean {
        val f = StateSet.stateSetMatches(intArrayOf(android.R.attr.state_focused), state)
        if (f == focused) return false
        focused = f
        invalidateSelf()
        return true
    }

    override fun getIntrinsicWidth(): Int = Math.round(52 * density)

    override fun getIntrinsicHeight(): Int = Math.round(32 * density)

    override fun draw(c: Canvas) {
        val b = bounds
        val t = progress.coerceIn(0f, 1f)
        val trackOff = if (focused) p.inverseSurface else p.surfaceContainerHighest
        val trackOn = if (focused) p.inversePrimary else p.primary
        val ring = if (focused) p.inverseOnSurface else p.outline
        val thumbOff = if (focused) p.inverseOnSurface else p.outline
        val thumbOn = if (focused) p.inverseSurface else p.onPrimary

        val h = b.height().toFloat()
        val r = h / 2f
        rect.set(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
        track.color = argb.evaluate(t, trackOff, trackOn) as Int
        c.drawRoundRect(rect, r, r, track)
        if (t < 1f) {
            val inset = outline.strokeWidth / 2f
            outline.color = Palette.alpha(ring, Math.round(255 * (1f - t)))
            c.drawRoundRect(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset, r - inset, r - inset, outline)
        }
        val size = (16 + 8 * t) * density
        val start = b.left + r
        val end = b.right - r
        val cx = start + (end - start) * progress
        thumb.color = argb.evaluate(t, thumbOff, thumbOn) as Int
        c.drawCircle(cx, b.exactCenterY().toFloat(), size / 2f, thumb)
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
