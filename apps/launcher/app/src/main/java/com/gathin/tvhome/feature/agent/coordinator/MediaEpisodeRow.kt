package com.gathin.tvhome.feature.agent.coordinator

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.gathin.tvhome.R
import com.gathin.tvhome.core.design.Fonts
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.feature.agent.data.CatalogEpisode

class MediaEpisodeRow(context: Context, episode: CatalogEpisode, series: Boolean) :
    LinearLayout(context) {
    init {
        val palette = Palette.currentOrBaseline()
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(6), dp(16), dp(6))
        isFocusable = episode.playable
        isClickable = episode.playable
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply {
                setColor(palette.surfaceContainerHighest)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(2), palette.primary)
            })
            addState(intArrayOf(), GradientDrawable().apply {
                setColor(Palette.alpha(palette.onSurface, 9))
                cornerRadius = dp(12).toFloat()
            })
        }
        if (series && episode.order > 0) addView(TextView(context).apply {
            text = episode.order.toString().padStart(2, '0')
            textSize = 13f
            typeface = Fonts.MEDIUM
            gravity = Gravity.CENTER
            setTextColor(palette.onSurfaceVariant)
        }, LayoutParams(dp(34), -1))
        val information = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(if (series) dp(10) else 0, 0, 0, 0)
        }
        information.addView(TextView(context).apply {
            text = episode.label
            textSize = 14f
            typeface = Fonts.MEDIUM
            maxLines = if (series) 2 else 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(palette.onSurface)
        })
        if (episode.group.isNotBlank()) information.addView(TextView(context).apply {
            text = episode.group
            textSize = 11f
            maxLines = 1
            setTextColor(palette.onSurfaceVariant)
        })
        addView(information, LayoutParams(0, -2, 1f))
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_media_play)
            imageTintList = android.content.res.ColorStateList.valueOf(palette.onSurfaceVariant)
        }, LayoutParams(dp(18), dp(18)))
        alpha = if (episode.playable) 1f else 0.4f
        layoutParams = LayoutParams(-1, dp(48)).apply { bottomMargin = dp(6) }
    }
}
