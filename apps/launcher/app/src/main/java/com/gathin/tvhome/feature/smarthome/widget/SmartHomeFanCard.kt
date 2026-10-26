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
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.smarthome.data.SmartHomeAction
import com.gathin.tvhome.feature.smarthome.data.SmartHomeDevice
import org.json.JSONObject


object SmartHomeFanCard {

    fun create(
        context: Context,
        entity: SmartHomeDevice,
        editMode: Boolean,
        onAction: (SmartHomeDevice, SmartHomeAction, JSONObject?) -> Unit,
        onToggleHidden: (String, View) -> Unit,
        onShowPresetMenu: (View, List<String>, String, (String) -> Unit) -> Unit
    ): View {
        val density = context.resources.displayMetrics.density
        val isOn = entity.state == "on" && entity.available
        val percentage = entity.percentage ?: if (isOn) 100 else 0
        val isOsc = entity.oscillating == true && isOn
        val currentPreset = entity.presetMode ?: "Direct Breeze"

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, (174 * density).toInt(), 2f).apply {
                val m = (5 * density).toInt()
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
            setImageResource(if (isOn) R.drawable.ic_fan else R.drawable.ic_fan_off)
            val badgeSize = (38 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(badgeSize, badgeSize).apply {
                marginEnd = (10 * density).toInt()
            }
            val p = (8 * density).toInt()
            setPadding(p, p, p, p)
            background = GradientDrawable().apply {
                setColor(if (isOn) 0x3300BCD4.toInt() else 0xFF2A2C34.toInt())
                cornerRadius = 19 * density
            }
            imageTintList = ColorStateList.valueOf(
                if (isOn) 0xFF00E5FF.toInt() else 0xFFA0A4B0.toInt()
            )
        }
        topRow.addView(iconBadge)

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        textCol.addView(TextView(context).apply {
            text = SmartHomeFanControls.cleanFanName(entity.name)
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
        })
        textCol.addView(TextView(context).apply {
            text = if (!isOn) "Tắt" else "$percentage%"
            textSize = 12f
            setTextColor(if (isOn) 0xFF00E5FF.toInt() else 0xFFA0A4B0.toInt())
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = (2 * density).toInt()
            }
        })
        topRow.addView(textCol)

        val speedBar = SmartHomeFanControls.setupSpeedBar(
            context, entity, isOn, percentage, density, onAction
        )
        topRow.addView(speedBar)

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
        if (editMode) speedBar.visibility = View.GONE
        card.addView(topRow)

        setupMidAndBottom(
            context, card, entity, isOn, isOsc, currentPreset, density,
            onAction, onShowPresetMenu
        )

        if (editMode) {
            card.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            card.isFocusable = true
            card.isClickable = true
            card.setOnClickListener { onToggleHidden(entity.id, card) }
        }
        return card
    }

    private fun setupMidAndBottom(
        context: Context,
        card: LinearLayout,
        entity: SmartHomeDevice,
        isOn: Boolean,
        isOsc: Boolean,
        preset: String,
        density: Float,
        onAction: (SmartHomeDevice, SmartHomeAction, JSONObject?) -> Unit,
        onShowMenu: (View, List<String>, String, (String) -> Unit) -> Unit
    ) {
        val midRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, (36 * density).toInt()).apply {
                topMargin = (10 * density).toInt()
            }
        }
        val oscBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-2, -1)
            background = GradientDrawable().apply {
                setColor(0xFF24262E.toInt())
                cornerRadius = 10 * density
            }
            val p = (2 * density).toInt()
            setPadding(p, p, p, p)
        }
        oscBar.addView(
            SmartHomeFanControls.createOscBtn(
                context, R.drawable.ic_oscillate_off, !isOsc, density
            ) {
                val data = JSONObject().put("oscillating", false)
                onAction(entity, SmartHomeAction("fan", "oscillate"), data)
            }
        )
        oscBar.addView(
            SmartHomeFanControls.createOscBtn(
                context, R.drawable.ic_oscillate, isOsc, density
            ) {
                val data = JSONObject().put("oscillating", true)
                onAction(entity, SmartHomeAction("fan", "oscillate"), data)
            }
        )
        midRow.addView(oscBar)

        val divider = View(context).apply {
            val m = (10 * density).toInt()
            layoutParams = LinearLayout.LayoutParams((1 * density).toInt(), (20 * density).toInt())
                .apply { setMargins(m, 0, m, 0) }
            setBackgroundColor(0x33FFFFFF.toInt())
        }
        midRow.addView(divider)
        midRow.addView(
            SmartHomeFanControls.createPresetSelector(
                context, entity, preset, density, onAction, onShowMenu
            )
        )
        card.addView(midRow)

        val power = ImageView(context).apply {
            setImageResource(if (isOn) R.drawable.ic_fan else R.drawable.ic_fan_off)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val p = (8 * density).toInt()
            setPadding(p, p, p, p)
            isFocusable = true
            isClickable = true
            layoutParams = LinearLayout.LayoutParams(-1, (38 * density).toInt()).apply {
                topMargin = (10 * density).toInt()
            }
            val normalBg = if (isOn) 0xFF5A5E6B.toInt() else 0xFF24262E.toInt()
            background = GradientDrawable().apply {
                setColor(normalBg)
                cornerRadius = 10 * density
            }
            imageTintList = ColorStateList.valueOf(
                if (isOn) 0xFFFFFFFF.toInt() else 0xFFA0A4B0.toInt()
            )
            setOnFocusChangeListener { v, hasFocus ->
                val bg = if (hasFocus) 0xFF0085FF.toInt() else normalBg
                (v.background as? GradientDrawable)?.setColor(bg)
                val tint = if (hasFocus || isOn) 0xFFFFFFFF.toInt() else 0xFFA0A4B0.toInt()
                imageTintList = ColorStateList.valueOf(tint)
            }
            setOnClickListener { onAction(entity, SmartHomeAction("fan", "toggle"), null) }
        }
        card.addView(power)
    }
}
