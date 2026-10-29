package com.gathin.tvhome.feature.player

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.gathin.tvhome.R
import com.gathin.tvhome.core.design.Fonts
import com.gathin.tvhome.core.design.Palette

class PlayerOverlay(context: Context) : FrameLayout(context) {

    private val density = context.resources.displayMetrics.density
    private val primary = Palette.dark(Palette.currentOrBaseline()).primary
    private val panel = LinearLayout(context)
    private val title = text(24f, 0xFFFFFFFF.toInt(), Fonts.MEDIUM)
    private val subtitle = text(15f, 0xCCFFFFFF.toInt(), Fonts.REGULAR)
    private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
    private val time = text(14f, 0xCCFFFFFF.toInt(), Fonts.REGULAR)
    private val hint = text(13f, 0x99FFFFFF.toInt(), Fonts.REGULAR)
    private val spinner = ProgressBar(context)
    private val message = text(18f, 0xFFFFFFFF.toInt(), Fonts.MEDIUM)
    private val paused = text(56f, 0xE6FFFFFF.toInt(), Fonts.MEDIUM)

    init {
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(dp(56), dp(80), dp(56), dp(36))
        panel.background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
            intArrayOf(0xE6000000.toInt(), 0x99000000.toInt(), 0x00000000))
        panel.addView(title)
        panel.addView(subtitle, wrap().apply { topMargin = dp(4) })
        progress.max = PROGRESS_MAX
        progress.progressTintList = ColorStateList.valueOf(primary)
        progress.progressBackgroundTintList = ColorStateList.valueOf(0x40FFFFFF)
        panel.addView(progress, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(6)).apply { topMargin = dp(18) })
        val row = LinearLayout(context)
        row.addView(time, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(hint)
        hint.setText(R.string.player_hint)
        panel.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) })
        addView(panel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM))

        spinner.indeterminateTintList = ColorStateList.valueOf(primary)
        addView(spinner, LayoutParams(dp(56), dp(56), Gravity.CENTER))
        message.gravity = Gravity.CENTER
        message.visibility = GONE
        addView(message, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
            Gravity.CENTER))
        paused.text = "❚❚"
        paused.visibility = GONE
        addView(paused, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
            Gravity.CENTER))
    }

    val panelShown: Boolean get() = panel.visibility == VISIBLE

    fun setTitles(main: String, detail: String) {
        title.text = main
        subtitle.text = detail
        subtitle.visibility = if (detail.isBlank()) GONE else VISIBLE
    }

    fun showPanel(shown: Boolean) {
        panel.animate().cancel()
        if (shown) {
            panel.visibility = VISIBLE
            panel.animate().alpha(1f).setDuration(FADE_MS).start()
        } else {
            panel.animate().alpha(0f).setDuration(FADE_MS)
                .withEndAction { panel.visibility = GONE }.start()
        }
    }

    fun update(positionMs: Long, durationMs: Long, playing: Boolean) {
        progress.progress = if (durationMs > 0) {
            (positionMs * PROGRESS_MAX / durationMs).toInt()
        } else 0
        time.text = if (durationMs > 0) "${clock(positionMs)} / ${clock(durationMs)}"
        else clock(positionMs)
        paused.visibility = if (!playing && spinner.visibility != VISIBLE &&
            message.visibility != VISIBLE) VISIBLE else GONE
    }

    fun loading(shown: Boolean) {
        spinner.visibility = if (shown) VISIBLE else GONE
        if (shown) message.visibility = GONE
    }

    fun error(text: String?) {
        message.text = text.orEmpty()
        message.visibility = if (text.isNullOrBlank()) GONE else VISIBLE
        if (!text.isNullOrBlank()) spinner.visibility = GONE
    }

    private fun clock(ms: Long): String {
        val total = ms / 1000
        val hours = total / 3600
        val minutes = total % 3600 / 60
        val seconds = total % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
        else "%d:%02d".format(minutes, seconds)
    }

    private fun text(size: Float, color: Int, font: android.graphics.Typeface) =
        TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            setTextColor(color)
            typeface = font
        }

    private fun wrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int = Math.round(value * density)

    private companion object {
        const val PROGRESS_MAX = 1000
        const val FADE_MS = 200L
    }
}
