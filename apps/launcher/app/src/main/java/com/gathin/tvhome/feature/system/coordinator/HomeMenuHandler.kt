package com.gathin.tvhome.feature.system.coordinator
import com.gathin.tvhome.feature.system.data.*
import com.gathin.tvhome.feature.system.widget.*

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import com.gathin.tvhome.feature.settings.data.Config
import com.gathin.tvhome.core.design.M3Skin
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.core.motion.Motion


class HomeMenuHandler(
    private val activity: Activity,
    private val config: Config
) {

    fun createPopup(panel: View, widthDp: Int): PopupWindow {
        return PopupWindow(panel, dp(widthDp), ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            animationStyle = 0
            elevation = 0f
        }
    }

    fun showMenuAt(popup: PopupWindow, panel: View, anchor: View, x: Int, y: Int) {
        panel.alpha = 0f
        panel.pivotY = 0f
        panel.scaleY = 0.92f
        panel.translationY = -dp(8).toFloat()
        popup.showAtLocation(activity.window.decorView, Gravity.TOP or Gravity.START, x, y)
        M3Skin.apply(panel, Palette.currentOrBaseline(), config.animations, config.reducedMotion)
        Motion.DEFAULT_SPATIAL.reduced(config.reducedMotion).drive(panel.animate().alpha(1f).scaleY(1f).translationY(0f)).start()
    }

    private fun dp(value: Int): Int = Math.round(value * activity.resources.displayMetrics.density)
}
