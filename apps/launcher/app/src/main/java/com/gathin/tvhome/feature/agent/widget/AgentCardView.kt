package com.gathin.tvhome.feature.agent.widget
import com.gathin.tvhome.feature.agent.data.*
import com.gathin.tvhome.feature.agent.coordinator.*
import com.gathin.tvhome.feature.agent.ui.*

import com.gathin.tvhome.feature.agent.coordinator.AgentCardActionHandler
import com.gathin.tvhome.core.view.AgentCardData
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.gathin.tvhome.core.util.ImageLoader
import com.gathin.tvhome.core.design.Palette



class AgentCardView(context: Context, private val card: AgentCardData) : LinearLayout(context) {

    init {
        orientation = HORIZONTAL
        isFocusable = true
        isClickable = true
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS

        val density = context.resources.displayMetrics.density
        val palette = Palette.currentOrBaseline()

        val pad = (12 * density).toInt()
        setPadding(pad, pad, pad, pad)

        val normalBg = GradientDrawable().apply {
            setColor(palette.surfaceContainerHigh)
            cornerRadius = 14 * density
            setStroke((1.5f * density).toInt(), palette.outlineVariant)
        }
        val focusedBg = GradientDrawable().apply {
            setColor(palette.surfaceContainerHighest)
            cornerRadius = 14 * density
            setStroke((2.5f * density).toInt(), palette.primary)
        }
        background = normalBg

        setOnFocusChangeListener { _, hasFocus ->
            background = if (hasFocus) focusedBg else normalBg
            animate().scaleX(if (hasFocus) 1.03f else 1.0f)
                .scaleY(if (hasFocus) 1.03f else 1.0f)
                .setDuration(150)
                .start()
        }

        setOnClickListener {
            AgentCardActionHandler.handleCardAction(context, card)
        }

        val posterWidth = (72 * density).toInt()
        val posterHeight = (108 * density).toInt()
        val poster = ImageView(context).apply {
            layoutParams = LayoutParams(posterWidth, posterHeight)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                setColor(0xFF202532.toInt())
                cornerRadius = 8 * density
            }
            clipToOutline = true
        }
        addView(poster)
        if (card.posterUrl.isNotEmpty()) {
            ImageLoader.load(card.posterUrl, poster, posterWidth, posterHeight)
        }

        val meta = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            val metaParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (14 * density).toInt()
            }
            layoutParams = metaParams
        }

        val titleView = TextView(context).apply {
            text = card.title
            setTextColor(palette.onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        meta.addView(titleView)

        val subInfo = listOfNotNull(
            if (card.year.isNotEmpty()) card.year else null,
            if (card.type.isNotEmpty()) card.type else null
        ).joinToString(" · ")

        if (subInfo.isNotEmpty()) {
            val subView = TextView(context).apply {
                text = subInfo
                setTextColor(palette.onSurfaceVariant)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    topMargin = (4 * density).toInt()
                }
                layoutParams = lp
            }
            meta.addView(subView)
        }

        val requested = card.status.equals(AgentCardData.STATUS_REQUESTED, ignoreCase = true)
        val downloading = card.status.equals(AgentCardData.STATUS_DOWNLOADING, ignoreCase = true)
        val isPlex = card.isPlex()
        val web = card.isWeb()
        val badgeText = when {
            isPlex -> "PLEX"
            web -> "WEB"
            downloading -> "ĐANG TẢI"
            requested -> "ĐÃ YÊU CẦU"
            else -> card.sourceId.ifBlank { "THƯ VIỆN" }
        }
        val badgeColor = when {
            isPlex -> 0xFFE5A00D.toInt()
            web -> 0xFF3A4150.toInt()
            downloading || requested -> palette.tertiary
            else -> palette.primary
        }

        val badge = TextView(context).apply {
            text = badgeText
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTextColor(if (isPlex) Color.BLACK else Color.WHITE)
            val hPad = (8 * density).toInt()
            val vPad = (3 * density).toInt()
            setPadding(hPad, vPad, hPad, vPad)
            background = GradientDrawable().apply {
                setColor(badgeColor)
                cornerRadius = 6 * density
            }
            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = (8 * density).toInt()
            }
            layoutParams = lp
        }
        meta.addView(badge)
        addView(meta)
    }
}
