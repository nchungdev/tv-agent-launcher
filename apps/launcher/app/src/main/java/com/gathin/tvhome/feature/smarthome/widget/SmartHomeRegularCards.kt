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
import com.gathin.tvhome.feature.settings.data.Config
import com.gathin.tvhome.feature.smarthome.data.SmartHomeAction
import com.gathin.tvhome.feature.smarthome.data.SmartHomeActions
import com.gathin.tvhome.feature.smarthome.data.SmartHomeDevice
import com.gathin.tvhome.core.design.M3Skin
import com.gathin.tvhome.core.design.Palette


object SmartHomeRegularCards {

    fun createSceneCard(
        context: Context,
        entity: SmartHomeDevice,
        editMode: Boolean,
        config: Config,
        onAction: (SmartHomeDevice, SmartHomeAction) -> Unit,
        onToggleHidden: (String, View) -> Unit
    ): View {
        val density = context.resources.displayMetrics.density
        val palette = Palette.dark(Palette.currentOrBaseline())

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, (82 * density).toInt(), 1f).apply {
                val m = (5 * density).toInt()
                setMargins(m, m, m, m)
            }
            val pad = (14 * density).toInt()
            setPadding(pad, 0, pad, 0)
            background = context.getDrawable(R.drawable.settings_choice_bg)
            isFocusable = true
            isClickable = true
            isEnabled = entity.available
            tag = entity.id
        }

        val badge = ImageView(context).apply {
            setImageResource(R.drawable.ic_home)
            val badgeSize = (40 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(badgeSize, badgeSize).apply {
                marginEnd = (12 * density).toInt()
            }
            val p = (8 * density).toInt()
            setPadding(p, p, p, p)
            background = GradientDrawable().apply {
                setColor(0xFF2A2C34.toInt())
                cornerRadius = 20 * density
            }
            imageTintList = ColorStateList.valueOf(0xFFE1E3EB.toInt())
        }
        card.addView(badge)

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        textCol.addView(TextView(context).apply {
            text = entity.name
            textSize = 13.5f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(0xFFFFFFFF.toInt())
            includeFontPadding = false
        })
        textCol.addView(TextView(context).apply {
            text = "Nhấn để kích hoạt"
            textSize = 12f
            setTextColor(0xB0B3BD.toInt())
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = (2 * density).toInt()
            }
        })
        card.addView(textCol)

        val eyeBadge = ImageView(context).apply {
            tag = "eye_${entity.id}"
            val size = (24 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginStart = (6 * density).toInt()
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = if (editMode) View.VISIBLE else View.GONE
        }
        card.addView(eyeBadge)

        val action = SmartHomeActions.toggle(entity)
        card.setOnClickListener {
            if (editMode) {
                onToggleHidden(entity.id, card)
                return@setOnClickListener
            }
            if (action != null) onAction(entity, action)
        }

        M3Skin.apply(card, palette, config.animations, config.reducedMotion)
        return card
    }

    fun createCard(
        context: Context,
        entity: SmartHomeDevice,
        editMode: Boolean,
        config: Config,
        onAction: (SmartHomeDevice, SmartHomeAction) -> Unit,
        onToggleHidden: (String, View) -> Unit
    ): View {
        val density = context.resources.displayMetrics.density
        val palette = Palette.dark(Palette.currentOrBaseline())

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, (82 * density).toInt(), 1f).apply {
                val m = (5 * density).toInt()
                setMargins(m, m, m, m)
            }
            val pad = (14 * density).toInt()
            setPadding(pad, 0, pad, 0)
            background = context.getDrawable(R.drawable.settings_choice_bg)
            isFocusable = true
            isClickable = true
            isEnabled = entity.available
            tag = entity.id
        }

        val isOn = entity.state == "on"
        val badge = ImageView(context).apply {
            setImageResource(resolveIconRes(entity.domain))
            val badgeSize = (40 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(badgeSize, badgeSize).apply {
                marginEnd = (12 * density).toInt()
            }
            val p = (8 * density).toInt()
            setPadding(p, p, p, p)
            background = GradientDrawable().apply {
                val bgColor = if (isOn) 0x33FFC107.toInt() else 0xFF2A2C34.toInt()
                setColor(bgColor)
                cornerRadius = 20 * density
            }
            val tintColor = if (isOn) 0xFFFFC107.toInt() else 0xFFA0A4B0.toInt()
            imageTintList = ColorStateList.valueOf(tintColor)
        }
        card.addView(badge)

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        textCol.addView(TextView(context).apply {
            text = entity.name
            textSize = 13.5f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(0xFFFFFFFF.toInt())
            includeFontPadding = false
        })
        textCol.addView(TextView(context).apply {
            text = resolveStateLabel(entity.state)
            textSize = 12f
            setTextColor(if (isOn) 0xFFFFC107.toInt() else 0xFFA0A4B0.toInt())
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = (2 * density).toInt()
            }
        })
        card.addView(textCol)

        val eyeBadge = ImageView(context).apply {
            tag = "eye_${entity.id}"
            val size = (24 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginStart = (6 * density).toInt()
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = if (editMode) View.VISIBLE else View.GONE
        }
        card.addView(eyeBadge)

        val action = SmartHomeActions.toggle(entity)
        card.setOnClickListener {
            if (editMode) {
                onToggleHidden(entity.id, card)
                return@setOnClickListener
            }
            if (action != null) onAction(entity, action)
        }

        M3Skin.apply(card, palette, config.animations, config.reducedMotion)
        return card
    }

    fun updateCardUi(card: View, entity: SmartHomeDevice) {
        val isOn = entity.state == "on"
        val iconBadge = (card as? ViewGroup)?.getChildAt(0) as? ImageView
        if (iconBadge != null) {
            val bgColor = if (isOn) 0x33FFC107.toInt() else 0xFF2A2C34.toInt()
            (iconBadge.background as? GradientDrawable)?.setColor(bgColor)
            val tintColor = if (isOn) 0xFFFFC107.toInt() else 0xFFA0A4B0.toInt()
            iconBadge.imageTintList = ColorStateList.valueOf(tintColor)
        }

        val textCol = (card as? ViewGroup)?.getChildAt(1) as? ViewGroup
        val statusView = textCol?.getChildAt(1) as? TextView
        if (statusView != null) {
            statusView.text = resolveStateLabel(entity.state)
            statusView.setTextColor(if (isOn) 0xFFFFC107.toInt() else 0xFFA0A4B0.toInt())
        }
    }

    fun updateCardEditState(card: View, hidden: Boolean) {
        val eyeBadge = card.findViewWithTag<ImageView>("eye_${card.tag}")
        if (eyeBadge != null) {
            eyeBadge.setImageResource(
                if (hidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility
            )
            eyeBadge.imageTintList = ColorStateList.valueOf(
                if (hidden) 0xFFFF5252.toInt() else 0xFF4CAF50.toInt()
            )
        }
        card.alpha = if (hidden) 0.35f else 1.0f
    }

    private fun resolveIconRes(domain: String): Int = when (domain) {
        "light" -> R.drawable.ic_light
        "switch" -> R.drawable.ic_power
        "climate" -> R.drawable.ic_thermostat
        "fan" -> R.drawable.ic_fan
        "cover" -> R.drawable.ic_move
        "scene", "script" -> R.drawable.ic_auto_awesome
        else -> R.drawable.ic_smart_home
    }

    private fun resolveStateLabel(state: String): String = when (state) {
        "unavailable" -> "Mất kết nối"
        "unknown" -> "Chưa rõ"
        "on" -> "Bật"
        "off" -> "Tắt"
        else -> state
    }
}
