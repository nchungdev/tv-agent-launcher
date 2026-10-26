package com.gathin.tvhome.core.motion

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.view.View
import android.widget.TextView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import com.gathin.tvhome.R
import com.gathin.tvhome.core.design.Palette

class Expressive private constructor(
    private val view: TextView,
    private val animations: Boolean,
    reducedMotion: Boolean,
    focusScale: Float
) {
    private val background = GradientDrawable()
    private val argb = ArgbEvaluator()
    private val shape: SpringAnimation
    private val scaleX: SpringAnimation
    private val scaleY: SpringAnimation
    private val focusScale: Float = if (animations) focusScale else 1f
    private var fade: ValueAnimator? = null

    private var shapeProgress = 0f
    private var colorProgress = 0f
    private var restCorners = floatArrayOf(FULL, FULL, FULL, FULL)
    private var focusCorners = floatArrayOf(FULL, FULL, FULL, FULL)
    private val radii = FloatArray(8)
    private var restBg = 0
    private var restFg = 0
    private var selectedBg = 0
    private var selectedFg = 0
    private var activeBg = 0
    private var activeFg = 0
    private var tintIcons = true
    private var iconTint = 0
    private var labelAlpha = 1f
    private var choice = false
    private var listItem = false
    private var focusHook: Runnable? = null

    init {
        background.shape = GradientDrawable.RECTANGLE
        view.background = background
        view.foreground = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            view.defaultFocusHighlightEnabled = false
        }
        shape = Motion.FAST_SPATIAL.reduced(reducedMotion).apply(SpringAnimation(this, SHAPE))
        shape.setMinimumVisibleChange(0.002f)
        scaleX = Motion.FAST_SPATIAL.reduced(reducedMotion).apply(SpringAnimation(view, DynamicAnimation.SCALE_X))
        scaleY = Motion.FAST_SPATIAL.reduced(reducedMotion).apply(SpringAnimation(view, DynamicAnimation.SCALE_Y))
        view.addOnLayoutChangeListener { _, _, t, _, b, _, ot, _, ob ->
            if (b - t != ob - ot) applyShape()
        }
        view.setOnFocusChangeListener { _, _ ->
            update()
            focusHook?.run()
        }
    }

    fun corners(rest: FloatArray, focused: FloatArray): Expressive {
        restCorners = rest.clone()
        focusCorners = focused.clone()
        applyShape()
        return this
    }

    fun asChoice(): Expressive {
        choice = true
        return this
    }

    fun onFocusChange(hook: Runnable): Expressive {
        focusHook = hook
        return this
    }

    fun asListItem(): Expressive {
        listItem = true
        return this
    }

    fun iconTint(argb: Int) {
        if (argb == iconTint) return
        iconTint = argb
        applyColors()
    }

    fun labelAlpha(alpha: Float) {
        if (alpha == labelAlpha) return
        labelAlpha = alpha
        applyColors()
    }

    fun tintIcons(tint: Boolean): Expressive {
        tintIcons = tint
        applyColors()
        return this
    }

    fun palette(p: Palette): Expressive {
        if (listItem) {
            restBg = p.surfaceContainer
            restFg = p.onSurface
            activeBg = p.inverseSurface
            activeFg = p.inverseOnSurface
        } else if (choice) {
            restBg = p.surfaceContainerHigh
            restFg = p.onSurfaceVariant
            activeBg = p.onSurface
            activeFg = p.inverseOnSurface
        } else {
            restBg = Palette.alpha(p.surfaceVariant, 0xCC)
            restFg = Palette.alpha(p.onSurface, 0xCC)
            activeBg = p.onSurface
            activeFg = p.inverseOnSurface
        }
        selectedBg = if (choice) p.secondaryContainer else Palette.alpha(p.secondaryContainer, 0x66)
        selectedFg = p.onSecondaryContainer
        applyColors()
        return this
    }

    fun update() {
        val on = view.isFocused || view.isActivated
        val target = if (on) 1f else 0f
        fade?.cancel()
        if (!animations) {
            shape.cancel()
            colorProgress = target
            shapeProgress = target
            applyShape()
            applyColors()
            return
        }
        fade = ValueAnimator.ofFloat(colorProgress, target).apply {
            interpolator = Motion.DEFAULT_EFFECTS.interpolator()
            duration = Motion.DEFAULT_EFFECTS.durationMs()
            addUpdateListener { a ->
                colorProgress = a.animatedValue as Float
                applyColors()
            }
            start()
        }
        shape.animateToFinalPosition(target)
        val s = if (view.isFocused) focusScale else 1f
        scaleX.animateToFinalPosition(s)
        scaleY.animateToFinalPosition(s)
    }

    private fun applyShape() {
        val full = view.height / 2f
        for (i in 0 until 4) {
            val rest = if (restCorners[i] == FULL) full else restCorners[i]
            val focus = if (focusCorners[i] == FULL) full else focusCorners[i]
            val r = (rest + (focus - rest) * shapeProgress).coerceIn(0f, full)
            radii[i * 2] = r
            radii[i * 2 + 1] = r
        }
        background.cornerRadii = radii
    }

    private fun applyColors() {
        val selected = view.isSelected
        val baseBg = if (selected) selectedBg else restBg
        val baseFg = if (selected) selectedFg else restFg
        background.setColor(argb.evaluate(colorProgress, baseBg, activeBg) as Int)
        val fg = argb.evaluate(colorProgress, baseFg, activeFg) as Int
        view.setTextColor(if (labelAlpha >= 1f) fg else Palette.alpha(fg, Math.round((fg ushr 24) * labelAlpha)))
        val icon = if (iconTint == 0) fg else argb.evaluate(colorProgress, iconTint, activeFg) as Int
        if (tintIcons) applyIconTint(icon)
    }

    private fun applyIconTint(color: Int) {
        for (drawable in view.compoundDrawablesRelative) {
            val layered = drawable as? LayerDrawable ?: continue
            layered.mutate()
            val inner = layered.findDrawableByLayerId(android.R.id.icon) ?: continue
            view.compoundDrawableTintList = null
            inner.mutate().setTint(color)
            view.invalidate()
            return
        }
        view.compoundDrawableTintList = ColorStateList.valueOf(color)
    }

    companion object {
        const val FULL = -1f

        private val SHAPE = object : FloatPropertyCompat<Expressive>("shape") {
            override fun getValue(e: Expressive): Float = e.shapeProgress

            override fun setValue(e: Expressive, value: Float) {
                e.shapeProgress = value
                e.applyShape()
            }
        }

        @JvmStatic
        fun attach(view: TextView, animations: Boolean, reducedMotion: Boolean, focusScale: Float): Expressive {
            val e = Expressive(view, animations, reducedMotion, focusScale)
            view.setTag(R.id.expressive, e)
            return e
        }

        @JvmStatic
        fun of(view: View): Expressive? {
            val tag = view.getTag(R.id.expressive)
            return if (tag is Expressive) tag else null
        }
    }
}
