package com.gathin.tvhome.feature.playback

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.gathin.tvhome.core.design.Fonts
import com.gathin.tvhome.core.design.Palette

class PlaybackControls(context: Context) : LinearLayout(context) {
    val title = label(22f, Fonts.MEDIUM)
    val status = label(14f, Fonts.REGULAR)
    val timeline = SeekBar(context)
    val buttons = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        clipChildren = false
        clipToPadding = false
    }
    val pause: TextView

    init {
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false
        val density = resources.displayMetrics.density
        val padH = (36 * density).toInt()
        val padV = (24 * density).toInt()
        setPadding(padH, padV, padH, padV)
        background = GradientDrawable(
            GradientDrawable.Orientation.BOTTOM_TOP,
            intArrayOf(0xFA0F172A.toInt(), 0xD00F172A.toInt(), 0x000F172A)
        )

        status.setTextColor(0xFF94A3B8.toInt())
        val primary = Palette.dark(Palette.currentOrBaseline()).primary

        timeline.progressTintList = ColorStateList.valueOf(primary)
        timeline.progressBackgroundTintList = ColorStateList.valueOf(0x33FFFFFF)
        timeline.thumbTintList = ColorStateList.valueOf(primary)

        addView(title)
        addView(status, LayoutParams(-1, -2).apply { topMargin = (4 * density).toInt() })
        addView(timeline, LayoutParams(-1, -2).apply {
            topMargin = (16 * density).toInt()
            bottomMargin = (12 * density).toInt()
        })
        addView(buttons)
        pause = button("⏯") {}
    }

    fun button(label: String, action: () -> Unit): TextView {
        val density = resources.displayMetrics.density
        val primary = Palette.dark(Palette.currentOrBaseline()).primary
        val view = label(14f, Fonts.MEDIUM).apply {
            text = label
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            val padH = (14 * density).toInt()
            val padV = (8 * density).toInt()
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply {
                cornerRadius = 12 * density
                setColor(0x22FFFFFF)
            }
            setOnClickListener { action() }
            setOnFocusChangeListener { _, focused ->
                background = GradientDrawable().apply {
                    cornerRadius = 12 * density
                    setColor(if (focused) primary else 0x22FFFFFF)
                }
                setTextColor(if (focused) Color.WHITE else 0xE6FFFFFF.toInt())
                animate().scaleX(if (focused) 1.05f else 1f)
                    .scaleY(if (focused) 1.05f else 1f).setDuration(150).start()
            }
        }
        val params = LayoutParams(-2, -2).apply {
            marginEnd = (8 * density).toInt()
        }
        buttons.addView(view, params)
        return view
    }

    private fun label(sizeSp: Float, font: Typeface) =
        TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(Color.WHITE)
            typeface = font
            maxLines = 2
        }

    companion object {
        fun time(millis: Int): String {
            val seconds = millis.coerceAtLeast(0) / 1000
            val h = seconds / 3600
            val m = seconds % 3600 / 60
            val s = seconds % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
        }
    }
}
