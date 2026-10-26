package com.gathin.tvhome.feature.watching.data

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.tv.TvContract

class WatchNextSource(private val context: Context) {

    fun load(): List<WatchItem> {
        val items = ArrayList<WatchItem>()
        val cursor = query() ?: return items
        cursor.use {
            while (it.moveToNext() && items.size < MAX_ITEMS) {
                val item = read(it) ?: continue
                items.add(item)
            }
        }
        return items
    }

    private fun query(): Cursor? {
        return try {
            context.contentResolver.query(
                TvContract.WatchNextPrograms.CONTENT_URI,
                COLUMNS,
                null,
                null,
                "${TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS} DESC"
            )
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun read(cursor: Cursor): WatchItem? {
        val title = cursor.getString(COLUMN_TITLE)?.takeIf { it.isNotBlank() } ?: return null
        val packageName = cursor.getString(COLUMN_PACKAGE).orEmpty()
        val duration = cursor.getLong(COLUMN_DURATION)
        val position = cursor.getLong(COLUMN_POSITION)
        val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
        return WatchItem(
            title = title,
            subtitle = subtitle(cursor, position, duration),
            progress = progress,
            posterUri = cursor.getString(COLUMN_POSTER),
            appLabel = appLabel(packageName),
            packageName = packageName,
            launch = launchIntent(cursor.getString(COLUMN_INTENT))
        )
    }

    private fun subtitle(cursor: Cursor, position: Long, duration: Long): String {
        val episode = cursor.getString(COLUMN_EPISODE_TITLE)?.takeIf { it.isNotBlank() }
        val resume = if (duration > 0 && position > 0) "Dừng ở ${clock(position)}" else null
        return listOfNotNull(episode, resume).joinToString(" · ")
    }

    private fun clock(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = totalSeconds % 3600 / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
        else "%d:%02d".format(minutes, seconds)
    }

    private fun appLabel(packageName: String): String {
        return try {
            val packages = context.packageManager
            packages.getApplicationLabel(packages.getApplicationInfo(packageName, 0)).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    private fun launchIntent(uri: String?): Intent? {
        if (uri.isNullOrBlank()) return null
        return try {
            Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val MAX_ITEMS = 8
        const val COLUMN_TITLE = 0
        const val COLUMN_EPISODE_TITLE = 1
        const val COLUMN_PACKAGE = 2
        const val COLUMN_POSTER = 3
        const val COLUMN_INTENT = 4
        const val COLUMN_DURATION = 5
        const val COLUMN_POSITION = 6

        val COLUMNS = arrayOf(
            TvContract.WatchNextPrograms.COLUMN_TITLE,
            TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE,
            TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME,
            TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
            TvContract.WatchNextPrograms.COLUMN_INTENT_URI,
            TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS,
            TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS
        )
    }
}
