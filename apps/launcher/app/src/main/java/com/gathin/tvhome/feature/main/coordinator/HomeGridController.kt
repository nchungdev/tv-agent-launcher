package com.gathin.tvhome.feature.main.coordinator

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View


class HomeGridController(
    private val ambientView: View,
    private val density: Float
) {
    private var ambientColor = -1
    private var ambientAnimator: ValueAnimator? = null

    fun setAmbientColor(target: Int, animations: Boolean) {
        if (target == ambientColor) return
        val from = if (ambientColor == -1) target else ambientColor
        ambientColor = target
        if (!animations) {
            drawAmbient(target)
            return
        }
        ambientAnimator?.cancel()
        val anim = ValueAnimator.ofArgb(from, target)
        anim.duration = 400
        anim.addUpdateListener { a -> drawAmbient(a.animatedValue as Int) }
        anim.start()
        ambientAnimator = anim
    }

    private fun drawAmbient(color: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] = minOf(1f, hsv[1] * 1.1f)
        hsv[2] = maxOf(0.4f, hsv[2])
        val glowColor = Color.HSVToColor(hsv)
        val c = glowColor and 0x00FFFFFF
        val gradient = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            setColors(intArrayOf(0x66000000 or c, 0x20000000 or c, 0x00000000 or c))
            gradientRadius = 360f * density
            setGradientCenter(0.5f, 0.4f)
        }
        ambientView.background = gradient
    }
}
