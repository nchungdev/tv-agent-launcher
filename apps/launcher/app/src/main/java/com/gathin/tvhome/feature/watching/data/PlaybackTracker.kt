package com.gathin.tvhome.feature.watching.data

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

class PlaybackTracker(private val context: Context, private val listener: ComponentName) {

    private val main = Handler(Looper.getMainLooper())
    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val reporter = WatchReporter(context)
    private val watched = HashMap<MediaSession.Token, Watched>()

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener {
        follow(it.orEmpty())
    }

    private val heartbeat = object : Runnable {
        override fun run() {
            for (entry in watched.values) entry.reportIfPlaying()
            main.postDelayed(this, HEARTBEAT_MS)
        }
    }

    fun start() {
        val manager = sessions ?: return
        try {
            manager.addOnActiveSessionsChangedListener(sessionsChanged, listener, main)
            follow(manager.getActiveSessions(listener))
        } catch (_: SecurityException) {
            return
        }
        main.postDelayed(heartbeat, HEARTBEAT_MS)
    }

    fun stop() {
        main.removeCallbacks(heartbeat)
        runCatching { sessions?.removeOnActiveSessionsChangedListener(sessionsChanged) }
        for (entry in watched.values) entry.finish()
        watched.clear()
    }

    private fun follow(controllers: List<MediaController>) {
        val current = controllers.filter { it.packageName != context.packageName }
        val live = current.map { it.sessionToken }.toSet()
        for (gone in watched.keys.filter { it !in live }) watched.remove(gone)?.finish()
        for (controller in current) {
            if (watched.containsKey(controller.sessionToken)) continue
            watched[controller.sessionToken] = Watched(controller)
        }
    }

    private inner class Watched(private val controller: MediaController) {
        private var lastEvent: WatchEvent? = null

        private val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) = reportChange()
            override fun onPlaybackStateChanged(state: PlaybackState?) = reportChange()
            override fun onSessionDestroyed() = finish()
        }

        init {
            controller.registerCallback(callback, main)
            reportChange()
        }

        fun reportIfPlaying() {
            if (lastEvent?.state == WatchEvent.PLAYING) report(build(WatchEvent.PLAYING))
        }

        fun finish() {
            controller.unregisterCallback(callback)
            val last = lastEvent ?: return
            if (last.state == WatchEvent.STOPPED) return
            report(build(WatchEvent.STOPPED) ?: last.copy(state = WatchEvent.STOPPED))
        }

        private fun reportChange() {
            val state = if (isPlaying()) WatchEvent.PLAYING else WatchEvent.PAUSED
            val event = build(state) ?: return
            val previous = lastEvent
            if (previous != null && previous.key == event.key && previous.state == state) return
            report(event)
        }

        private fun report(event: WatchEvent?) {
            if (event == null) return
            lastEvent = event
            reporter.report(event)
        }

        private fun isPlaying(): Boolean =
            controller.playbackState?.state == PlaybackState.STATE_PLAYING

        private fun build(state: String): WatchEvent? {
            val metadata = controller.metadata ?: return null
            val title = SessionMetadata.title(metadata) ?: return null
            return WatchEvent(
                packageName = controller.packageName,
                appLabel = SessionMetadata.appLabel(context, controller.packageName),
                title = title,
                subtitle = SessionMetadata.subtitle(metadata),
                mediaId = SessionMetadata.mediaId(metadata),
                positionMs = position(),
                durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION),
                state = state,
                artworkUrl = SessionMetadata.artwork(metadata),
                at = System.currentTimeMillis()
            )
        }

        private fun position(): Long {
            val playback = controller.playbackState ?: return 0L
            if (playback.state != PlaybackState.STATE_PLAYING) return playback.position
            val elapsed = SystemClock.elapsedRealtime() - playback.lastPositionUpdateTime
            return playback.position + (elapsed * playback.playbackSpeed).toLong()
        }
    }

    private companion object {
        const val HEARTBEAT_MS = 60_000L
    }
}
