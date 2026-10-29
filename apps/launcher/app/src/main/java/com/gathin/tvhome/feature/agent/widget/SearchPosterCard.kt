package com.gathin.tvhome.feature.agent.widget

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.core.util.ImageLoader
import com.gathin.tvhome.core.view.AgentCardData
import com.gathin.tvhome.feature.agent.coordinator.AgentCardActionHandler

class SearchPosterCard(context: Context, private val card: AgentCardData) :
    LinearLayout(context) {

    private val density = context.resources.displayMetrics.density
    private val palette = Palette.dark(Palette.currentOrBaseline())
    private val poster = ImageView(context)
    private val fallback = TextView(context)
    private val title = TextView(context)
    private val meta = TextView(context)

    init {
        orientation = VERTICAL
        isFocusable = true
        isFocusableInTouchMode = true
        clipChildren = false
        clipToPadding = false
        setPadding(dp(6), dp(6), dp(6), dp(8))
        background = backgroundDrawable(false)

        val landscape = card.sourceId.equals("youtube", ignoreCase = true) ||
            card.type.equals("video", ignoreCase = true)
        val posterWidth = if (landscape) 168 else 112
        val posterHeight = if (landscape) 94 else 168

        layoutParams = LayoutParams(dp(posterWidth + 12), LayoutParams.WRAP_CONTENT)
        contentDescription = card.type

        val imageFrame = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            clipChildren = true
            background = GradientDrawable().apply {
                setColor(0xFF20252E.toInt())
                cornerRadius = dp(8).toFloat()
            }
        }
        fallback.apply {
            text = card.title.take(1).uppercase()
            setTextColor(Color.WHITE)
            textSize = if (landscape) 26f else 34f
            gravity = Gravity.CENTER
        }
        imageFrame.addView(
            fallback, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        poster.scaleType = ImageView.ScaleType.CENTER_CROP
        imageFrame.addView(
            poster, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        addView(imageFrame, LayoutParams(LayoutParams.MATCH_PARENT, dp(posterHeight)))

        title.apply {
            text = card.title
            setTextColor(palette.onSurface)
            textSize = 12f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(2), dp(6), dp(2), 0)
        }
        addView(title, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        meta.apply {
            text = card.year.takeIf(String::isNotBlank)
                ?: card.type.replace('_', ' ').replaceFirstChar { it.uppercaseChar() }
            setTextColor(palette.onSurfaceVariant)
            textSize = 10f
            maxLines = 1
            setPadding(dp(2), dp(2), dp(2), 0)
        }
        addView(meta, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        ImageLoader.load(card.posterUrl, poster, dp(posterWidth), dp(posterHeight))
        setOnFocusChangeListener { _, focused ->
            background = backgroundDrawable(focused)
            title.setTextColor(if (focused) Color.WHITE else palette.onSurface)
            meta.setTextColor(if (focused) Color.WHITE else palette.onSurfaceVariant)
        }
        setOnClickListener {
            AgentCardActionHandler.handleCardAction(context, card)
        }
    }

    private fun backgroundDrawable(focused: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(10).toFloat()
        setColor(if (focused) 0xFF303A49.toInt() else 0xFF171B22.toInt())
        setStroke(dp(if (focused) 2 else 1), if (focused) palette.primary else 0x337F8A9B)
    }

    private fun dp(value: Int): Int = Math.round(value * density)
}
