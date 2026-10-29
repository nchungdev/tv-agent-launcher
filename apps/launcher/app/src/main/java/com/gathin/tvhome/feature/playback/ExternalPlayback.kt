package com.gathin.tvhome.feature.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.gathin.tvhome.feature.agent.data.PlaybackDescriptor

object ExternalPlayback {
    private val packages = mapOf("plex" to "com.plexapp.android",
        "jellyfin" to "org.jellyfin.androidtv", "youtube" to "com.google.android.youtube.tv")

    fun source(context: Context, source: String, url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        if (uri.scheme !in setOf("http", "https", "plex", "jellyfin")) return false
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        packages[source]?.let { intent.setPackage(it) }
        return start(context, intent)
    }

    fun stream(context: Context, descriptor: PlaybackDescriptor): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(descriptor.url),
            descriptor.mimeType.ifBlank { "video/*" }).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val players = descriptor.preferredPlayers + listOf("org.videolan.vlc",
            "com.brouken.player", "com.mxtech.videoplayer.ad", "com.mxtech.videoplayer.pro")
        for (player in players.distinct()) {
            if (!com.gathin.tvhome.feature.watching.data.WatchMerge
                .validPackage(player)) continue
            val explicit = Intent(intent).setPackage(player)
            if (explicit.resolveActivity(context.packageManager) != null &&
                start(context, explicit)) {
                return true
            }
        }
        if (intent.resolveActivity(context.packageManager) == null) return false
        return start(context, Intent.createChooser(intent, descriptor.title)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun start(context: Context, intent: Intent): Boolean = runCatching {
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
