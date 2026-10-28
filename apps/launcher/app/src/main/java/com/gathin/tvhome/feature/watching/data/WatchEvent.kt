package com.gathin.tvhome.feature.watching.data

import org.json.JSONObject

data class WatchEvent(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val subtitle: String,
    val mediaId: String,
    val positionMs: Long,
    val durationMs: Long,
    val state: String,
    val artworkUrl: String,
    val at: Long,
    val resumeUri: String = ""
) {
    val key: String get() = when {
        mediaId.isNotBlank() -> "$packageName|id:$mediaId"
        resumeUri.isNotBlank() -> "$packageName|uri:$resumeUri"
        else -> "$packageName|text:$title|$subtitle"
    }

    fun toJson(): JSONObject = JSONObject()
        .put("package_name", packageName)
        .put("app_label", appLabel)
        .put("title", title)
        .put("subtitle", subtitle)
        .put("media_id", mediaId)
        .put("position_ms", positionMs)
        .put("duration_ms", durationMs)
        .put("state", state)
        .put("artwork_url", artworkUrl)
        .put("at", at)
        .put("resume_uri", resumeUri)

    companion object {
        const val PLAYING = "playing"
        const val PAUSED = "paused"
        const val STOPPED = "stopped"

        fun fromJson(json: JSONObject): WatchEvent = WatchEvent(
            packageName = json.optString("package_name"),
            appLabel = json.optString("app_label"),
            title = json.optString("title"),
            subtitle = json.optString("subtitle"),
            mediaId = json.optString("media_id"),
            positionMs = json.optLong("position_ms"),
            durationMs = json.optLong("duration_ms"),
            state = json.optString("state"),
            artworkUrl = json.optString("artwork_url"),
            at = json.optLong("at"),
            resumeUri = json.optString("resume_uri")
        )
    }
}
