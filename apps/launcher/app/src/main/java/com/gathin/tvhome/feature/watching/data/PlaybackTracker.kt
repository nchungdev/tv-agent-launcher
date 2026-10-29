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
import android.os.HandlerThread
import android.os.SystemClock

class PlaybackTracker(private val context: Context, private val listener: ComponentName) {

    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val reporter = WatchReporter(context)
    private val artwork = ArtworkStore(context)
    private var active: TrackingSession? = null

    @Synchronized
    fun start() {
        if (active != null || sessions == null) return
        active = TrackingSession().also { it.start() }
    }

    @Synchronized
    fun stop() {
        active?.stop()
        active = null
    }

    private inner class TrackingSession {
        private val thread = HandlerThread("watch-sessions").apply { start() }
        private val handler = Handler(thread.looper)
        private val watched = HashMap<MediaSession.Token, Watched>()
        @Volatile private var running = true
        private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener {
            follow(it.orEmpty())
        }
        private val heartbeat = object : Runnable {
            override fun run() {
                if (!running) return
                for (entry in watched.values) entry.reportIfPlaying()
                handler.postDelayed(this, HEARTBEAT_MS)
            }
        }

        fun start() {
            handler.post {
                if (!running) return@post
                try {
                    sessions?.addOnActiveSessionsChangedListener(sessionsChanged, listener, handler)
                    follow(sessions?.getActiveSessions(listener).orEmpty())
                    handler.postDelayed(heartbeat, HEARTBEAT_MS)
                } catch (_: SecurityException) { stop() }
            }
        }

        fun stop() {
            running = false
            handler.post {
                handler.removeCallbacksAndMessages(null)
                runCatching { sessions?.removeOnActiveSessionsChangedListener(sessionsChanged) }
                for (entry in watched.values) entry.finish()
                watched.clear()
                thread.quitSafely()
            }
        }

        private fun follow(controllers: List<MediaController>) {
            if (!running) return
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
                controller.registerCallback(callback, handler)
                reportChange()
            }

            fun reportIfPlaying() {
                if (lastEvent?.state == WatchEvent.PLAYING) report(build(WatchEvent.PLAYING))
            }

            fun finish() {
                controller.unregisterCallback(callback)
                val last = lastEvent ?: return
                if (last.state == WatchEvent.STOPPED) return
                val stopped = last.copy(state = WatchEvent.STOPPED, at = System.currentTimeMillis())
                report(build(WatchEvent.STOPPED) ?: stopped)
            }

            private fun reportChange() {
                if (!running) return
                val state = if (isPlaying()) WatchEvent.PLAYING else WatchEvent.PAUSED
                val event = build(state) ?: return
                val previous = lastEvent
                if (previous != null && previous.key == event.key && previous.state == state &&
                    previous.resumeUri == event.resumeUri && previous.title == event.title &&
                    previous.subtitle == event.subtitle) return
                report(event)
            }

            private fun report(event: WatchEvent?) {
                if (event == null) return
                val kept = keepProgress(event)
                lastEvent = kept
                reporter.report(kept)
            }

            private fun keepProgress(event: WatchEvent): WatchEvent {
                val previous = lastEvent ?: return event
                if (previous.key != event.key || event.positionMs > 0) return event
                return event.copy(
                    positionMs = previous.positionMs,
                    durationMs = maxOf(event.durationMs, previous.durationMs),
                    artworkUrl = event.artworkUrl.ifEmpty { previous.artworkUrl }
                )
            }

            private fun isPlaying(): Boolean =
                controller.playbackState?.state == PlaybackState.STATE_PLAYING

            private fun build(state: String): WatchEvent? {
                val metadata = controller.metadata ?: return null
                val title = SessionMetadata.title(metadata) ?: return null
                val mediaId = SessionMetadata.mediaId(metadata)
                val key = "${controller.packageName}|${mediaId.ifBlank { title }}"
                val subtitle = SessionMetadata.subtitle(metadata)
                return WatchEvent(
                    packageName = controller.packageName,
                    appLabel = SessionMetadata.appLabel(context, controller.packageName),
                    title = title,
                    subtitle = subtitle,
                    mediaId = mediaId,
                    positionMs = position(),
                    durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION),
                    state = state,
                    artworkUrl = artwork.artwork(metadata, key),
                    at = System.currentTimeMillis(),
                    resumeUri = SessionMetadata.resumeUri(metadata),
                    details = SessionMetadata.details(controller.packageName, metadata, title, subtitle)
                )
            }

            private fun position(): Long {
                val playback = controller.playbackState ?: return 0L
                if (playback.state != PlaybackState.STATE_PLAYING) return playback.position
                val elapsed = SystemClock.elapsedRealtime() - playback.lastPositionUpdateTime
                return playback.position + (elapsed * playback.playbackSpeed).toLong()
            }
        }

    }

    private companion object {
        const val HEARTBEAT_MS = 60_000L
    }
}
