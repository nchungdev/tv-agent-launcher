package com.gathin.tvhome.feature.playback

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

class PlaybackControls(context: Context) : LinearLayout(context) {
    val title = label(20f)
    val status = label(14f)
    val timeline = SeekBar(context)
    val buttons = LinearLayout(context).apply { gravity = Gravity.CENTER }
    val pause: TextView

    init {
        orientation = VERTICAL
        val pad = (24 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad / 2, pad, pad / 2)
        setBackgroundColor(0xDD10131B.toInt())
        addView(title)
        addView(status)
        addView(timeline, LayoutParams(-1, -2))
        addView(buttons)
        pause = button("⏯") {}
    }

    fun button(label: String, action: () -> Unit): TextView {
        val density = resources.displayMetrics.density
        val view = label(15f).apply {
            text = label
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            setPadding((12 * density).toInt(), (10 * density).toInt(),
                (12 * density).toInt(), (10 * density).toInt())
            setOnClickListener { action() }
            setOnFocusChangeListener { _, focused ->
                background = GradientDrawable().apply {
                    cornerRadius = 12 * density
                    setColor(if (focused) 0xFF516788.toInt() else Color.TRANSPARENT)
                }
            }
        }
        buttons.addView(view)
        return view
    }

    private fun label(size: Float) = TextView(context).apply {
        textSize = size
        setTextColor(Color.WHITE)
        maxLines = 2
    }

    companion object {
        fun time(millis: Int): String {
            val seconds = millis.coerceAtLeast(0) / 1000
            return "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
        }
    }
}
