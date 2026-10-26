package com.gathin.tvhome.feature.system.coordinator
import com.gathin.tvhome.feature.system.data.*
import com.gathin.tvhome.feature.system.widget.*

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.settings.data.Config
import com.gathin.tvhome.core.design.M3Skin
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.core.motion.Motion
import com.gathin.tvhome.feature.main.widget.WidgetShelf


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
        val spec = Motion.DEFAULT_SPATIAL.reduced(config.reducedMotion)
        spec.drive(panel.animate().alpha(1f).scaleY(1f).translationY(0f)).start()
    }

    fun showContextMenu(anchor: View, items: List<WidgetShelf.MenuItem>) {
        if (items.isEmpty()) return
        val inflater = activity.layoutInflater
        val panel = inflater.inflate(R.layout.popup_inputs, null, false)
        val rows = panel.findViewById<LinearLayout>(R.id.rows)
        val popupWidth = dp(220)
        val popup = PopupWindow(panel, popupWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
        }

        for (item in items) {
            val row = inflater.inflate(R.layout.item_input, rows, false)
            val iconView = row.findViewById<ImageView>(R.id.icon)
            if (item.icon != 0) {
                iconView?.setImageResource(item.icon)
                iconView?.visibility = View.VISIBLE
            } else {
                iconView?.visibility = View.GONE
            }
            row.findViewById<TextView>(R.id.title)?.text = item.title
            val statusView = row.findViewById<TextView>(R.id.status)
            if (!item.value.isNullOrEmpty()) {
                statusView?.text = item.value
                statusView?.visibility = View.VISIBLE
            } else {
                statusView?.visibility = View.GONE
            }
            row.findViewById<View>(R.id.dot)?.visibility = View.GONE

            row.setOnClickListener {
                popup.dismiss()
                item.action.run()
            }
            rows.addView(row)
        }

        popup.showAsDropDown(anchor, (anchor.width - popupWidth) / 2, -dp(8), Gravity.START)
    }

    private fun dp(value: Int): Int = Math.round(value * activity.resources.displayMetrics.density)
}
