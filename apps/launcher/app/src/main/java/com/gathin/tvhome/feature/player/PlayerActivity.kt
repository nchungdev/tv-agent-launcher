package com.gathin.tvhome.feature.player

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.agent.data.CatalogClient
import com.gathin.tvhome.feature.agent.data.CatalogEpisode
import com.gathin.tvhome.feature.playback.ExoPlayerHelper
import java.util.concurrent.Executors

@OptIn(markerClass = [UnstableApi::class])
class PlayerActivity : Activity() {

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var request: PlayRequest
    private lateinit var overlay: PlayerOverlay
    private lateinit var resume: PlayerResume
    private var player: ExoPlayer? = null
    private val failedServers = HashSet<String>()
    private var loadToken = 0
    private var lastSaved = 0L

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            main.postDelayed(this, TICK_MS)
        }
    }
    private val hidePanel = Runnable { if (player?.isPlaying == true) overlay.showPanel(false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        request = intent.getStringExtra(EXTRA_REQUEST)?.let(PlayRequest::fromJson) ?: run {
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        resume = PlayerResume(this)

        val exo = ExoPlayerHelper.createPlayer(this)
        player = exo
        exo.addListener(listener)
        val view = PlayerView(this).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShutterBackgroundColor(0xFF000000.toInt())
            this.player = exo
        }
        overlay = PlayerOverlay(this)
        val root = FrameLayout(this)
        root.addView(view)
        root.addView(overlay)
        setContentView(root)
        request.current?.let(::load) ?: finish()
    }

    override fun onStart() {
        super.onStart()
        main.post(ticker)
    }

    override fun onStop() {
        save(force = true)
        player?.pause()
        main.removeCallbacks(ticker)
        super.onStop()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        worker.shutdownNow()
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val exo = player ?: return super.onKeyDown(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> {
                if (exo.isPlaying) exo.pause() else exo.play()
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> exo.play()
            KeyEvent.KEYCODE_MEDIA_PAUSE -> exo.pause()
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND ->
                seekBy(-step(event))
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD ->
                seekBy(step(event))
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_MENU -> openPicker()
            KeyEvent.KEYCODE_MEDIA_NEXT -> request.next()?.let(::switchTo)
            KeyEvent.KEYCODE_DPAD_UP -> Unit
            else -> return super.onKeyDown(keyCode, event)
        }
        poke()
        return true
    }

    private fun step(event: KeyEvent): Long =
        if (event.repeatCount > FAST_AFTER) FAST_SEEK_MS else SEEK_MS

    private fun seekBy(deltaMs: Long) {
        val exo = player ?: return
        val duration = exo.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        exo.seekTo((exo.currentPosition + deltaMs).coerceIn(0, duration))
    }

    private fun poke() {
        overlay.showPanel(true)
        main.removeCallbacks(hidePanel)
        main.postDelayed(hidePanel, PANEL_MS)
        refresh()
    }

    private fun openPicker() {
        player?.pause()
        PickerDialog.show(this, request) { switchTo(it) }
    }

    private fun switchTo(episode: CatalogEpisode) {
        save(force = true)
        failedServers.clear()
        request = request.with(episode)
        load(episode)
    }

    private fun load(episode: CatalogEpisode) {
        val token = ++loadToken
        overlay.setTitles(request.displayTitle(), detail(episode))
        overlay.error(null)
        overlay.loading(true)
        poke()
        worker.execute {
            val stream = runCatching { CatalogClient.resolvePlayback(this, episode.id) }
                .getOrNull()?.playback
            main.post {
                if (token != loadToken || isFinishing) return@post
                val url = stream?.url.orEmpty()
                if (url.isBlank()) failover(episode) else start(episode, url, stream?.mimeType)
            }
        }
    }

    private fun start(episode: CatalogEpisode, url: String, mime: String?) {
        val exo = player ?: return
        val item = ExoPlayerHelper.buildMediaItem(url, mime)
        exo.setMediaItem(item, resume.position(episode.id))
        exo.prepare()
        exo.play()
    }

    private fun failover(episode: CatalogEpisode) {
        failedServers.add(episode.group)
        val other = request.servers.firstOrNull { it !in failedServers }
            ?.let { request.sameEpisodeOn(it) }
        if (other != null) {
            request = request.with(other)
            load(other)
            return
        }
        overlay.loading(false)
        overlay.error(getString(R.string.player_failed))
        overlay.showPanel(true)
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            overlay.loading(state == Player.STATE_BUFFERING)
            if (state == Player.STATE_ENDED) onEnded()
            refresh()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) poke() else main.postDelayed(hidePanel, PANEL_MS)
            refresh()
        }

        override fun onPlayerError(error: PlaybackException) {
            request.current?.let(::failover)
        }
    }

    private fun onEnded() {
        request.current?.let { resume.save(it.id, 0, 0) }
        val next = if (request.series) request.next() else null
        if (next != null) switchTo(next) else finish()
    }

    private fun refresh() {
        val exo = player ?: return
        overlay.update(exo.currentPosition, exo.duration.coerceAtLeast(0), exo.isPlaying)
        save(force = false)
    }

    private fun save(force: Boolean) {
        val exo = player ?: return
        val episode = request.current ?: return
        val now = System.currentTimeMillis()
        if (!force && now - lastSaved < SAVE_MS) return
        if (exo.duration <= 0) return
        lastSaved = now
        resume.save(episode.id, exo.currentPosition, exo.duration)
    }

    private fun detail(episode: CatalogEpisode): String =
        listOf(episode.group, if (request.series) "" else episode.label.takeIf { it != "Full" })
            .filter { !it.isNullOrBlank() }.joinToString(" · ")

    companion object {
        private const val EXTRA_REQUEST = "play_request"
        private const val TICK_MS = 1000L
        private const val PANEL_MS = 4000L
        private const val SAVE_MS = 10_000L
        private const val SEEK_MS = 10_000L
        private const val FAST_SEEK_MS = 30_000L
        private const val FAST_AFTER = 3

        fun start(context: Context, request: PlayRequest) {
            val intent = Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_REQUEST, request.toJson())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
