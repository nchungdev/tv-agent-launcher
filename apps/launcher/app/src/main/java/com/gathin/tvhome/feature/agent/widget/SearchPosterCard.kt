package com.gathin.tvhome.feature.agent.widget

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.gathin.tvhome.R
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.core.util.ImageLoader
import com.gathin.tvhome.core.view.AgentCardData
import com.gathin.tvhome.feature.agent.coordinator.AgentCardActionHandler

class SearchPosterCard(context: Context, private val card: AgentCardData) : LinearLayout(context) {

    private val density = context.resources.displayMetrics.density
    private val frame = FrameLayout(context)

    init {
        orientation = VERTICAL
        isFocusable = true
        isClickable = true
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        clipChildren = false

        val palette = Palette.dark(Palette.currentOrBaseline())
        val wide = card.isWeb()
        val width = dp(if (wide) WIDE_WIDTH else POSTER_WIDTH)
        val height = if (wide) width * 9 / 16 else width * 3 / 2

        val poster = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        frame.background = rounded(PLACEHOLDER, 0, 0)
        frame.clipToOutline = true
        frame.addView(poster, FrameLayout.LayoutParams(width, height))
        if (card.posterUrl.isBlank()) frame.addView(initials(), FrameLayout.LayoutParams(width, height))
        else ImageLoader.load(card.posterUrl, poster, width, height)
        addView(frame, LayoutParams(width, height))

        addView(text(card.title, 14f, 0xFFFFFFFF.toInt(), 2).apply {
            layoutParams = LayoutParams(width, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
        })
        val meta = meta()
        if (meta.isNotEmpty()) {
            addView(text(meta, 12f, palette.onSurfaceVariant, 1).apply {
                layoutParams = LayoutParams(width, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2) }
            })
        }

        setOnFocusChangeListener { _, focused ->
            frame.foreground = if (focused) rounded(0, palette.primary, 3) else null
            animate().scaleX(if (focused) FOCUS_SCALE else 1f).scaleY(if (focused) FOCUS_SCALE else 1f)
                .setDuration(150).start()
        }
        setOnClickListener { AgentCardActionHandler.handleCardAction(context, card) }
    }

    private fun meta(): String {
        if (card.isWeb()) return card.type
        val parts = ArrayList<String>()
        if (card.year.isNotBlank()) parts.add(card.year)
        if (card.season > 0) parts.add(context.getString(R.string.watch_season, card.season))
        if (card.episode > 0) parts.add(context.getString(R.string.watch_episode, card.episode))
        return parts.joinToString(" · ")
    }

    private fun initials(): TextView = text(card.title.take(1).uppercase(), 34f,
        0x66FFFFFF, 1).apply { gravity = Gravity.CENTER }

    private fun text(value: String, size: Float, color: Int, lines: Int) = TextView(context).apply {
        text = value
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        maxLines = lines
        ellipsize = TextUtils.TruncateAt.END
    }

    private fun rounded(fill: Int, stroke: Int, strokeDp: Int) = GradientDrawable().apply {
        cornerRadius = dp(CORNER).toFloat()
        setColor(fill)
        if (strokeDp > 0) setStroke(dp(strokeDp), stroke)
    }

    private fun dp(value: Int): Int = Math.round(value * density)

    private companion object {
        const val POSTER_WIDTH = 108
        const val WIDE_WIDTH = 192
        const val CORNER = 10
        const val FOCUS_SCALE = 1.06f
        const val PLACEHOLDER = 0xFF202532.toInt()
    }
}
