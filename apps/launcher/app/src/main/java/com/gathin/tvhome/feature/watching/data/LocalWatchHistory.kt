package com.gathin.tvhome.feature.watching.data

import android.content.Context
import org.json.JSONArray

class LocalWatchHistory(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun record(event: WatchEvent) {
        val history = load()
        val earlier = history.firstOrNull { it.key == event.key }
        val kept = history.filter { it.key != event.key }.take(MAX_ENTRIES - 1)
        val rows = JSONArray()
        rows.put(keepPosition(event, earlier).toJson())
        for (entry in kept) rows.put(entry.toJson())
        prefs.edit().putString(KEY, rows.toString()).apply()
    }

    @Synchronized
    fun load(): List<WatchEvent> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val rows = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until rows.length()).mapNotNull { index ->
            rows.optJSONObject(index)?.let { WatchEvent.fromJson(it) }
        }
    }

    private fun keepPosition(event: WatchEvent, earlier: WatchEvent?): WatchEvent {
        if (earlier == null || event.positionMs > 0 || earlier.positionMs <= 0) return event
        return event.copy(
            positionMs = earlier.positionMs,
            durationMs = maxOf(event.durationMs, earlier.durationMs)
        )
    }

    fun continueItems(): List<WatchItem> {
        val since = System.currentTimeMillis() - KEEP_MS
        return load()
            .filter { it.at >= since && unfinished(it) }
            .take(MAX_ITEMS)
            .map { toItem(it) }
    }

    private fun unfinished(event: WatchEvent): Boolean {
        if (event.durationMs <= 0) return true
        val watched = event.positionMs.toFloat() / event.durationMs
        return watched in MIN_PROGRESS..MAX_PROGRESS
    }

    private fun toItem(event: WatchEvent): WatchItem {
        val progress = if (event.durationMs > 0) {
            (event.positionMs.toFloat() / event.durationMs).coerceIn(0f, 1f)
        } else 0f
        return WatchItem(
            title = event.title,
            subtitle = event.subtitle,
            progress = progress,
            posterUri = event.artworkUrl.ifBlank { null },
            appLabel = event.appLabel,
            packageName = event.packageName,
            actionType = "app.open",
            parameters = mapOf("package_name" to event.packageName),
            remainingMs = (event.durationMs - event.positionMs).coerceAtLeast(0L)
        )
    }

    private companion object {
        const val PREFS = "watch_history"
        const val KEY = "events"
        const val MAX_ENTRIES = 20
        const val MAX_ITEMS = 8
        const val KEEP_MS = 14L * 24 * 60 * 60 * 1000
        const val MIN_PROGRESS = 0.03f
        const val MAX_PROGRESS = 0.95f
    }
}
