package com.gathin.tvhome.feature.smarthome.widget
import com.gathin.tvhome.feature.smarthome.data.*
import com.gathin.tvhome.feature.smarthome.presenter.*
import com.gathin.tvhome.feature.smarthome.ui.*

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.smarthome.data.SmartHomeAction
import com.gathin.tvhome.feature.smarthome.data.SmartHomeDevice
import org.json.JSONObject


object SmartHomeClimateControls {

    fun setupControls(
        context: Context,
        card: LinearLayout,
        entity: SmartHomeDevice,
        target: Double,
        low: Double,
        high: Double,
        density: Float,
        onAction: (SmartHomeDevice, SmartHomeAction, JSONObject?) -> Unit
    ) {
        val tempRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, (44 * density).toInt()).apply {
                topMargin = (12 * density).toInt()
            }
            background = GradientDrawable().apply {
                setColor(0xFF24262E.toInt())
                cornerRadius = 12 * density
            }
        }

        tempRow.addView(createTempButton(context, "−", density) {
            val next = (target - 1.0).coerceIn(low, high)
            val action = SmartHomeAction("climate", "set_temperature")
            onAction(entity, action, JSONObject().put("temperature", next))
        })
        tempRow.addView(TextView(context).apply {
            text = "${target.toInt()} °C"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
        })
        tempRow.addView(createTempButton(context, "+", density) {
            val next = (target + 1.0).coerceIn(low, high)
            val action = SmartHomeAction("climate", "set_temperature")
            onAction(entity, action, JSONObject().put("temperature", next))
        })
        card.addView(tempRow)

        val modeBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, (40 * density).toInt()).apply {
                topMargin = (10 * density).toInt()
            }
            background = GradientDrawable().apply {
                setColor(0xFF24262E.toInt())
                cornerRadius = 12 * density
            }
        }

        val isOff = entity.state == "off"
        addModeButton(
            context, modeBar, R.drawable.ic_power, !isOff, 0xFF8E8E93.toInt(), density
        ) {
            val act = if (isOff) {
                SmartHomeAction("climate", "set_hvac_mode")
            } else {
                SmartHomeAction("climate", "turn_off")
            }
            val params = if (isOff) JSONObject().put("hvac_mode", "cool") else null
            onAction(entity, act, params)
        }
        val modes = listOf(
            Triple(R.drawable.ic_snowflake, "cool", 0xFF0085FF.toInt()),
            Triple(R.drawable.ic_droplet, "dry", 0xFF0085FF.toInt()),
            Triple(R.drawable.ic_air, "fan_only", 0xFF0085FF.toInt()),
            Triple(R.drawable.ic_auto_mode, "auto", 0xFF0085FF.toInt())
        )
        for ((icon, mode, color) in modes) {
            addModeButton(context, modeBar, icon, entity.state == mode, color, density) {
                val act = SmartHomeAction("climate", "set_hvac_mode")
                onAction(entity, act, JSONObject().put("hvac_mode", mode))
            }
        }
        card.addView(modeBar)
    }

    private fun createTempButton(
        context: Context,
        symbol: String,
        density: Float,
        onClick: () -> Unit
    ): TextView = TextView(context).apply {
        text = symbol
        textSize = 20f
        gravity = Gravity.CENTER
        setTextColor(0xFFA0A4B0.toInt())
        val size = (44 * density).toInt()
        layoutParams = LinearLayout.LayoutParams(size, size)
        isFocusable = true
        isClickable = true
        background = GradientDrawable().apply { cornerRadius = 10 * density }
        setOnFocusChangeListener { _, hasFocus ->
            (background as? GradientDrawable)?.setColor(
                if (hasFocus) 0xFF353842.toInt() else 0
            )
            setTextColor(if (hasFocus) 0xFFFFFFFF.toInt() else 0xFFA0A4B0.toInt())
        }
        setOnClickListener { onClick() }
    }

    private fun addModeButton(
        context: Context,
        bar: LinearLayout,
        iconRes: Int,
        selected: Boolean,
        activeColor: Int,
        density: Float,
        onClick: () -> Unit
    ) {
        val btn = ImageView(context).apply {
            setImageResource(iconRes)
            val p = (8 * density).toInt()
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
            isFocusable = true
            isClickable = true
            background = GradientDrawable().apply {
                cornerRadius = 10 * density
                setColor(if (selected) activeColor else 0)
            }
            imageTintList = ColorStateList.valueOf(
                if (selected) 0xFFFFFFFF.toInt() else 0xFFA0A4B0.toInt()
            )
            setOnFocusChangeListener { _, hasFocus ->
                (background as? GradientDrawable)?.setColor(
                    when {
                        hasFocus -> 0xFF353842.toInt()
                        selected -> activeColor
                        else -> 0
                    }
                )
                imageTintList = ColorStateList.valueOf(
                    if (hasFocus || selected) 0xFFFFFFFF.toInt() else 0xFFA0A4B0.toInt()
                )
            }
            setOnClickListener { onClick() }
        }
        bar.addView(btn)
    }
}
