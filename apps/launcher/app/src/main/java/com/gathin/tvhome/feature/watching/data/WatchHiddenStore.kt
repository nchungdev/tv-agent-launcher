package com.gathin.tvhome.feature.watching.data

import android.content.Context

class WatchHiddenStore(context: Context) {
    private val prefs = context.getSharedPreferences("watch_hidden", Context.MODE_PRIVATE)

    fun load(): Set<String> = prefs.getStringSet("items", emptySet()).orEmpty().toSet()

    fun hide(item: WatchItem) {
        val keys = load() + item.keys()
        prefs.edit().putStringSet("items", keys.takeLastBounded()).apply()
    }

    private fun Set<String>.takeLastBounded(): Set<String> = toList().takeLast(512).toSet()
}
