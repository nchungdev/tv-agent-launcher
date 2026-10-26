package com.gathin.tvhome.feature.smarthome.widget
import com.gathin.tvhome.feature.smarthome.data.*
import com.gathin.tvhome.feature.smarthome.presenter.*
import com.gathin.tvhome.feature.smarthome.ui.*

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.smarthome.data.SmartHomeAction
import com.gathin.tvhome.feature.smarthome.data.SmartHomeDevice
import org.json.JSONObject


object SmartHomeClimateCard {

    fun create(
        context: Context,
        entity: SmartHomeDevice,
        editMode: Boolean,
        onAction: (SmartHomeDevice, SmartHomeAction, JSONObject?) -> Unit,
        onToggleHidden: (String, View) -> Unit
    ): View {
        val density = context.resources.displayMetrics.density
        val target = entity.temperature ?: entity.currentTemperature ?: 24.0
        val minTemp = if (entity.minimum.isFinite()) entity.minimum else 16.0
        val maxTemp = if (entity.maximum.isFinite()) entity.maximum else 32.0
        val low = minOf(minTemp, maxTemp)
        val high = maxOf(minTemp, maxTemp)
        val isActive = entity.state != "off" && entity.available

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val m = (5 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(0, (174 * density).toInt(), 2f).apply {
                setMargins(m, m, m, m)
            }
            val pad = (14 * density).toInt()
            setPadding(pad, pad, pad, pad)
            background = context.getDrawable(R.drawable.settings_choice_bg)
            tag = entity.id
        }

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }

        val iconBadge = ImageView(context).apply {
            setImageResource(R.drawable.ic_thermostat)
            val badgeSize = (38 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(badgeSize, badgeSize).apply {
                marginEnd = (12 * density).toInt()
            }
            val p = (8 * density).toInt()
            setPadding(p, p, p, p)
            background = GradientDrawable().apply {
                setColor(if (isActive) 0x330085FF.toInt() else 0xFF2A2C34.toInt())
                cornerRadius = 19 * density
            }
            imageTintList = ColorStateList.valueOf(
                if (isActive) 0xFF0085FF.toInt() else 0xFFA0A4B0.toInt()
            )
        }
        topRow.addView(iconBadge)

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }

        textCol.addView(TextView(context).apply {
            text = entity.name
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            includeFontPadding = false
        })

        textCol.addView(TextView(context).apply {
            text = climateStateText(entity.state, target)
            textSize = 12f
            setTextColor(if (isActive) 0xFF0085FF.toInt() else 0xFFA0A4B0.toInt())
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = (2 * density).toInt()
            }
        })
        topRow.addView(textCol)

        val eyeBadge = ImageView(context).apply {
            tag = "eye_${entity.id}"
            val s = (26 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(s, s).apply {
                marginStart = (8 * density).toInt()
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = if (editMode) View.VISIBLE else View.GONE
        }
        topRow.addView(eyeBadge)
        card.addView(topRow)

        if (entity.available) {
            SmartHomeClimateControls.setupControls(
                context, card, entity, target, low, high, density, onAction
            )
        }

        if (editMode) {
            card.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            card.isFocusable = true
            card.isClickable = true
            card.setOnClickListener { onToggleHidden(entity.id, card) }
        }
        return card
    }

    private fun climateStateText(state: String, target: Double): String = when (state) {
        "off" -> "Tắt"
        "cool" -> "Làm lạnh · ${target.toInt()} °C"
        "heat" -> "Sưởi ấm · ${target.toInt()} °C"
        "auto" -> "Tự động · ${target.toInt()} °C"
        "dry" -> "Hút ẩm"
        "fan_only" -> "Quạt"
        else -> state
    }

    fun updateUi(card: View, entity: SmartHomeDevice) {
        val target = entity.temperature ?: entity.currentTemperature ?: 24.0
        val isActive = entity.state != "off" && entity.available
        val topRow = (card as? ViewGroup)?.getChildAt(0) as? ViewGroup
        val iconBadge = topRow?.getChildAt(0) as? ImageView
        if (iconBadge != null) {
            (iconBadge.background as? GradientDrawable)?.setColor(
                if (isActive) 0x330085FF.toInt() else 0xFF2A2C34.toInt()
            )
            iconBadge.imageTintList = ColorStateList.valueOf(
                if (isActive) 0xFF0085FF.toInt() else 0xFFA0A4B0.toInt()
            )
        }
        val textCol = topRow?.getChildAt(1) as? ViewGroup
        val statusView = textCol?.getChildAt(1) as? TextView
        if (statusView != null) {
            statusView.text = climateStateText(entity.state, target)
            statusView.setTextColor(if (isActive) 0xFF0085FF.toInt() else 0xFFA0A4B0.toInt())
        }

        if (entity.available && card is ViewGroup && card.childCount >= 2) {
            val tempRow = card.getChildAt(1) as? ViewGroup
            tempRow?.getChildAt(1)?.let { (it as? TextView)?.text = "${target.toInt()} °C" }

            val modeBar = card.getChildAt(2) as? ViewGroup ?: return
            val isOff = entity.state == "off"
            val modes = listOf(
                !isOff,
                entity.state == "cool",
                entity.state == "dry",
                entity.state == "fan_only",
                entity.state == "auto"
            )
            val colors = listOf(
                0xFF8E8E93.toInt(),
                0xFF0085FF.toInt(),
                0xFF0085FF.toInt(),
                0xFF0085FF.toInt(),
                0xFF0085FF.toInt()
            )
            for (i in 0 until minOf(modeBar.childCount, modes.size)) {
                val btn = modeBar.getChildAt(i) as? ImageView ?: continue
                val sel = modes[i]
                if (!btn.hasFocus()) {
                    (btn.background as? GradientDrawable)?.setColor(if (sel) colors[i] else 0)
                    btn.imageTintList = ColorStateList.valueOf(
                        if (sel) 0xFFFFFFFF.toInt() else 0xFFA0A4B0.toInt()
                    )
                }
            }
        }
    }
}
