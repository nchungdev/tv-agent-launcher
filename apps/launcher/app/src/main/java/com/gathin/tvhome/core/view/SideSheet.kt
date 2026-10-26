package com.gathin.tvhome.core.view

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import com.gathin.tvhome.feature.settings.data.Config
import com.gathin.tvhome.core.motion.Motion

class SideSheet(
    private val activity: Activity,
    private val scrim: View,
    private val sheet: View,
    private val config: Config,
    private val onClosed: (() -> Unit)? = null
) {
    private var closing = false

    fun closing(): Boolean = closing

    @JvmOverloads
    fun slideIn(sections: ViewGroup? = null) {
        if (!config.animations) return
        if (sections != null && !config.reducedMotion) staggerIn(sections)
        val spring = Motion.DEFAULT_SPATIAL.reduced(config.reducedMotion)
        scrim.alpha = 0f
        scrim.animate().alpha(1f).duration = Motion.DEFAULT_EFFECTS.durationMs()
        sheet.translationX = offscreen()
        sheet.post {
            sheet.translationX = offscreen()
            sheet.animate().translationX(0f).setInterpolator(spring.interpolator()).duration = spring.durationMs()
        }
    }

    private fun staggerIn(sections: ViewGroup) {
        val offset = STAGGER_DP * activity.resources.displayMetrics.density
        val spatial = Motion.DEFAULT_SPATIAL
        val effects = Motion.DEFAULT_EFFECTS
        for (i in 0 until minOf(STAGGERED, sections.childCount)) {
            val section = sections.getChildAt(i)
            val delay = (i + 1) * STAGGER_MS
            section.translationX = offset * (1 + i * 0.15f)
            section.alpha = 0f
            spatial.drive(section.animate().translationX(0f)).startDelay = delay
            effects.fade(section, 1f, delay)
        }
    }

    fun close() {
        if (closing) return
        closing = true
        val finishAction = { onClosed?.invoke() ?: activity.finish() }
        if (!config.animations) {
            finishAction()
            return
        }
        val fast = Motion.FAST_SPATIAL.reduced(config.reducedMotion)
        scrim.animate().alpha(0f).duration = fast.durationMs()
        sheet.animate().translationX(offscreen())
            .setInterpolator(fast.interpolator())
            .setDuration(fast.durationMs())
            .withEndAction(finishAction)
    }

    private fun offscreen(): Float {
        return if (sheet.width > 0) sheet.width + 24 * activity.resources.displayMetrics.density
        else activity.resources.displayMetrics.widthPixels.toFloat()
    }

    companion object {
        private const val STAGGERED = 8
        private const val STAGGER_MS = 35L
        private const val STAGGER_DP = 56f
    }
}
