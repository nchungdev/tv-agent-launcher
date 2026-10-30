package com.gathin.tvhome.feature.agent.coordinator

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.gathin.tvhome.R
import com.gathin.tvhome.core.design.Fonts
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.core.util.ImageLoader
import com.gathin.tvhome.core.view.AgentCardData

class MediaDetailView(
    private val activity: Activity,
    card: AgentCardData,
    val compact: Boolean = false
) : LinearLayout(activity) {
    private val density = resources.displayMetrics.density
    private val palette = Palette.currentOrBaseline()
    private val poster: ImageView
    private val hero: LinearLayout
    private val header = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
    val back = MediaActionView(activity, "").apply {
        contentDescription = activity.getString(R.string.media_back)
        tag = "media-detail-back"
        setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_arrow_back, 0, 0, 0)
        compoundDrawablesRelative[0]?.mutate()?.setTint(palette.onSurface)
        setPadding(dp(8), dp(8), dp(8), dp(8))
        layoutParams = LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(12) }
    }
    val actions = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
    val content = LinearLayout(activity).apply { orientation = VERTICAL }
    val title = text(card.title, 28f).apply {
        typeface = Fonts.MEDIUM
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    val metadata = text(listOf(card.year,
        card.type.replace('_', ' ').replaceFirstChar { it.uppercase() }, card.sourceId,
        card.season.takeIf { it > 0 }?.let { "S$it" }.orEmpty(),
        card.episode.takeIf { it > 0 }?.let { "E$it" }.orEmpty())
        .filter { it.isNotBlank() }.joinToString("  ·  "), 13f).apply {
        setTextColor(palette.onSurfaceVariant)
        setPadding(0, dp(6), 0, 0)
    }
    val synopsis = text(card.summary.take(5000), 14f)

    init {
        clipChildren = false
        clipToPadding = false
        actions.clipChildren = false
        actions.clipToPadding = false
        content.clipChildren = false
        content.clipToPadding = false
        orientation = VERTICAL
        setPadding(dp(40), dp(18), dp(40), dp(20))
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(
            palette.surfaceContainerHigh, palette.surface, palette.surface))
        val headerTitle = if (compact) {
            val sourceName = card.sourceId.uppercase()
            if (card.title.isNotBlank()) "$sourceName  ·  ${card.title}" else sourceName
        } else card.sourceId.uppercase()

        val source = text(headerTitle, if (compact) 16f else 11f).apply {
            if (!compact) letterSpacing = 0.18f
            typeface = Fonts.MEDIUM
            setTextColor(if (compact) palette.onSurface else palette.primary)
        }
        header.addView(back)
        header.addView(source, LayoutParams(0, -2, 1f))
        addView(header, LayoutParams(-1, dp(40)))

        hero = LinearLayout(activity).apply {
            setPadding(0, dp(12), 0, 0)
            visibility = if (compact) GONE else VISIBLE
        }
        poster = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                setColor(palette.surfaceContainerHigh)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), palette.outlineVariant)
            }
            clipToOutline = true
            contentDescription = card.title
        }
        hero.addView(poster, LayoutParams(dp(112), dp(168)))
        if (!compact) ImageLoader.load(card.posterUrl, poster, dp(112), dp(168))
        val information = LinearLayout(activity).apply {
            orientation = VERTICAL
            setPadding(dp(28), 0, dp(28), 0)
        }
        information.addView(title)
        information.addView(metadata)
        synopsis.maxLines = 3
        synopsis.ellipsize = android.text.TextUtils.TruncateAt.END
        synopsis.setTextColor(palette.onSurfaceVariant)
        synopsis.setLineSpacing(dp(3).toFloat(), 1f)
        synopsis.setPadding(0, dp(12), 0, dp(16))
        synopsis.visibility = if (card.summary.isBlank()) GONE else VISIBLE
        if (card.summary.length > 250) {
            synopsis.isFocusable = true
            synopsis.isClickable = true
            synopsis.setOnClickListener {
                synopsis.maxLines = if (synopsis.maxLines == 3) Int.MAX_VALUE else 3
            }
        }
        information.addView(synopsis)
        information.addView(actions)
        hero.addView(information, LayoutParams(0, -2, 1f))
        addView(hero)
        addView(content)
        configureKind(card.type)
    }

    fun configureKind(kind: String) {
        val movie = kind == "movie"
        poster.layoutParams = LayoutParams(dp(if (movie) 168 else 112),
            dp(if (movie) 252 else 168))
        title.textSize = if (movie) 34f else 28f
        synopsis.textSize = if (movie) 15f else 14f
        synopsis.maxLines = if (movie) 5 else 3
    }

    fun action(label: String, click: () -> Unit): MediaActionView {
        val button = MediaActionView(activity, label, actions.childCount == 0).apply {
            setOnClickListener { click() }
        }
        if (label == activity.getString(R.string.media_play)) {
            button.setCompoundDrawablesRelativeWithIntrinsicBounds(
                R.drawable.ic_media_play, 0, 0, 0)
            button.compoundDrawablePadding = dp(8)
            button.compoundDrawablesRelative[0]?.mutate()?.setTint(android.graphics.Color.BLACK)
        }
        actions.addView(button)
        return button
    }

    fun focusInitial() {
        if (compact) {
            (findFirstFocusable(content) ?: back).requestFocus()
        } else {
            (actions.getChildAt(0) ?: back).requestFocus()
        }
    }

    private fun findFirstFocusable(view: android.view.View): android.view.View? {
        if (view.isFocusable && view.visibility == VISIBLE) return view
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = findFirstFocusable(view.getChildAt(i))
                if (child != null) return child
            }
        }
        return null
    }
    fun focusPrimaryIfIdle(primary: MediaActionView) {
        if (back.hasFocus()) primary.requestFocus()
    }

    fun status(label: String): TextView = text(label, 14f).also {
        it.setPadding(0, dp(18), 0, 0)
        it.setTextColor(palette.onSurfaceVariant)
        content.addView(it)
    }

    private fun text(label: String, size: Float) = TextView(activity).apply {
        text = label
        textSize = size
        setTextColor(palette.onSurface)
    }

    private fun dp(value: Int) = (value * density).toInt()
}
