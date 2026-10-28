package com.gathin.tvhome.feature.watching.ui

import android.app.Activity
import android.text.TextUtils
import android.widget.LinearLayout
import android.widget.TextView
import com.gathin.tvhome.R
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.core.motion.FocusScrollView
import com.gathin.tvhome.feature.settings.data.Config
import com.gathin.tvhome.feature.settings.ui.SettingsChoiceDialog
import com.gathin.tvhome.feature.watching.data.ContinueWatching
import com.gathin.tvhome.feature.watching.data.WatchItem

object WatchNextMenu {
    fun show(
        activity: Activity, config: Config, feed: ContinueWatching,
        focused: WatchItem?, beforeHide: () -> Unit
    ) {
        val entries = (listOfNotNull(focused) + feed.items).distinctBy { it.keys() }
        val palette = Palette.currentOrBaseline()
        val density = activity.resources.displayMetrics.density
        val panel = SettingsChoiceDialog.dialogPanel(activity, R.string.widget_continue,
            palette, density)
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val scroll = FocusScrollView(activity).apply {
            setMotion(config.animations, config.reducedMotion)
            addView(list)
        }
        val rowHeight = (60 * density).toInt()
        val height = minOf((entries.size + 1) * rowHeight,
            activity.resources.displayMetrics.heightPixels - (160 * density).toInt())
        panel.addView(scroll, LinearLayout.LayoutParams(-1, height.coerceAtLeast(rowHeight)))
        var dialog: android.app.Dialog? = null
        for (index in 0..entries.size) {
            val item = entries.getOrNull(index)
            val row = TextView(activity).apply {
                text = if (item == null) activity.getString(R.string.watch_sources) else
                    SettingsChoiceDialog.item(activity, item.title, item.appLabel)
                textSize = 15f
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(palette.onSurface)
                setPadding((12 * density).toInt(), (8 * density).toInt(),
                    (12 * density).toInt(), (8 * density).toInt())
                isFocusable = true
                isClickable = true
                tag = "m3:listitem"
                setOnClickListener {
                    dialog?.dismiss()
                    if (item == null) WatchNextSourceSheet(activity, config, feed).show()
                    else itemActions(activity, config, feed, item, beforeHide)
                }
            }
            list.addView(row, LinearLayout.LayoutParams(-1, rowHeight))
        }
        dialog = SettingsChoiceDialog.showDialog(activity, panel, palette, config, density)
        list.getChildAt(0)?.requestFocus()
    }

    private fun itemActions(
        activity: Activity, config: Config, feed: ContinueWatching,
        item: WatchItem, beforeHide: () -> Unit
    ) {
        val labels = arrayOf<CharSequence>(activity.getString(R.string.watch_resume),
            activity.getString(R.string.watch_hide))
        SettingsChoiceDialog.chooseOne(activity, R.string.widget_continue, labels, null, 0,
            { index ->
                if (index == 0) WatchLauncher.open(activity, item) else {
                    beforeHide()
                    feed.hide(item)
                }
            }, config)
    }
}
