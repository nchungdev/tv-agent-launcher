package com.gathin.tvhome.feature.playback

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.VideoView
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.agent.data.PlaybackDescriptor

class LauncherPlayer(
    private val activity: Activity,
    private val identity: PlaybackIdentity,
    private val descriptor: PlaybackDescriptor,
    private val retry: () -> Unit,
    private val episodes: (() -> Unit)? = null
) {
    private val dialog = Dialog(activity, android.R.style.Theme_Material_NoActionBar_Fullscreen)
    private val video = VideoView(activity)
    private val controls = PlaybackControls(activity)
    private val handler = Handler(Looper.getMainLooper())
    private val history = PlaybackStore(activity)
    private var prepared = false
    private var ended = false
    private var media: MediaPlayer? = null
    private var trackDialog: android.app.AlertDialog? = null
    private var lastSave = 0L
    private var session: PlayerSession? = null
    private var closed = false
    private val hide = Runnable { controls.visibility = View.GONE }
    private val progress = object : Runnable {
        override fun run() {
            if (closed) return
            if (prepared) {
                session?.update(video.currentPosition, video.isPlaying)
                controls.timeline.max = video.duration.coerceAtLeast(1)
                controls.timeline.progress = video.currentPosition
                controls.status.text = PlaybackControls.time(video.currentPosition) + " / " +
                    PlaybackControls.time(video.duration)
                if (android.os.SystemClock.elapsedRealtime() - lastSave >= 10_000) save()
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
        root.addView(video, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        root.addView(controls, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        dialog.setContentView(root)
        dialog.setOnDismissListener { close() }
        dialog.setOnKeyListener { _, key, event -> key(key, event) }
        controls.title.text = identity.title
        controls.status.setText(R.string.playback_loading)
        controls.pause.setOnClickListener { toggle() }
        controls.button("−10s") { seek(video.currentPosition - 10_000) }
        controls.button("+10s") { seek(video.currentPosition + 10_000) }
        controls.button(activity.getString(R.string.playback_tracks)) { tracks() }
        episodes?.let { open ->
            controls.button(activity.getString(R.string.playback_episodes)) { close(); open() }
        }
        controls.button(activity.getString(R.string.playback_app)) {
            if (!ExternalPlayback.stream(activity, descriptor)) {
                android.widget.Toast.makeText(activity, R.string.playback_no_app,
                    android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        controls.button(activity.getString(R.string.playback_retry)) { close(); retry() }
        controls.button(activity.getString(R.string.playback_close)) { close() }
        controls.timeline.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, user: Boolean) {
                if (user && prepared) {
                    video.seekTo(value)
                    handler.removeCallbacks(hide)
                }
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar) { seek(bar.progress) }
        })
        video.setOnPreparedListener { player ->
            media = player
            prepared = true
            val position = history.position(identity).coerceAtMost(player.duration.toLong())
            if (position > 0 && position < player.duration * 0.95) video.seekTo(position.toInt())
            session = PlayerSession(activity, identity, { video.start(); reveal() },
                { pause() }, { seek(it) })
            video.start()
            reveal()
        }
        video.setOnInfoListener { _, code, _ ->
            if (code == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                controls.visibility = View.VISIBLE
                controls.status.setText(R.string.playback_loading)
            }
            false
        }
        video.setOnCompletionListener { ended = true; save(); reveal() }
        video.setOnErrorListener { _, _, _ ->
            save()
            prepared = false
            controls.visibility = View.VISIBLE
            controls.status.setText(R.string.playback_error)
            handler.removeCallbacks(hide)
            true
        }
        dialog.show()
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        dialog.window?.setLayout(-1, -1)
        controls.pause.requestFocus()
        video.setVideoURI(Uri.parse(descriptor.url))
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
            KeyEvent.KEYCODE_MEDIA_PLAY -> if (prepared) video.start()
            KeyEvent.KEYCODE_MEDIA_PAUSE -> pause()
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> seek(video.currentPosition + 10_000)
            KeyEvent.KEYCODE_MEDIA_REWIND -> seek(video.currentPosition - 10_000)
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
        if (video.isPlaying) pause() else { ended = false; video.start(); reveal() }
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
        video.seekTo(position.coerceIn(0, video.duration.coerceAtLeast(0)))
        reveal()
    }

    private fun tracks() {
        val player = media ?: return
        val tracks = player.trackInfo.withIndex().filter { (_, track) ->
            track.trackType in setOf(MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO,
                MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_SUBTITLE,
                MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT)
        }
        val labels = tracks.map { (_, track) ->
            (if (track.trackType == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO) "Audio" else
                activity.getString(R.string.playback_subtitles)) + " · " + track.language
        } + activity.getString(R.string.playback_subtitles_off)
        handler.removeCallbacks(hide)
        trackDialog = android.app.AlertDialog.Builder(activity).setTitle(R.string.playback_tracks)
            .setItems(labels.toTypedArray()) { _, index ->
                runCatching {
                    if (index < tracks.size) player.selectTrack(tracks[index].index) else {
                        tracks.filter { it.value.trackType !=
                            MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO }.forEach {
                            player.deselectTrack(it.index)
                        }
                    }
                }
                reveal()
            }.show()
    }

    private fun save() {
        if (!prepared) return
        runCatching { history.record(identity, video.currentPosition.toLong(),
            video.duration.toLong(), ended) }
        lastSave = android.os.SystemClock.elapsedRealtime()
    }

    fun close() {
        if (closed) return
        save()
        closed = true
        handler.removeCallbacksAndMessages(null)
        trackDialog?.dismiss()
        video.stopPlayback()
        media = null
        session?.close()
        session = null
        activity.application.unregisterActivityLifecycleCallbacks(lifecycle)
        dialog.dismiss()
    }
}
