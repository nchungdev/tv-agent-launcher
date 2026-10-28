package com.gathin.tvhome.feature.main.widget

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.widget.FrameLayout
import com.gathin.tvhome.core.design.FocusIndicator
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.core.design.Shapes
import com.gathin.tvhome.core.motion.Motion


open class WidgetCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hole = Paint(Paint.ANTI_ALIAS_FLAG)
    private val innerStroke = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density: Float = resources.displayMetrics.density
    private var ghost = false
    private var flat = false
    private var glass = false
    private var palette: Palette = Palette.currentOrBaseline()
    private var motion = false
    private var turning = false
    private var turnPaint: Runnable? = null
    private var turnFade: ObjectAnimator? = null

    init {
        setWillNotDraw(false)
        isFocusable = true
        isClickable = true
        val inset = Math.round(REACH_DP * density)
        setPadding(inset, inset, inset, inset)
        outline.style = Paint.Style.STROKE
        outline.strokeWidth = 2 * density
        innerStroke.style = Paint.Style.STROKE
        innerStroke.strokeWidth = density
        innerStroke.color = 0x1AFFFFFF
        setPalette(Palette.currentOrBaseline())
    }

    fun setGhost(on: Boolean) {
        ghost = on
        invalidate()
    }

    fun setGlass(on: Boolean) {
        glass = on
        setPalette(palette)
    }

    fun setFlat(on: Boolean) {
        flat = on
        invalidate()
    }

    fun setMotion(on: Boolean) {
        motion = on
    }

    override fun setPressed(pressed: Boolean) {
        val was = isPressed
        super.setPressed(pressed)
        if (pressed == was || !motion || !hasOnClickListeners()) return
        val scale = if (pressed) PRESSED_SCALE else 1f
        Motion.FAST_SPATIAL.drive(animate().scaleX(scale).scaleY(scale))
    }

    fun turn(paint: Runnable) {
        val content = getChildAt(0)
        if (!motion || content == null) {
            paint.run()
            return
        }
        if (turning) settle(content)
        turning = true
        turnPaint = paint
        val shift = TURN_DP * density
        content.animate().alpha(0f).translationX(-shift).setDuration(LEAVE_MS)
            .setInterpolator(AccelerateInterpolator()).withEndAction { arrive(content, shift) }
    }

    private fun arrive(content: View, shift: Float) {
        turnPaint?.run()
        content.translationX = shift
        Motion.DEFAULT_SPATIAL.drive(content.animate().translationX(0f)).withEndAction { turning = false }
        turnFade = Motion.DEFAULT_EFFECTS.fade(content, 1f, 0)
    }

    private fun settle(content: View) {
        content.animate().cancel()
        turnFade?.cancel()
        turnPaint?.run()
        content.translationX = 0f
        content.alpha = 1f
        turning = false
    }

    fun setPalette(p: Palette) {
        val darkP = Palette.dark(p)
        palette = darkP
        fill.color = if (glass) Palette.alpha(darkP.onSurface, 0x14) else if (flat) Palette.alpha(darkP.primary, 0x33) else darkP.surfaceContainer
        outline.color = darkP.primary
        hole.color = darkP.surface
        innerStroke.color = Palette.alpha(if (glass) darkP.onSurface else darkP.outlineVariant, if (glass) 0x2E else 0x4D)
        invalidate()
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (ghost || flat) invalidate()
    }

    override fun onDraw(c: Canvas) {
        val inset = REACH_DP * density
        val radius = Shapes.TILE_FOCUSED * density
        val w = width.toFloat()
        val h = height.toFloat()
        if (flat && !isFocused) return
        if (flat && isFocused) {
            val half = outline.strokeWidth / 2f
            c.drawRoundRect(inset, inset, w - inset, h - inset, radius, radius, fill)
            c.drawRoundRect(
                inset + half, inset + half,
                w - inset - half, h - inset - half,
                radius - half, radius - half,
                outline
            )
            return
        }
        if (ghost && !isFocused) {
            val half = outline.strokeWidth / 2f
            c.drawRoundRect(inset, inset, w - inset, h - inset, radius, radius, hole)
            c.drawRoundRect(inset + half, inset + half, w - inset - half, h - inset - half, radius - half, radius - half, outline)
            return
        }
        c.drawRoundRect(inset, inset, w - inset, h - inset, radius, radius, fill)
        val half = innerStroke.strokeWidth / 2f
        c.drawRoundRect(
            inset + half, inset + half,
            w - inset - half, h - inset - half,
            radius - half, radius - half,
            innerStroke
        )
    }

    companion object {
        const val REACH_DP = FocusIndicator.PADDING_DP
        private const val PRESSED_SCALE = 0.96f
        private const val TURN_DP = 24f
        private const val LEAVE_MS = 90L
    }
}
