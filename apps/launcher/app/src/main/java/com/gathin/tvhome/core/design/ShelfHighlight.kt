package com.gathin.tvhome.core.design

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import com.gathin.tvhome.feature.main.widget.WidgetCard
import com.gathin.tvhome.core.motion.Motion

class ShelfHighlight private constructor(
    private val shelf: ViewGroup,
    private val animations: Boolean,
    reducedMotion: Boolean,
    stretch: Boolean
) : Drawable() {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val density: Float = shelf.resources.displayMetrics.density
    private val inset: Float = WidgetCard.REACH_DP * density
    private val stretch: Boolean = stretch && animations && !reducedMotion
    private val trailSpring: SpringAnimation
    private val leadSpring: SpringAnimation
    private val pop: SpringAnimation
    private var trailX = 0f
    private var leadX = 0f
    private var cardW = 0f
    private var cardTop = 0f
    private var cardH = 0f
    private var padDp = FocusIndicator.PADDING_DP
    private var shown = false
    private var lifted = false

    private val focusListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus -> moveTo(newFocus) }

    init {
        strokePaint.strokeWidth = 2f * density
        val base = Palette.currentOrBaseline().primary
        fillPaint.color = Palette.alpha(base, 0x2E)
        strokePaint.color = base
        trailSpring = Motion.DEFAULT_SPATIAL.reduced(reducedMotion).apply(SpringAnimation(this, property("trail", 0)))
        leadSpring = LEAD.reduced(reducedMotion).apply(SpringAnimation(this, property("lead", 1)))
        pop = Motion.FAST_SPATIAL.reduced(reducedMotion).apply(SpringAnimation(this, property("pop", 2)))
        trailSpring.setMinimumVisibleChange(0.5f)
        leadSpring.setMinimumVisibleChange(0.5f)
        pop.setMinimumVisibleChange(0.05f)
    }

    private fun property(name: String, which: Int): FloatPropertyCompat<ShelfHighlight> {
        return object : FloatPropertyCompat<ShelfHighlight>(name) {
            override fun getValue(h: ShelfHighlight): Float = when (which) {
                0 -> h.trailX
                1 -> h.leadX
                else -> h.padDp
            }

            override fun setValue(h: ShelfHighlight, value: Float) {
                when (which) {
                    0 -> h.trailX = value
                    1 -> h.leadX = value
                    else -> h.padDp = value
                }
                h.invalidateSelf()
            }
        }
    }

    fun setLifted(on: Boolean) {
        if (on == lifted) return
        lifted = on
        val target = if (on) FocusIndicator.LIFT_DP else FocusIndicator.PADDING_DP
        if (!animations) {
            padDp = target
            invalidateSelf()
        } else {
            pop.animateToFinalPosition(target)
        }
    }

    fun setColor(argb: Int) {
        fillPaint.color = Palette.alpha(argb, 0x2E)
        strokePaint.color = argb
        invalidateSelf()
    }

    private fun moveTo(focused: View?) {
        if (focused !is WidgetCard || focused.parent !== shelf) {
            if (shown) {
                shown = false
                trailSpring.cancel()
                leadSpring.cancel()
                pop.cancel()
                invalidateSelf()
            }
            return
        }
        val x = focused.left + inset
        cardW = focused.width - 2 * inset
        cardTop = focused.top + inset
        cardH = focused.height - 2 * inset
        if (!shown || !animations) {
            trailSpring.cancel()
            leadSpring.cancel()
            leadX = x
            trailX = x
            shown = true
            val rest = if (lifted) FocusIndicator.LIFT_DP else FocusIndicator.PADDING_DP
            if (animations) {
                pop.cancel()
                padDp = FocusIndicator.POP_FROM_DP
                pop.animateToFinalPosition(rest)
            } else {
                padDp = rest
            }
            invalidateSelf()
            return
        }
        trailSpring.animateToFinalPosition(x)
        if (stretch) leadSpring.animateToFinalPosition(x)
        else {
            leadSpring.cancel()
            leadX = x
        }
    }

    fun follow(card: View?) {
        if (shown && card != null && card.isFocused) moveTo(card)
    }

    override fun draw(c: Canvas) {
        if (!shown || cardW <= 0f) return
        val pad = padDp * density
        var left = trailX
        var right = trailX + cardW
        if (stretch) {
            val ahead = (leadX - trailX).coerceIn(-cardW * MAX_LEAD, cardW * MAX_LEAD)
            left = minOf(left, trailX + ahead)
            right = maxOf(right, trailX + ahead + cardW)
        }
        val r = Shapes.TILE_FOCUSED * density + pad
        c.drawRoundRect(left - pad, cardTop - pad, right + pad, cardTop + cardH + pad, r, r, fillPaint)
        val half = strokePaint.strokeWidth / 2f
        c.drawRoundRect(
            left - pad + half, cardTop - pad + half,
            right + pad - half, cardTop + cardH + pad - half,
            r - half, r - half, strokePaint
        )
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        private val LEAD = Motion.Spec(Motion.DEFAULT_SPATIAL.damping, Motion.FAST_SPATIAL.stiffness)
        private const val MAX_LEAD = 0.3f

        @JvmStatic
        fun attach(shelf: ViewGroup, animations: Boolean, reducedMotion: Boolean, stretch: Boolean): ShelfHighlight {
            val h = ShelfHighlight(shelf, animations, reducedMotion, stretch)
            shelf.background = h
            shelf.viewTreeObserver.addOnGlobalFocusChangeListener(h.focusListener)
            return h
        }
    }
}
