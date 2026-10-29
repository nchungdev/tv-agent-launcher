package com.gathin.tvhome.feature.playback

import android.content.Context
import com.gathin.tvhome.feature.watching.data.WatchItem
import org.json.JSONArray
import org.json.JSONObject

class PlaybackStore(context: Context) {
    private val prefs = context.getSharedPreferences("launcher_playback", Context.MODE_PRIVATE)
    private val packageName = context.packageName

    fun remember(source: String) {
        if (source.isBlank()) return
        prefs.edit().putStringSet("sources", sources() + source).apply()
    }

    fun sources(): Set<String> = prefs.getStringSet("sources", emptySet())!!.toSet()
    fun preferred(source: String): String = prefs.getString("player:$source", "").orEmpty()
    fun prefer(source: String, player: String) {
        require(player in setOf("launcher", "app", ""))
        remember(source)
        prefs.edit().putString("player:$source", player).apply()
    }

    fun position(identity: PlaybackIdentity): Long = rows().firstOrNull {
        it.optString("source") == identity.sourceId &&
                it.optString("episode") == identity.episodeId
    }?.optLong("position") ?: 0

    fun record(identity: PlaybackIdentity, position: Long, duration: Long, ended: Boolean) {
        if (duration <= 0) return
        val kept = rows().filterNot {
            it.optString("source") == identity.sourceId &&
                it.optString("episode") == identity.episodeId
        }.take(19)
        val row = JSONObject().put("source", identity.sourceId).put("episode", identity.episodeId)
            .put("title", identity.title).put("poster", identity.poster)
            .put("title_id", identity.titleId).put("kind", identity.kind)
            .put("position", if (ended) duration else position.coerceIn(0, duration))
            .put("duration", duration).put("at", System.currentTimeMillis())
        prefs.edit().putString("history", JSONArray(listOf(row) + kept).toString()).apply()
    }

    fun items(): List<WatchItem> = rows().map { row ->
        val duration = row.optLong("duration").coerceAtLeast(1)
        val position = row.optLong("position").coerceIn(0, duration)
        val source = row.optString("source")
        val episode = row.optString("episode")
        WatchItem(row.optString("title"), source, position.toFloat() / duration,
            row.optString("poster").ifBlank { null }, "Launcher Player", packageName,
            "catalog.resume", mapOf("source_id" to source, "episode_id" to episode,
                "title" to row.optString("title"), "poster" to row.optString("poster"),
                "title_id" to row.optString("title_id"), "kind" to row.optString("kind")),
            duration - position, contentId = "catalog:$source|$episode",
            updatedAt = row.optLong("at"), durationMs = duration)
    }

    private fun rows(): List<JSONObject> {
        val array = runCatching { JSONArray(prefs.getString("history", "[]")) }.getOrNull()
            ?: return emptyList()
        return (0 until minOf(20, array.length())).mapNotNull { array.optJSONObject(it) }
    }
}
