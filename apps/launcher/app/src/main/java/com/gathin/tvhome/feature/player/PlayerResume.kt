package com.gathin.tvhome.feature.player

import android.content.Context

class PlayerResume(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun position(episodeId: String): Long = prefs.getLong(episodeId, 0L)

    fun save(episodeId: String, positionMs: Long, durationMs: Long) {
        val nearEnd = durationMs > 0 && durationMs - positionMs < END_MARGIN_MS
        val editor = prefs.edit()
        if (positionMs < START_MARGIN_MS || nearEnd) editor.remove(episodeId)
        else editor.putLong(episodeId, positionMs)
        if (prefs.all.size > MAX_ENTRIES) editor.clear().putLong(episodeId, positionMs)
        editor.apply()
    }

    private companion object {
        const val PREFS = "player_resume"
        const val START_MARGIN_MS = 30_000L
        const val END_MARGIN_MS = 90_000L
        const val MAX_ENTRIES = 200
    }
}
