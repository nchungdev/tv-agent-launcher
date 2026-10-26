package com.gathin.tvhome.feature.settings.ui
import com.gathin.tvhome.feature.settings.data.*
import com.gathin.tvhome.feature.settings.widget.*

import android.app.Activity
import android.view.View
import com.gathin.tvhome.feature.settings.data.Config
import com.gathin.tvhome.core.motion.Motion


class CenterModal(
    private val activity: Activity,
    private val scrim: View,
    private val dialog: View,
    private val config: Config,
    private val onClose: (() -> Unit)? = { activity.finish() }
) {
    private var closing = false

    fun closing(): Boolean = closing

    fun show() {
        if (!config.animations) return
        val spring = Motion.DEFAULT_SPATIAL.reduced(config.reducedMotion)
        scrim.alpha = 0f
        scrim.animate().alpha(1f).duration = Motion.DEFAULT_EFFECTS.durationMs()
        dialog.scaleX = 0.92f
        dialog.scaleY = 0.92f
        dialog.alpha = 0f
        dialog.post {
            dialog.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setInterpolator(spring.interpolator())
                .setDuration(spring.durationMs())
                .start()
        }
    }

    fun close() {
        if (closing) return
        closing = true
        if (!config.animations) {
            onClose?.invoke()
            return
        }
        val fast = Motion.FAST_SPATIAL.reduced(config.reducedMotion)
        scrim.animate().alpha(0f).duration = fast.durationMs()
        dialog.animate()
            .scaleX(0.92f)
            .scaleY(0.92f)
            .alpha(0f)
            .setInterpolator(fast.interpolator())
            .setDuration(fast.durationMs())
            .withEndAction { onClose?.invoke() }
            .start()
    }
}
