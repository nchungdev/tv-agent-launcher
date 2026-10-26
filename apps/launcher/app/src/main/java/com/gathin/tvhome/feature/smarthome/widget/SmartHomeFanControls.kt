package com.gathin.tvhome.feature.smarthome.widget
import com.gathin.tvhome.feature.smarthome.data.*
import com.gathin.tvhome.feature.smarthome.presenter.*
import com.gathin.tvhome.feature.smarthome.ui.*

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.smarthome.data.SmartHomeAction
import com.gathin.tvhome.feature.smarthome.data.SmartHomeDevice
import org.json.JSONObject


object SmartHomeFanControls {

    fun setupSpeedBar(
        context: Context,
        entity: SmartHomeDevice,
        isOn: Boolean,
        percentage: Int,
        density: Float,
        onAction: (SmartHomeDevice, SmartHomeAction, JSONObject?) -> Unit
    ): LinearLayout {
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-2, (34 * density).toInt())
            background = GradientDrawable().apply {
                setColor(0xFF24262E.toInt())
                cornerRadius = 10 * density
            }
            val p = (2 * density).toInt()
            setPadding(p, p, p, p)
        }
        val act = when {
            !isOn -> 0
            percentage <= 25 -> 1
            percentage <= 50 -> 2
            percentage <= 75 -> 3
            else -> 4
        }
        bar.addView(createSeg(context, R.drawable.ic_fan_off, null, act == 0, density) {
            onAction(entity, SmartHomeAction("fan", "turn_off"), null)
        })
        for (i in 1..4) {
            val speed = i * 25
            bar.addView(createSeg(context, null, "$i", act == i, density) {
                val data = JSONObject().put("percentage", speed)
                onAction(entity, SmartHomeAction("fan", "set_percentage"), data)
            })
        }
        return bar
    }

    fun createPresetSelector(
        context: Context,
        entity: SmartHomeDevice,
        preset: String,
        density: Float,
        onAction: (SmartHomeDevice, SmartHomeAction, JSONObject?) -> Unit,
        onShowMenu: (View, List<String>, String, (String) -> Unit) -> Unit
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
        background = GradientDrawable().apply {
            setColor(0xFF24262E.toInt())
            cornerRadius = 10 * density
        }
        isFocusable = true
        isClickable = true
        setPadding((10 * density).toInt(), 0, (10 * density).toInt(), 0)

        addView(TextView(context).apply {
            text = "• "
            textSize = 14f
            setTextColor(0xFF00E5FF.toInt())
        })
        val title = TextView(context).apply {
            text = preset
            textSize = 13f
            setTextColor(0xFFE1E3EB.toInt())
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        addView(title)
        val arrow = ImageView(context).apply {
            setImageResource(R.drawable.ic_arrow_drop_down)
            val s = (18 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(s, s)
            imageTintList = ColorStateList.valueOf(0xFFA0A4B0.toInt())
        }
        addView(arrow)
        setOnFocusChangeListener { v, hasFocus ->
            val bg = if (hasFocus) 0xFF0085FF.toInt() else 0xFF24262E.toInt()
            (v.background as? GradientDrawable)?.setColor(bg)
            title.setTextColor(if (hasFocus) 0xFFFFFFFF.toInt() else 0xFFE1E3EB.toInt())
            val tint = if (hasFocus) 0xFFFFFFFF.toInt() else 0xFFA0A4B0.toInt()
            arrow.imageTintList = ColorStateList.valueOf(tint)
        }
        setOnClickListener { anchor ->
            val modes = if (entity.presetModes.isNotEmpty()) entity.presetModes
            else listOf("Direct Breeze", "Natural Breeze", "Sleeping Mode")
            onShowMenu(anchor, modes, title.text.toString()) { chosen ->
                title.text = chosen
                val data = JSONObject().put("preset_mode", chosen)
                onAction(entity, SmartHomeAction("fan", "set_preset_mode"), data)
            }
        }
    }

    fun createSeg(
        context: Context,
        iconRes: Int?,
        label: String?,
        selected: Boolean,
        density: Float,
        onClick: () -> Unit
    ): View {
        val normalBg = if (selected) 0xFF5A5E6B.toInt() else 0x00000000
        val v = if (iconRes != null) {
            ImageView(context).apply {
                setImageResource(iconRes)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                val p = (6 * density).toInt()
                setPadding(p, p, p, p)
                val tint = if (selected) 0xFFFFFFFF.toInt() else 0xFFA0A4B0.toInt()
                imageTintList = ColorStateList.valueOf(tint)
            }
        } else {
            TextView(context).apply {
                text = label
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(if (selected) 0xFFFFFFFF.toInt() else 0xFFA0A4B0.toInt())
            }
        }
        return v.apply {
            isFocusable = true
            isClickable = true
            layoutParams = LinearLayout.LayoutParams((32 * density).toInt(), -1)
            background = GradientDrawable().apply {
                setColor(normalBg)
                cornerRadius = 8 * density
            }
            setOnFocusChangeListener { view, hasFocus ->
                val bg = if (hasFocus) 0xFF0085FF.toInt() else normalBg
                (view.background as? GradientDrawable)?.setColor(bg)
                val color = if (hasFocus || selected) 0xFFFFFFFF.toInt() else 0xFFA0A4B0.toInt()
                if (view is TextView) view.setTextColor(color)
                else (view as? ImageView)?.imageTintList = ColorStateList.valueOf(color)
            }
            setOnClickListener { onClick() }
        }
    }

    fun createOscBtn(
        context: Context,
        iconRes: Int,
        selected: Boolean,
        density: Float,
        onClick: () -> Unit
    ): ImageView {
        val normalBg = if (selected) 0xFF5A5E6B.toInt() else 0x00000000
        return ImageView(context).apply {
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val p = (7 * density).toInt()
            setPadding(p, p, p, p)
            isFocusable = true
            isClickable = true
            layoutParams = LinearLayout.LayoutParams((44 * density).toInt(), -1)
            background = GradientDrawable().apply {
                setColor(normalBg)
                cornerRadius = 8 * density
            }
            val tint = if (selected) 0xFFFFFFFF.toInt() else 0xFFA0A4B0.toInt()
            imageTintList = ColorStateList.valueOf(tint)
            setOnFocusChangeListener { v, hasFocus ->
                val bg = if (hasFocus) 0xFF0085FF.toInt() else normalBg
                (v.background as? GradientDrawable)?.setColor(bg)
                val c = if (hasFocus || selected) 0xFFFFFFFF.toInt() else 0xFFA0A4B0.toInt()
                imageTintList = ColorStateList.valueOf(c)
            }
            setOnClickListener { onClick() }
        }
    }

    fun cleanFanName(name: String): String {
        val n = name.trim()
        val parts = n.split("\\s+".toRegex())
        if (parts.size >= 4 && parts.size % 2 == 0) {
            val half = parts.size / 2
            val first = parts.subList(0, half).joinToString(" ")
            val second = parts.subList(half, parts.size).joinToString(" ")
            if (first.equals(second, ignoreCase = true)) return first
        }
        return n.ifBlank { "Quạt" }
    }
}
