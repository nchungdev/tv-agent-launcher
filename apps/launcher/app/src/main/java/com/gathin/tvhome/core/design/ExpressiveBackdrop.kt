package com.gathin.tvhome.core.design

import android.animation.ObjectAnimator
import android.animation.TimeAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import com.gathin.tvhome.core.motion.Motion

class ExpressiveBackdrop(
    context: Context,
    animations: Boolean,
    reducedMotion: Boolean,
    advancedEffects: Boolean
) : FrameLayout(context) {

    private val cookie: ShapeView
    private val clover: ShapeView
    private val motion = ArrayList<ValueAnimator>()
    private val animate: Boolean = animations && !reducedMotion
    private var scrolled = 0f
    private val cookieParallax: SpringAnimation
    private val cloverParallax: SpringAnimation
    private var wanderMs = 0L
    private var wander: TimeAnimator? = null
    private val morph: Boolean = animate && advancedEffects
    private val morphCurve = MORPH_SPRING.interpolator()
    private val morphMs = MORPH_SPRING.durationMs()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        cookie = ShapeView(context, COOKIE_FORMS)
        clover = ShapeView(context, CLOVER_FORMS)
        addView(cookie)
        addView(clover)
        cookieParallax = Motion.DEFAULT_SPATIAL.reduced(reducedMotion)
            .apply(SpringAnimation(cookie, ShapeView.PARALLAX))
        cloverParallax = Motion.DEFAULT_SPATIAL.reduced(reducedMotion)
            .apply(SpringAnimation(clover, ShapeView.PARALLAX))
    }

    fun setPalette(p: Palette) {
        setBackgroundColor(p.surface)
        cookie.setColor(p.primaryContainer)
        clover.setColor(p.tertiaryContainer)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post {
            val headerLine = h * 0.30f
            val cookieR = h * 0.40f
            place(cookie, w * 0.66f, maxOf(headerLine + cookieR, h * 0.80f), cookieR)
            val cloverR = h * 0.24f
            place(clover, w * 0.30f, h - cloverR * 0.35f, cloverR)
        }
    }

    fun onGridScrolled(dy: Int) {
        scrolled += dy
        if (!animate) {
            cookie.setParallax(-scrolled * COOKIE_DEPTH)
            clover.setParallax(-scrolled * CLOVER_DEPTH)
            return
        }
        cookieParallax.animateToFinalPosition(-scrolled * COOKIE_DEPTH)
        cloverParallax.animateToFinalPosition(-scrolled * CLOVER_DEPTH)
    }

    fun start() {
        if (!animate || motion.isNotEmpty()) return
        motion.add(turn(cookie, COOKIE_TURN_MS, 1f))
        motion.add(turn(clover, CLOVER_TURN_MS, -1f))
        motion.add(breathe(cookie, 0))
        motion.add(breathe(clover, BREATH_MS / 2))
        val base = wanderMs
        val w = TimeAnimator().apply {
            setTimeListener { _, totalMs, _ ->
                wanderMs = base + totalMs
                val s = wanderMs / 1000f
                val widthF = width.toFloat()
                val heightF = height.toFloat()
                cookie.setWander(
                    wave(s, COOKIE_PERIOD_X_S, 0f) * COOKIE_REACH_X * widthF,
                    wave(s, COOKIE_PERIOD_Y_S, 0.3f) * COOKIE_REACH_Y * heightF
                )
                clover.setWander(
                    wave(s, CLOVER_PERIOD_X_S, 0.6f) * CLOVER_REACH_X * widthF,
                    wave(s, CLOVER_PERIOD_Y_S, 0.1f) * CLOVER_REACH_Y * heightF
                )
                if (morph) morphBoth(wanderMs)
            }
            start()
        }
        wander = w
        motion.add(w)
    }

    private fun morphBoth(ms: Long) {
        val cycleMs = MORPH_HOLD_MS + morphMs
        morphAt(cookie, ms, cycleMs)
        morphAt(clover, ms + cycleMs / 2, cycleMs)
    }

    private fun morphAt(shape: ShapeView, ms: Long, cycleMs: Long) {
        val step = ms / cycleMs
        val intoMorph = ms % cycleMs - MORPH_HOLD_MS
        val progress = if (intoMorph <= 0) 0f else morphCurve.getInterpolation(minOf(1f, intoMorph / morphMs.toFloat()))
        shape.setMorph(step, progress)
    }

    fun stop() {
        wander?.cancel()
        wander = null
        for (a in motion) a.cancel()
        motion.clear()
    }

    private class ShapeView(context: Context, private val forms: Array<Form>) : View(context) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        private var from = forms[0]
        private var to = forms[0]
        private var morphProgress = 0f
        private var drawnFrame = 0
        private var parallaxY = 0f
        private var wanderX = 0f
        private var wanderY = 0f

        fun setParallax(y: Float) {
            parallaxY = y
            translationY = parallaxY + wanderY
        }

        fun setWander(x: Float, y: Float) {
            wanderX = x
            wanderY = y
            translationX = wanderX
            translationY = parallaxY + wanderY
        }

        fun setMorph(step: Long, progress: Float) {
            val stepFrom = forms[(step % forms.size).toInt()]
            val stepTo = forms[((step + 1) % forms.size).toInt()]
            val frame = Math.round(progress * MORPH_FRAMES)
            if (stepFrom === from && stepTo === to && frame == drawnFrame) return
            from = stepFrom
            to = stepTo
            morphProgress = progress
            drawnFrame = frame
            invalidate()
        }

        fun setColor(color: Int) {
            fill.color = color
            invalidate()
        }

        override fun onDraw(c: Canvas) {
            val r = width / 2f
            morphPath(path, r, r, r, from, to, morphProgress)
            c.drawPath(path, fill)
        }

        companion object {
            private const val MORPH_FRAMES = 48

            val PARALLAX = object : FloatPropertyCompat<ShapeView>("parallax") {
                override fun getValue(v: ShapeView): Float = v.parallaxY

                override fun setValue(v: ShapeView, value: Float) {
                    v.setParallax(value)
                }
            }
        }
    }

    class Form(@JvmField val lobes: Int, @JvmField val depth: Float, @JvmField val rotationDeg: Float) {
        fun scaleAt(angle: Double): Double = waveScale(lobes, depth, Math.toRadians(rotationDeg.toDouble()), angle)
    }

    companion object {
        private const val COOKIE_TURN_MS = 90_000L
        private const val CLOVER_TURN_MS = 140_000L
        private const val BREATH_MS = 7_000L
        private const val BREATH = 1.03f

        private const val COOKIE_DEPTH = 0.25f
        private const val CLOVER_DEPTH = 0.40f

        private const val COOKIE_REACH_X = 0.26f
        private const val COOKIE_REACH_Y = 0.10f
        private const val CLOVER_REACH_X = 0.26f
        private const val CLOVER_REACH_Y = 0.12f

        private const val COOKIE_PERIOD_X_S = 47f
        private const val COOKIE_PERIOD_Y_S = 31f
        private const val CLOVER_PERIOD_X_S = 59f
        private const val CLOVER_PERIOD_Y_S = 37f

        private val COOKIE_FORMS = arrayOf(
            Form(9, 0.06f, 12f), Form(12, 0.05f, 0f),
            Form(8, 0.09f, 22.5f), Form(7, 0.07f, 0f)
        )
        private val CLOVER_FORMS = arrayOf(
            Form(4, 0.20f, 45f), Form(8, 0.13f, 22.5f),
            Form(6, 0.10f, 0f), Form(5, 0.16f, 18f)
        )

        private const val MORPH_HOLD_MS = 4_000L
        private val MORPH_SPRING = Motion.SLOW_SPATIAL
        private const val OUTLINE_STEPS = 180

        private fun place(v: View, cx: Float, cy: Float, r: Float) {
            val size = Math.round(r * 2)
            val lp = LayoutParams(size, size)
            lp.leftMargin = Math.round(cx - r)
            lp.topMargin = Math.round(cy - r)
            v.layoutParams = lp
        }

        private fun wave(seconds: Float, periodS: Float, phase: Float): Float =
            Math.sin(2.0 * Math.PI * (seconds / periodS + phase)).toFloat()

        private fun turn(v: View, ms: Long, direction: Float): ValueAnimator {
            val from = v.rotation
            val a = ObjectAnimator.ofFloat(v, View.ROTATION, from, from + 360f * direction)
            a.duration = ms
            a.repeatCount = ValueAnimator.INFINITE
            a.interpolator = LinearInterpolator()
            a.start()
            return a
        }

        private fun breathe(v: View, delay: Long): ValueAnimator {
            val a = ValueAnimator.ofFloat(1f, BREATH)
            a.duration = BREATH_MS
            a.startDelay = delay
            a.repeatMode = ValueAnimator.REVERSE
            a.repeatCount = ValueAnimator.INFINITE
            a.interpolator = AccelerateDecelerateInterpolator()
            a.addUpdateListener { x ->
                val s = x.animatedValue as Float
                v.scaleX = s
                v.scaleY = s
            }
            a.start()
            return a
        }

        @JvmStatic
        fun morphPath(path: Path, cx: Float, cy: Float, radius: Float, from: Form, to: Form, progress: Float) {
            path.rewind()
            for (i in 0..OUTLINE_STEPS) {
                val t = Math.PI * 2 * i / OUTLINE_STEPS
                val fromScale = from.scaleAt(t)
                val scale = fromScale + (to.scaleAt(t) - fromScale) * progress
                val r = radius * maxOf(0.0, scale)
                val x = (cx + r * Math.cos(t)).toFloat()
                val y = (cy + r * Math.sin(t)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
        }

        @JvmStatic
        fun shapePath(path: Path, cx: Float, cy: Float, radius: Float, lobes: Int, depth: Float, rotationDeg: Float) {
            path.rewind()
            val rot = Math.toRadians(rotationDeg.toDouble())
            for (i in 0..OUTLINE_STEPS) {
                val t = Math.PI * 2 * i / OUTLINE_STEPS
                val r = radius * waveScale(lobes, depth, rot, t)
                val x = (cx + r * Math.cos(t)).toFloat()
                val y = (cy + r * Math.sin(t)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
        }

        private fun waveScale(lobes: Int, depth: Float, rotation: Double, angle: Double): Double =
            1.0 - depth + depth * Math.cos(lobes * (angle - rotation))
    }
}
