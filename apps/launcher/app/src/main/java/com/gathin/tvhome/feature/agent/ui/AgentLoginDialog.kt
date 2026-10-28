package com.gathin.tvhome.feature.agent.ui

import android.app.Activity
import android.app.Dialog
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.gathin.tvhome.R
import com.gathin.tvhome.core.design.M3Skin
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.feature.agent.data.AgentClient
import com.gathin.tvhome.feature.agent.data.ServerPairing
import com.gathin.tvhome.feature.settings.data.AgentHomeConfig
import com.gathin.tvhome.feature.settings.data.Config
import com.gathin.tvhome.feature.settings.ui.CenterModal
import java.util.concurrent.Executors

class AgentLoginDialog(
    private val activity: Activity,
    private val onClosed: (signedIn: Boolean) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val dialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
    private lateinit var modal: CenterModal
    private lateinit var qrImage: ImageView
    private lateinit var codeText: TextView
    private lateinit var statusText: TextView
    private var pairing: ServerPairing? = null
    private var started: ServerPairing.Started? = null
    private var expiresAt = 0L
    private var active = true
    private var signedIn = false

    fun show() {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_agent_login, null)
        dialog.setContentView(view)
        qrImage = view.findViewById(R.id.loginQr)
        codeText = view.findViewById(R.id.loginCode)
        statusText = view.findViewById(R.id.loginStatus)

        val config = Config.load(activity)
        val scrim = view.findViewById<View>(R.id.scrim)
        val sheet = view.findViewById<View>(R.id.sheet)
        M3Skin.apply(sheet, Palette.dark(Palette.currentOrBaseline()), config.animations,
            config.reducedMotion)
        modal = CenterModal(activity, scrim, sheet, config) { finish() }
        view.findViewById<View>(R.id.loginCancel).apply {
            setOnClickListener { modal.close() }
            requestFocus()
        }
        dialog.setOnKeyListener { _, keyCode, event ->
            val back = keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP
            if (back) modal.close()
            back
        }
        dialog.show()
        modal.show()

        pairing = runCatching { ServerPairing(AgentClient.baseUrl(activity)) }.getOrNull()
        begin()
    }

    private fun begin() {
        if (!active) return
        val client = pairing ?: return say(R.string.agent_login_offline)
        worker.execute {
            val result = runCatching { client.start() }
            main.post {
                if (!active) return@post
                result.onSuccess { render(it) }.onFailure {
                    Log.w(TAG, "pairing start failed", it)
                    say(R.string.agent_login_offline)
                    main.postDelayed(::begin, RETRY_MS)
                }
            }
        }
    }

    private fun render(pairingStarted: ServerPairing.Started) {
        started = pairingStarted
        expiresAt = SystemClock.elapsedRealtime() + pairingStarted.expiresInSeconds * 1000L
        codeText.text = pairingStarted.code
        runCatching { qrImage.setImageBitmap(AgentQr.render(pairingStarted.approveUrl, QR_PX)) }
        say(R.string.agent_login_waiting)
        schedulePoll()
    }

    private fun schedulePoll() {
        val interval = (started?.intervalSeconds ?: 2) * 1000L
        main.postDelayed(::poll, interval)
    }

    private fun poll() {
        val client = pairing ?: return
        val current = started ?: return
        if (!active) return
        if (SystemClock.elapsedRealtime() > expiresAt) return renew()
        worker.execute {
            val result = runCatching { client.poll(current.pairId) }
            main.post {
                if (!active) return@post
                when (val state = result.getOrNull()) {
                    is ServerPairing.Poll.Approved -> save(state.token)
                    ServerPairing.Poll.Expired -> renew()
                    ServerPairing.Poll.Pending -> schedulePoll()
                    null -> {
                        Log.w(TAG, "pairing poll failed", result.exceptionOrNull())
                        say(R.string.agent_login_offline)
                        schedulePoll()
                    }
                }
            }
        }
    }

    private fun renew() {
        say(R.string.agent_login_renewing)
        begin()
    }

    private fun save(token: String) {
        val saved = runCatching { AgentHomeConfig(activity).agentToken = token }
        saved.exceptionOrNull()?.let {
            Log.w(TAG, "saving token failed", it)
            return say(R.string.agent_login_save_failed)
        }
        signedIn = true
        say(R.string.agent_login_done)
        main.postDelayed({ modal.close() }, CLOSE_DELAY_MS)
    }

    private fun say(message: Int) {
        statusText.setText(message)
    }

    private fun finish() {
        active = false
        main.removeCallbacksAndMessages(null)
        worker.shutdownNow()
        dialog.dismiss()
        onClosed(signedIn)
    }

    private companion object {
        const val TAG = "AgentLogin"
        const val QR_PX = 352
        const val RETRY_MS = 4_000L
        const val CLOSE_DELAY_MS = 700L
    }
}
