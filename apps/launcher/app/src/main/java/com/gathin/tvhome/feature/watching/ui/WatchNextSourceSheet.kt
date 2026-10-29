package com.gathin.tvhome.feature.watching.ui

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.Window
import com.gathin.tvhome.core.motion.FocusScrollView
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.gathin.tvhome.R
import com.gathin.tvhome.core.design.M3Skin
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.feature.settings.data.Config
import com.gathin.tvhome.feature.watching.data.ContinueWatching
import com.gathin.tvhome.feature.watching.data.WatchAvailability
import com.gathin.tvhome.feature.watching.data.WatchSource

class WatchNextSourceSheet(
    private val activity: Activity,
    private val config: Config,
    private val feed: ContinueWatching
) {
    private val density = activity.resources.displayMetrics.density
    private val palette = Palette.currentOrBaseline()
    private val rows = ArrayList<TextView>()
    private val listener: () -> Unit = { update() }
    private val dialog = Dialog(activity)
    private val owned = !feed.isRunning()

    fun show() {
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(20))
            background = GradientDrawable().apply {
                setColor(palette.surface)
                cornerRadius = dp(24).toFloat()
            }
        }
        panel.addView(TextView(activity).apply {
            setText(R.string.watch_sources)
            setTextColor(palette.onSurface)
            textSize = 22f
        })
        for (source in WatchSource.entries) {
            val row = TextView(activity).apply {
                textSize = 15f
                setTextColor(palette.onSurface)
                setPadding(dp(12), dp(12), dp(12), dp(12))
                isFocusable = true
                isClickable = true
                tag = "m3:listitem"
                setOnClickListener { configure(source) }
            }
            rows.add(row)
            panel.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
        panel.addView(TextView(activity).apply {
            setText(R.string.watch_filter_hint)
            setTextColor(palette.onSurfaceVariant)
            textSize = 12f
            setPadding(dp(12), dp(12), dp(12), dp(12))
        })
        val refresh = TextView(activity).apply {
            setText(R.string.watch_refresh)
            setTextColor(palette.onSurface)
            textSize = 16f
            setPadding(dp(12), dp(12), dp(12), dp(12))
            isFocusable = true
            isClickable = true
            tag = "m3:listitem"
            setOnClickListener { feed.refresh() }
        }
        panel.addView(refresh)
        M3Skin.apply(panel, palette, config.animations, config.reducedMotion)
        val scroll = FocusScrollView(activity).apply {
            setMotion(config.animations, config.reducedMotion)
            addView(panel)
        }
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(scroll)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.45f; gravity = Gravity.CENTER }
        }
        dialog.setOnDismissListener {
            feed.removeListener(listener)
            if (owned) feed.stop()
        }
        feed.addListener(listener)
        if (owned) feed.start {} else feed.refresh()
        update()
        dialog.show()
        dialog.window?.setLayout(minOf(dp(440), activity.resources.displayMetrics.widthPixels),
            minOf(dp(480), activity.resources.displayMetrics.heightPixels - dp(48)))
        refresh.requestFocus()
    }

    private fun update() {
        if (activity.isDestroyed) return
        for ((index, source) in WatchSource.entries.withIndex()) {
            val name = activity.getString(when (source) {
                WatchSource.TV -> R.string.watch_source_tv
                WatchSource.SERVER -> R.string.watch_source_server
                WatchSource.LOCAL -> R.string.watch_source_local
            })
            val status = activity.getString(when (feed.state(source)) {
                WatchAvailability.READY -> R.string.watch_ready
                WatchAvailability.EMPTY -> R.string.watch_empty
                WatchAvailability.PERMISSION_REQUIRED -> R.string.watch_permission
                WatchAvailability.ERROR -> R.string.watch_error
                WatchAvailability.UNSUPPORTED -> if (source == WatchSource.SERVER)
                    R.string.watch_not_configured else R.string.watch_unsupported
            })
            rows[index].text = "$name\n$status · ${feed.count(source)}"
        }
    }

    private fun configure(source: WatchSource) {
        if (source != WatchSource.LOCAL &&
            feed.state(source) != WatchAvailability.PERMISSION_REQUIRED) return
        dialog.dismiss()
        if (source == WatchSource.TV) {
            activity.requestPermissions(arrayOf("android.permission.READ_TV_LISTINGS"), REQUEST_CODE)
        } else if (source == WatchSource.LOCAL) {
            val listener = ComponentName(activity.packageName, "com.gathin.tvhome.NowPlayingListener")
            val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    listener.flattenToString())
            runCatching { activity.startActivity(detail) }.recoverCatching {
                activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
    }

    private fun dp(value: Int) = (value * density).toInt()

    companion object { const val REQUEST_CODE = 4102 }
}
