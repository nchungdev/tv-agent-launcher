package com.gathin.tvhome.feature.watching.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.tv.TvContract

class TvWatchNextSource(private val context: Context) {
    fun availability(): WatchAvailability = when {
        context.packageManager.resolveContentProvider(TvContract.AUTHORITY, 0) == null ->
            WatchAvailability.UNSUPPORTED
        context.checkSelfPermission("android.permission.READ_TV_LISTINGS") !=
            PackageManager.PERMISSION_GRANTED -> WatchAvailability.PERMISSION_REQUIRED
        else -> WatchAvailability.READY
    }

    fun load(request: WatchRequest): WatchResult {
        val access = availability()
        if (access != WatchAvailability.READY) return WatchResult(access)
        return try {
            val cursor = context.contentResolver.query(TvContract.WatchNextPrograms.CONTENT_URI,
                PROJECTION, null, null,
                "last_engagement_time_utc_millis DESC, _id DESC", request.signal)
                ?: return WatchResult(WatchAvailability.ERROR)
            cursor.use { WatchResult.loaded(read(it)) }
        } catch (_: SecurityException) {
            WatchResult(WatchAvailability.PERMISSION_REQUIRED)
        } catch (_: IllegalArgumentException) {
            WatchResult(WatchAvailability.ERROR)
        }
    }

    private fun read(cursor: Cursor): List<WatchItem> {
        val items = ArrayList<WatchItem>()
        var scanned = 0
        while (scanned++ < MAX_ROWS && cursor.moveToNext()) {
            if (number(cursor, "watch_next_type", -1) !=
                TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE.toLong()) continue
            val packageName = text(cursor, "package_name")
            val title = text(cursor, "title").ifBlank { text(cursor, "episode_title") }
            if (!WatchMerge.validPackage(packageName) || title.isBlank()) continue
            val position = number(cursor, "last_playback_position_millis").coerceAtLeast(0)
            val duration = number(cursor, "duration_millis").coerceAtLeast(0)
            val episode = text(cursor, "episode_title")
            items.add(WatchItem(
                title, episode.takeIf { it != title }.orEmpty().ifBlank {
                    text(cursor, "short_description").ifBlank { text(cursor, "long_description") }
                },
                if (duration > 0) WatchMerge.progress(position.toFloat() / duration) else 0f,
                text(cursor, "poster_art_uri").ifBlank { text(cursor, "thumbnail_uri") }
                    .takeIf { it.isNotBlank() },
                packageName, packageName, "app.open", mapOf("package_name" to packageName),
                (duration - position).coerceAtLeast(0), WatchSource.TV,
                text(cursor, "_id"), number(cursor, "last_engagement_time_utc_millis"),
                text(cursor, "intent_uri"), durationMs = duration
            ))
        }
        return items
    }

    private fun text(cursor: Cursor, column: String): String {
        val index = cursor.getColumnIndex(column)
        return if (index < 0 || cursor.isNull(index)) "" else
            cursor.getString(index).orEmpty().take(MAX_TEXT)
    }

    private fun number(cursor: Cursor, column: String, fallback: Long = 0): Long {
        val index = cursor.getColumnIndex(column)
        return if (index < 0 || cursor.isNull(index)) fallback else
            runCatching { cursor.getLong(index) }.getOrDefault(fallback)
    }

    companion object {
        const val MAX_ROWS = 512
        private const val MAX_TEXT = 8192
        private val PROJECTION = arrayOf("_id", "package_name", "title", "episode_title",
            "short_description", "long_description", "watch_next_type", "poster_art_uri",
            "thumbnail_uri", "last_playback_position_millis", "duration_millis", "intent_uri",
            "last_engagement_time_utc_millis")
    }
}
