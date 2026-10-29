package com.gathin.tvhome.feature.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

@OptIn(markerClass = [UnstableApi::class])
object ExoPlayerHelper {

    private const val USER_AGENT =
        "Mozilla/5.0 (SmartTV; Linux; Android 11; TV) AppleWebKit/537.36 Chrome/122.0.0.0 Safari/537.36"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 20_000

    fun createPlayer(context: Context): ExoPlayer {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(READ_TIMEOUT_MS)

        val mediaSourceFactory = DefaultMediaSourceFactory(httpFactory)

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
    }

    fun buildMediaItem(url: String, mimeType: String?): MediaItem {
        val builder = MediaItem.Builder().setUri(url)
        val mime = mimeType?.lowercase()?.trim().orEmpty()
        val isHls = mime.contains("mpegurl") ||
            url.contains(".m3u8", ignoreCase = true) ||
            mime == "application/x-mpegurl" ||
            mime == "application/vnd.apple.mpegurl"
        val isDash = mime.contains("dash") || url.contains(".mpd", ignoreCase = true)
        val isSs = mime.contains("smoothstreaming") || url.contains(".ism", ignoreCase = true)

        when {
            isHls -> builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            isDash -> builder.setMimeType(MimeTypes.APPLICATION_MPD)
            isSs -> builder.setMimeType(MimeTypes.APPLICATION_SS)
            mime.isNotBlank() && mime != "video/*" && mime != "audio/*" &&
                mime != "application/octet-stream" -> builder.setMimeType(mime)
        }
        return builder.build()
    }
}
