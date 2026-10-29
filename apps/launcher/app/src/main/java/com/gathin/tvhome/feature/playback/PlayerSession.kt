package com.gathin.tvhome.feature.playback

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState

class PlayerSession(context: Context, identity: PlaybackIdentity,
    play: () -> Unit, pause: () -> Unit, seek: (Int) -> Unit) {
    private val session = MediaSession(context, "LauncherPlayer").apply {
        setCallback(object : MediaSession.Callback() {
            override fun onPlay() = play()
            override fun onPause() = pause()
            override fun onSeekTo(position: Long) =
                seek(position.coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
        })
        setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,
            identity.title).putString(MediaMetadata.METADATA_KEY_MEDIA_ID, identity.key).build())
        isActive = true
    }

    fun update(position: Int, playing: Boolean) {
        session.setPlaybackState(PlaybackState.Builder().setActions(
            PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SEEK_TO)
            .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                position.toLong(), if (playing) 1f else 0f).build())
    }

    fun close() { session.isActive = false; session.release() }
}
