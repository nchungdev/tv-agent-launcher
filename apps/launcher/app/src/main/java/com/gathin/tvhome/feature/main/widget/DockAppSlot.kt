package com.gathin.tvhome.feature.main.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.gathin.tvhome.R
import com.gathin.tvhome.core.design.DockIconNormalizer
import com.gathin.tvhome.core.design.Palette

class DockAppSlot(context: Context, icon: Drawable?, private val allApps: Boolean) :
    FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private val fill = GradientDrawable().apply { cornerRadius = dp(16).toFloat() }
    private val border = GradientDrawable().apply {
        cornerRadius = dp(16).toFloat()
        setColor(Color.TRANSPARENT)
    }
    private val normalized = if (allApps) null else DockIconNormalizer.normalize(resources, icon)
    private val fullBleed = normalized?.fullBleed == true
    private val edgeColor = if (fullBleed) normalized?.edgeColor ?: Color.TRANSPARENT
        else Color.TRANSPARENT
    private val image = ImageView(context)
    private val runningDot = View(context)

    init {
        layoutParams = LinearLayout.LayoutParams(dp(58), dp(58))
        background = fill
        foreground = border
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        round(this, dp(16).toFloat())

        val iconSize = if (allApps) dp(28) else if (fullBleed) dp(50) else dp(38)
        val radius = if (fullBleed) dp(13).toFloat() else dp(10).toFloat()
        val iconCard = FrameLayout(context)
        if (fullBleed && edgeColor != Color.TRANSPARENT) {
            iconCard.background = GradientDrawable().apply {
                cornerRadius = radius
                setColor(edgeColor)
            }
        }
        round(iconCard, radius)
        image.scaleType = if (fullBleed) ImageView.ScaleType.CENTER_CROP
            else ImageView.ScaleType.FIT_CENTER
        image.setImageDrawable(
            normalized?.drawable ?: icon ?: context.getDrawable(R.drawable.ic_apps)
        )
        iconCard.addView(image, LayoutParams(-1, -1))
        addView(iconCard, LayoutParams(iconSize, iconSize, Gravity.CENTER))

        runningDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFF22C55E.toInt())
            setStroke(dp(1), 0xCC000000.toInt())
        }
        runningDot.visibility = View.GONE
        addView(runningDot, LayoutParams(dp(8), dp(8), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(4)
            marginEnd = dp(4)
        })
    }

    fun setRunning(running: Boolean) {
        runningDot.visibility = if (running) View.VISIBLE else View.GONE
    }

    fun setPalette(palette: Palette) {
        val color = if (allApps) palette.secondaryContainer
        else if (edgeColor != Color.TRANSPARENT) {
            Palette.alpha(palette.surfaceContainerHighest, 0x55)
        } else palette.surfaceContainerHighest
        fill.setColor(color)
        val stroke = if (isFocused) palette.primary else Palette.alpha(palette.outlineVariant, 0x45)
        border.setStroke(if (isFocused) dp(2.5f) else dp(1), stroke)
        if (allApps) {
            image.imageTintList = ColorStateList.valueOf(
                if (isFocused) palette.primary else palette.onSecondaryContainer
            )
        }
    }

    private fun round(view: View, radius: Float) {
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        view.clipToOutline = true
    }

    private fun dp(value: Number): Int = Math.round(value.toFloat() * density)
}
