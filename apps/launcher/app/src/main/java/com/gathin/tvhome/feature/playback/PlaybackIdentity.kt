package com.gathin.tvhome.feature.playback

import java.net.URI

data class PlaybackIdentity(
    val sourceId: String,
    val episodeId: String,
    val title: String,
    val poster: String = "",
    val titleId: String = "",
    val kind: String = ""
) {
    val key: String get() = "$sourceId|$episodeId"
}

object PlaybackUrls {
    private val mimeTypes = setOf(
        "video/mp4", "video/webm", "video/x-matroska",
        "application/x-mpegurl", "application/vnd.apple.mpegurl", "video/*"
    )

    fun resolve(base: String, value: String): String {
        val uri = URI(value.trim())
        require(value.isNotBlank() && value.length <= 8192 && uri.rawUserInfo == null)
        require(uri.rawAuthority == null || uri.isAbsolute)
        val resolved = if (uri.isAbsolute) uri else URI(base.trimEnd('/') + "/").resolve(uri)
        require(resolved.scheme in setOf("http", "https") && !resolved.host.isNullOrBlank())
        require(resolved.rawUserInfo == null && resolved.fragment == null)
        return resolved.toASCIIString()
    }

    fun supported(mime: String): Boolean {
        val clean = mime.lowercase().trim()
        if (clean.isBlank() || clean == "video/*" || clean == "audio/*" ||
            clean == "application/octet-stream") return true
        if (clean.startsWith("video/") || clean.startsWith("audio/")) return true
        if (clean.contains("mpegurl") || clean.contains("dash") ||
            clean.contains("smoothstreaming")) return true
        return clean in mimeTypes
    }
}
