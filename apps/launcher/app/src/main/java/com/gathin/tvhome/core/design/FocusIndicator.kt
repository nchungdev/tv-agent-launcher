package com.gathin.tvhome.core.design

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.recyclerview.widget.RecyclerView
import com.gathin.tvhome.core.motion.Motion

class FocusIndicator(
    private val model: View,
    private val root: ViewGroup,
    private val grid: RecyclerView,
    private val density: Float,
    private val animations: Boolean,
    reducedMotion: Boolean,
    stretch: Boolean
) : RecyclerView.ItemDecoration() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val offset = Rect()
    private var tileW = 0f
    private var tileH = 0f
    private var tileRadius = 0f
    private var padDp = PADDING_DP
    private val pop: SpringAnimation
    private val stretch: Boolean = stretch && animations && !reducedMotion
    private var leadX = 0f
    private var leadY = 0f
    private var scrollShift = 0f
    private val leadSpringX: SpringAnimation
    private val leadSpringY: SpringAnimation
    private var lifted = false

    init {
        paint.color = Palette.currentOrBaseline().primary
        pop = Motion.FAST_SPATIAL.reduced(reducedMotion).apply(SpringAnimation(this, PAD))
        pop.setMinimumVisibleChange(0.05f)
        leadSpringX = LEAD.reduced(reducedMotion).apply(SpringAnimation(this, LEAD_X))
        leadSpringY = LEAD.reduced(reducedMotion).apply(SpringAnimation(this, LEAD_Y))
    }

    fun leadTo(x: Float, y: Float) {
        if (!stretch) return
        leadSpringX.animateToFinalPosition(x)
        leadSpringY.animateToFinalPosition(y)
    }

    fun snapLead(x: Float, y: Float) {
        leadSpringX.cancel()
        leadSpringY.cancel()
        leadX = x
        leadY = y
    }

    fun setScrollShift(shift: Float) {
        scrollShift = shift
    }

    fun setLifted(on: Boolean) {
        if (on == lifted) return
        lifted = on
        val target = if (on) LIFT_DP else PADDING_DP
        if (!animations) {
            padDp = target
            grid.invalidate()
        } else {
            pop.animateToFinalPosition(target)
        }
    }

    fun pop() {
        if (!animations || lifted) return
        pop.cancel()
        padDp = POP_FROM_DP
        pop.animateToFinalPosition(PADDING_DP)
    }

    fun setColor(argb: Int) {
        paint.color = argb
    }

    fun setTile(width: Float, height: Float, radius: Float) {
        tileW = width
        tileH = height
        tileRadius = radius
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val alpha = model.alpha
        if (alpha <= 0f || tileW <= 0f || model.width <= 0) return
        offset.set(0, 0, 0, 0)
        root.offsetDescendantRectToMyCoords(parent, offset)
        val cx = model.translationX + model.left + model.width / 2f - offset.left
        val cy = model.translationY + model.top + model.height / 2f - offset.top
        val pad = padDp * density
        val halfW = tileW / 2f + pad
        val halfH = tileH / 2f + pad
        rect.set(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
        if (stretch) {
            val ax = (leadX - model.translationX).coerceIn(-tileW * MAX_LEAD, tileW * MAX_LEAD)
            val ay = (leadY + scrollShift - model.translationY).coerceIn(-tileH * MAX_LEAD, tileH * MAX_LEAD)
            rect.union(cx + ax - halfW, cy + ay - halfH, cx + ax + halfW, cy + ay + halfH)
        }
        val r = tileRadius + pad
        val base = paint.alpha
        paint.alpha = Math.round(base * alpha)
        c.drawRoundRect(rect, r, r, paint)
        paint.alpha = base
    }

    companion object {
        const val PADDING_DP = 4f
        const val POP_FROM_DP = 1f
        const val LIFT_DP = 8f
        private const val MAX_LEAD = 0.3f
        private val LEAD = Motion.Spec(Motion.DEFAULT_SPATIAL.damping, Motion.FAST_SPATIAL.stiffness)

        private val PAD = object : FloatPropertyCompat<FocusIndicator>("pad") {
            override fun getValue(f: FocusIndicator): Float = f.padDp

            override fun setValue(f: FocusIndicator, value: Float) {
                f.padDp = value
                f.grid.invalidate()
            }
        }

        private val LEAD_X = object : FloatPropertyCompat<FocusIndicator>("leadX") {
            override fun getValue(f: FocusIndicator): Float = f.leadX

            override fun setValue(f: FocusIndicator, value: Float) {
                f.leadX = value
                f.grid.invalidate()
            }
        }

        private val LEAD_Y = object : FloatPropertyCompat<FocusIndicator>("leadY") {
            override fun getValue(f: FocusIndicator): Float = f.leadY

            override fun setValue(f: FocusIndicator, value: Float) {
                f.leadY = value
                f.grid.invalidate()
            }
        }
    }
}
