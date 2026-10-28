package com.gathin.tvhome.feature.watching.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import com.gathin.tvhome.R
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.feature.watching.data.WatchItem

class DockWatchNextView(context: Context) : FrameLayout(context) {
    private data class Content(val item: WatchItem?, val poster: Bitmap?, val open: () -> Unit)

    private val density = resources.displayMetrics.density
    private val fill = GradientDrawable().apply { cornerRadius = dp(14).toFloat() }
    private val border = GradientDrawable().apply { cornerRadius = dp(14).toFloat() }
    private val image = ImageView(context)
    private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
    private var pending: Content? = null
    private var current: Content? = null
    private var palette: Palette = Palette.currentOrBaseline()

    init {
        layoutParams = LinearLayout.LayoutParams(dp(96), dp(58))
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        visibility = View.GONE
        border.setColor(Color.TRANSPARENT)
        background = fill
        foreground = border
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dp(14).toFloat())
            }
        }
        clipToOutline = true
        addView(image, LayoutParams(-1, -1))
        progress.max = 1000
        addView(progress, LayoutParams(-1, dp(3.5f), Gravity.BOTTOM))
        setOnClickListener { current?.open?.invoke() }
        setPalette(palette)
    }

    val boundItem: WatchItem? get() = current?.item

    fun bind(item: WatchItem?, poster: Bitmap?, open: () -> Unit) {
        val content = Content(item, poster, open)
        if (hasFocus()) {
            pending = content
            return
        }
        current = content
        visibility = if (item == null) View.GONE else View.VISIBLE
        if (item == null) {
            image.setImageDrawable(null)
            return
        }

        val remaining = WatchFormat.remaining(context, item.remainingMs)
        contentDescription = listOfNotNull(
            context.getString(R.string.widget_continue), item.title,
            item.appLabel.takeIf { it.isNotBlank() }, remaining
        ).joinToString(" · ")
        if (poster != null) {
            image.scaleType = ImageView.ScaleType.CENTER_CROP
            image.setImageBitmap(poster)
        } else {
            image.scaleType = ImageView.ScaleType.FIT_CENTER
            image.setImageDrawable(WatchFormat.appArt(context, item.packageName)
                ?: context.getDrawable(R.drawable.ic_videocam))
        }
        progress.progress = (item.progress.coerceIn(0f, 1f) * 1000).toInt()
        progress.visibility = if (item.progress > 0f) View.VISIBLE else View.INVISIBLE
    }

    fun releasePending() {
        val content = pending ?: return
        pending = null
        bind(content.item, content.poster, content.open)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        setPalette(palette)
    }

    fun setPalette(newPalette: Palette) {
        palette = newPalette
        val darkP = Palette.dark(palette)
        fill.setColor(Palette.alpha(darkP.surfaceContainerHighest, 0xD9))
        val focused = hasFocus() || isFocused
        border.setStroke(
            dp(if (focused) 2.5f else 1f),
            if (focused) darkP.primary else Palette.alpha(darkP.outlineVariant, 0x55)
        )
        progress.progressTintList = ColorStateList.valueOf(darkP.primary)
        progress.progressBackgroundTintList = ColorStateList.valueOf(Palette.alpha(darkP.onSurfaceVariant, 0x40))
    }

    private fun dp(value: Number): Int = Math.round(value.toFloat() * density)
}
