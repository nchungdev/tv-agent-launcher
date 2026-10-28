package com.gathin.tvhome.feature.watching.data

import android.content.Context
import org.json.JSONArray

class LocalWatchHistory(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun record(input: WatchEvent) {
        val event = input.copy(
            packageName = input.packageName.take(256), appLabel = input.appLabel.take(256),
            title = input.title.take(512), subtitle = input.subtitle.take(1024),
            mediaId = input.mediaId.take(2048),
            artworkUrl = input.artworkUrl.takeIf { it.length <= 2048 }.orEmpty(),
            resumeUri = input.resumeUri.takeIf { it.length <= 2048 }.orEmpty(),
            details = input.details.copy(channel = input.details.channel.take(512))
        )
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
        if (raw.length > 512 * 1024) return emptyList()
        val rows = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until minOf(rows.length(), MAX_ENTRIES)).mapNotNull { index ->
            rows.optJSONObject(index)?.let { WatchEvent.fromJson(it) }
        }
    }

    private fun keepPosition(event: WatchEvent, earlier: WatchEvent?): WatchEvent {
        if (earlier == null) return event
        return event.copy(
            positionMs = if (event.positionMs > 0) event.positionMs else earlier.positionMs,
            durationMs = if (event.positionMs > 0) event.durationMs else
                maxOf(event.durationMs, earlier.durationMs),
            resumeUri = event.resumeUri.ifBlank { earlier.resumeUri },
            artworkUrl = event.artworkUrl.ifBlank { earlier.artworkUrl }
        )
    }

    fun continueItems(): List<WatchItem> = load().map { toItem(it) }

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
            remainingMs = (event.durationMs - event.positionMs).coerceAtLeast(0L),
            contentId = event.mediaId,
            updatedAt = event.at,
            deepLink = event.resumeUri,
            durationMs = event.durationMs,
            details = event.details
        )
    }

    private companion object {
        const val PREFS = "watch_history"
        const val KEY = "events"
        const val MAX_ENTRIES = 20
    }
}
