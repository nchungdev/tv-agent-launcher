package com.gathin.tvhome.feature.smarthome.widget
import com.gathin.tvhome.feature.smarthome.data.*
import com.gathin.tvhome.feature.smarthome.presenter.*
import com.gathin.tvhome.feature.smarthome.ui.*

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView


object SmartHomePresetMenu {

    fun show(
        context: Context,
        anchor: View,
        modes: List<String>,
        current: String,
        onSelect: (String) -> Unit
    ) {
        val density = context.resources.displayMetrics.density
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (6 * density).toInt()
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(0xFF1E2028.toInt())
                cornerRadius = 12 * density
                setStroke((1 * density).toInt(), 0xFF353945.toInt())
            }
            elevation = 16 * density
        }

        val popup = PopupWindow(
            panel,
            maxOf(anchor.width, (180 * density).toInt()),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = 16 * density
        }

        var initialFocus: View? = null
        for (mode in modes) {
            val isCurrent = mode.equals(current, ignoreCase = true)
            val item = TextView(context).apply {
                val prefix = if (isCurrent) "●  " else "    "
                text = "$prefix$mode"
                textSize = 14f
                val hPad = (14 * density).toInt()
                val vPad = (10 * density).toInt()
                setPadding(hPad, vPad, hPad, vPad)
                isFocusable = true
                isClickable = true
                background = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    cornerRadius = 8 * density
                }
                val defColor = if (isCurrent) 0xFF00E5FF.toInt() else 0xFFE1E3EB.toInt()
                setTextColor(defColor)

                setOnFocusChangeListener { v, hasFocus ->
                    val bg = if (hasFocus) 0xFF0085FF.toInt() else Color.TRANSPARENT
                    (v.background as? GradientDrawable)?.setColor(bg)
                    setTextColor(if (hasFocus) 0xFFFFFFFF.toInt() else defColor)
                }
                setOnClickListener {
                    popup.dismiss()
                    onSelect(mode)
                }
            }
            panel.addView(item)
            if (isCurrent && initialFocus == null) {
                initialFocus = item
            }
        }

        popup.showAsDropDown(anchor, 0, (4 * density).toInt())
        (initialFocus ?: panel.getChildAt(0))?.requestFocus()
    }
}
