package com.gathin.tvhome.feature.playback

import android.app.Activity
import android.app.AlertDialog
import android.widget.CheckBox
import com.gathin.tvhome.R

object PlaybackChoices {
    fun choose(activity: Activity, source: String, selected: (String) -> Unit) {
        val store = PlaybackStore(activity)
        store.remember(source)
        val remember = CheckBox(activity).apply { setText(R.string.playback_remember) }
        val labels = arrayOf(activity.getString(R.string.playback_launcher),
            activity.getString(R.string.playback_app))
        PlaybackPrompts.show(activity, AlertDialog.Builder(activity)
            .setTitle(source).setView(remember)
            .setItems(labels) { _, index ->
                val player = if (index == 0) "launcher" else "app"
                if (remember.isChecked) store.prefer(source, player)
                selected(player)
            }.setNegativeButton(android.R.string.cancel, null))
    }

    fun settings(activity: Activity) {
        val store = PlaybackStore(activity)
        val sources = store.sources().sorted()
        val labels = sources.map { source ->
            val choice = when (store.preferred(source)) {
                "launcher" -> activity.getString(R.string.playback_launcher)
                "app" -> activity.getString(R.string.playback_app)
                else -> activity.getString(R.string.playback_ask)
            }
            "$source · $choice"
        }
        PlaybackPrompts.show(activity, AlertDialog.Builder(activity)
            .setTitle(R.string.playback_settings)
            .setItems(labels.toTypedArray()) { _, index ->
                val source = sources[index]
                val players = arrayOf(activity.getString(R.string.playback_ask),
                    activity.getString(R.string.playback_launcher),
                    activity.getString(R.string.playback_app))
                PlaybackPrompts.show(activity, AlertDialog.Builder(activity).setTitle(source)
                    .setItems(players) { _, choice ->
                        store.prefer(source, listOf("", "launcher", "app")[choice])
                    })
            }.setMessage(if (sources.isEmpty()) activity.getString(R.string.playback_no_sources)
                else null).setNegativeButton(android.R.string.cancel, null))
    }
}
