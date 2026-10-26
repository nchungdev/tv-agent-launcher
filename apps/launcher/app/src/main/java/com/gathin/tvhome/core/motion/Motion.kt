package com.gathin.tvhome.core.motion

import android.animation.ObjectAnimator
import android.view.View
import android.view.ViewPropertyAnimator
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

object Motion {

    @JvmField val FAST_SPATIAL = Spec(0.6f, 800f)
    @JvmField val DEFAULT_SPATIAL = Spec(0.8f, 380f)
    @JvmField val SLOW_SPATIAL = Spec(0.8f, 200f)
    @JvmField val STANDARD_DEFAULT_SPATIAL = Spec(0.9f, 700f)
    @JvmField val FAST_EFFECTS = Spec(1.0f, 3800f)
    @JvmField val DEFAULT_EFFECTS = Spec(1.0f, 1600f)
    @JvmField val SLOW_EFFECTS = Spec(1.0f, 800f)

    private const val REDUCED_DAMPING = 0.95f
    const val EMPHASIZED_DECELERATE_MS = 500L
    const val EMPHASIZED_ACCELERATE_MS = 200L

    @JvmStatic
    fun emphasizedDecelerate(): Interpolator = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)

    @JvmStatic
    fun emphasizedAccelerate(): Interpolator = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)

    class Spec(@JvmField val damping: Float, @JvmField val stiffness: Float) {

        fun reduced(reducedMotion: Boolean): Spec {
            return if (reducedMotion && damping < REDUCED_DAMPING) Spec(REDUCED_DAMPING, stiffness) else this
        }

        fun apply(animation: SpringAnimation): SpringAnimation {
            var force = animation.spring
            if (force == null) {
                force = SpringForce()
                animation.spring = force
            }
            force.setDampingRatio(damping).setStiffness(stiffness)
            return animation
        }

        fun interpolator(): Interpolator = SpringInterpolator(this)

        fun drive(animator: ViewPropertyAnimator): ViewPropertyAnimator {
            return animator.setInterpolator(interpolator()).setDuration(durationMs())
        }

        fun fade(view: View, alpha: Float, delayMs: Long): ObjectAnimator {
            val fade = ObjectAnimator.ofFloat(view, View.ALPHA, alpha)
            fade.setInterpolator(interpolator())
            fade.duration = durationMs()
            fade.startDelay = delayMs
            fade.start()
            return fade
        }

        fun durationMs(): Long = SpringInterpolator.settleMs(this)
    }

    class SpringInterpolator(spec: Spec) : Interpolator {
        private val damping = spec.damping
        private val omega = Math.sqrt(spec.stiffness.toDouble()).toFloat()
        private val settleSeconds = settleMs(spec) / 1000f

        override fun getInterpolation(input: Float): Float {
            if (input >= 1f) return 1f
            return position(damping, omega, input * settleSeconds)
        }

        companion object {
            private const val THRESHOLD = 0.001f

            @JvmStatic
            fun position(zeta: Float, omega: Float, t: Float): Float {
                if (zeta >= 1f) {
                    return (1.0 - Math.exp((-omega * t).toDouble()) * (1.0 + omega * t)).toFloat()
                }
                val wd = omega * Math.sqrt((1.0 - zeta * zeta))
                return (1.0 - Math.exp((-zeta * omega * t).toDouble()) *
                        (Math.cos(wd * t) + zeta * omega / wd * Math.sin(wd * t))).toFloat()
            }

            @JvmStatic
            fun settleMs(spec: Spec): Long {
                val omega = Math.sqrt(spec.stiffness.toDouble()).toFloat()
                for (ms in 1 until 5000) {
                    val t = ms / 1000f
                    val damp = spec.damping.toDouble()
                    val envelope = (Math.exp(-minOf(1.0, damp) * omega * t) *
                            if (damp >= 1.0) 1.0 + omega * t else 1.0 / Math.sqrt(1.0 - damp * damp)).toFloat()
                    if (envelope < THRESHOLD) return ms.toLong()
                }
                return 5000L
            }
        }
    }
}
