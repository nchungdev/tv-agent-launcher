package com.gathin.tvhome.feature.playback

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.agent.data.PlaybackDescriptor

@OptIn(markerClass = [UnstableApi::class])
class LauncherPlayer(
    private val activity: Activity,
    private val identity: PlaybackIdentity,
    private val descriptor: PlaybackDescriptor,
    private val retry: () -> Unit,
    private val episodes: (() -> Unit)? = null
) {
    private val dialog = Dialog(activity, android.R.style.Theme_Material_NoActionBar_Fullscreen)
    private val video: ExoPlayer = ExoPlayerHelper.createPlayer(activity)
    private val surface = PlayerView(activity).apply {
        useController = false
        player = video
    }
    private val controls = PlaybackControls(activity)
    private val handler = Handler(Looper.getMainLooper())
    private val history = PlaybackStore(activity)
    private var prepared = false
    private var ended = false
    private var trackDialog: AlertDialog? = null
    private var lastSave = 0L
    private var session: PlayerSession? = null
    private var closed = false
    private val hide = Runnable { controls.visibility = View.GONE }
    private val progress = object : Runnable {
        override fun run() {
            if (closed) return
            if (prepared) {
                session?.update(position(), video.isPlaying)
                controls.timeline.max = duration().coerceAtLeast(1)
                controls.timeline.progress = position()
                controls.status.text = PlaybackControls.time(position()) + " / " +
                    PlaybackControls.time(duration())
                if (SystemClock.elapsedRealtime() - lastSave >= 10_000) save()
            }
            handler.postDelayed(this, 1000)
        }
    }
    private val lifecycle = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityStopped(host: Activity) { if (host === activity) close() }
        override fun onActivityDestroyed(host: Activity) { if (host === activity) close() }
        override fun onActivityCreated(host: Activity, state: Bundle?) {}
        override fun onActivityStarted(host: Activity) {}
        override fun onActivityResumed(host: Activity) {}
        override fun onActivityPaused(host: Activity) { if (host === activity) pause() }
        override fun onActivitySaveInstanceState(host: Activity, state: Bundle) {}
    }

    fun show() {
        activity.application.registerActivityLifecycleCallbacks(lifecycle)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val root = FrameLayout(activity).apply { setBackgroundColor(Color.BLACK) }
        root.addView(surface, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        root.addView(controls, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        dialog.setContentView(root)
        dialog.setOnDismissListener { close() }
        dialog.setOnKeyListener { _, key, event -> key(key, event) }
        controls.title.text = identity.title
        controls.status.setText(R.string.playback_loading)
        controls.pause.setOnClickListener { toggle() }
        controls.button("−10s") { seek(position() - 10_000) }
        controls.button("+10s") { seek(position() + 10_000) }
        controls.button(activity.getString(R.string.playback_tracks)) { tracks() }
        episodes?.let { open ->
            controls.button(activity.getString(R.string.playback_episodes)) { close(); open() }
        }
        controls.button(activity.getString(R.string.playback_app)) {
            if (!ExternalPlayback.stream(activity, descriptor)) {
                Toast.makeText(activity, R.string.playback_no_app,
                    Toast.LENGTH_SHORT).show()
            }
        }
        controls.button(activity.getString(R.string.playback_retry)) { close(); retry() }
        controls.button(activity.getString(R.string.playback_close)) { close() }
        controls.timeline.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, user: Boolean) {
                if (user && prepared) {
                    video.seekTo(value.toLong())
                    handler.removeCallbacks(hide)
                }
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar) { seek(bar.progress) }
        })
        video.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (closed) return
                if (state == Player.STATE_READY && !prepared) {
                    prepared = true
                    session = PlayerSession(activity, identity, { video.play(); reveal() },
                        { pause() }, { seek(it) })
                    reveal()
                }
                if (state == Player.STATE_BUFFERING) {
                    controls.visibility = View.VISIBLE
                    controls.status.setText(R.string.playback_loading)
                }
                if (state == Player.STATE_ENDED) { ended = true; save(); reveal() }
            }

            override fun onPlayerError(error: PlaybackException) {
                save()
                prepared = false
                controls.visibility = View.VISIBLE
                controls.status.setText(R.string.playback_error)
                handler.removeCallbacks(hide)
            }
        })
        dialog.show()
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        dialog.window?.setLayout(-1, -1)
        controls.pause.requestFocus()
        val position = history.position(identity)
        val previous = history.items().firstOrNull { it.contentId ==
            "catalog:${identity.sourceId}|${identity.episodeId}" }
        val resume = if (previous != null && previous.progress < 0.95f) position else 0L
        val item = ExoPlayerHelper.buildMediaItem(descriptor.url, descriptor.mimeType)
        video.setMediaItem(item, resume)
        video.prepare()
        video.play()
        handler.post(progress)
    }

    private fun key(code: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return code == KeyEvent.KEYCODE_BACK
        if (code == KeyEvent.KEYCODE_BACK) {
            if (controls.visibility == View.VISIBLE) controls.visibility = View.GONE else close()
            return true
        }
        when (code) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> toggle()
            KeyEvent.KEYCODE_MEDIA_PLAY -> if (prepared) video.play()
            KeyEvent.KEYCODE_MEDIA_PAUSE -> pause()
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> seek(position() + 10_000)
            KeyEvent.KEYCODE_MEDIA_REWIND -> seek(position() - 10_000)
            else -> {
                if (controls.visibility != View.VISIBLE) { reveal(); return true }
                handler.removeCallbacks(hide)
                handler.postDelayed(hide, 5000)
                return false
            }
        }
        return true
    }

    private fun reveal() {
        controls.visibility = View.VISIBLE
        controls.pause.requestFocus()
        handler.removeCallbacks(hide)
        if (prepared && video.isPlaying) handler.postDelayed(hide, 5000)
    }

    private fun toggle() {
        if (!prepared) return
        if (video.isPlaying) pause() else { ended = false; video.play(); reveal() }
    }

    private fun pause() {
        if (!prepared) return
        video.pause()
        save()
        reveal()
    }

    private fun seek(position: Int) {
        if (!prepared) return
        ended = false
        video.seekTo(position.coerceIn(0, duration().coerceAtLeast(0)).toLong())
        reveal()
    }

    private fun tracks() {
        val tracks = video.currentTracks.groups.flatMap { group ->
            (0 until group.length).filter { group.isTrackSupported(it) &&
                group.type in setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_TEXT) }
                .map { group to it }
        }
        val labels = tracks.map { (group, index) ->
            val format = group.getTrackFormat(index)
            (if (group.type == C.TRACK_TYPE_AUDIO) "Audio" else
                activity.getString(R.string.playback_subtitles)) + " · " +
                (format.label ?: format.language.orEmpty())
        } + activity.getString(R.string.playback_subtitles_off)
        handler.removeCallbacks(hide)
        trackDialog = AlertDialog.Builder(activity).setTitle(R.string.playback_tracks)
            .setItems(labels.toTypedArray()) { _, index ->
                val parameters = video.trackSelectionParameters.buildUpon()
                if (index < tracks.size) {
                    val (group, track) = tracks[index]
                    parameters.setTrackTypeDisabled(group.type, false)
                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, track))
                } else parameters.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                video.trackSelectionParameters = parameters.build()
                reveal()
            }.show()
    }

    private fun position(): Int = video.currentPosition.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    private fun duration(): Int = video.duration.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

    private fun save() {
        if (!prepared) return
        runCatching { history.record(identity, position().toLong(),
            duration().toLong(), ended) }
        lastSave = SystemClock.elapsedRealtime()
    }

    fun close() {
        if (closed) return
        save()
        closed = true
        handler.removeCallbacksAndMessages(null)
        trackDialog?.dismiss()
        surface.player = null
        video.release()
        session?.close()
        session = null
        activity.application.unregisterActivityLifecycleCallbacks(lifecycle)
        dialog.dismiss()
    }
}
