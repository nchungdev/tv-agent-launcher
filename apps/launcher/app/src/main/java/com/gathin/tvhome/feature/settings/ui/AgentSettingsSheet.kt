package com.gathin.tvhome.feature.settings.ui
import com.gathin.tvhome.feature.settings.data.*
import com.gathin.tvhome.feature.settings.widget.*

import android.app.Activity
import android.app.Dialog
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.settings.data.Config
import com.gathin.tvhome.feature.agent.data.AgentClient
import com.gathin.tvhome.core.view.ShapeBadge
import com.gathin.tvhome.core.design.M3Skin
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.feature.settings.data.AgentHomeConfig
import com.gathin.tvhome.feature.agent.ui.AgentQr
import com.gathin.tvhome.feature.agent.ui.AgentVoiceReply
import com.gathin.tvhome.feature.agent.data.LocalPairingServer
import com.gathin.tvhome.feature.agent.data.NsdDiscoveryHelper
import java.util.concurrent.Executors


class AgentSettingsSheet(
    private val activity: Activity,
    private val config: Config,
    private val onDismiss: (() -> Unit)? = null
) {
    private val agentConfig = AgentHomeConfig(activity)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val dialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)

    private lateinit var modal: CenterModal
    private lateinit var btnReload: ImageView
    private lateinit var btnSettings: ImageView
    private lateinit var subtitleText: TextView
    private lateinit var configSection: LinearLayout
    private lateinit var discoveryContainer: LinearLayout
    private lateinit var qrPairingContainer: LinearLayout
    private lateinit var connectedSection: LinearLayout
    private lateinit var sheetRoot: View

    private var pairingServer: LocalPairingServer? = null
    private var voicePreview: AgentVoiceReply? = null
    private var foundUrl: String? = null
    private var lastLatency = -1L
    private val nsdHelper = NsdDiscoveryHelper(
        activity, "_home-sentinel._tcp.",
        { onServerFound(it) },
        { onScanTimeout() }
    )

    fun show() {
        val view = LayoutInflater.from(activity).inflate(
            R.layout.activity_agent_settings, null
        )
        sheetRoot = view
        dialog.setContentView(view)
        btnReload = view.findViewById(R.id.btnReload)
        btnSettings = view.findViewById(R.id.btnSettings)
        subtitleText = view.findViewById(R.id.agentSettingsSubtitle)
        configSection = view.findViewById(R.id.configSection)
        discoveryContainer = view.findViewById(R.id.discoveryContainer)
        qrPairingContainer = view.findViewById(R.id.qrPairingContainer)
        connectedSection = view.findViewById(R.id.connectedSection)

        view.findViewById<ShapeBadge>(R.id.agentSettingsBadge).apply {
            setIcon(activity.getDrawable(R.drawable.ic_mic))
            val p = Palette.dark(Palette.currentOrBaseline())
            setColors(p.primaryContainer, p.onPrimaryContainer)
        }
        setupListeners(view)

        val scrim = view.findViewById<View>(R.id.scrim)
        val sheet = view.findViewById<View>(R.id.sheet)
        val darkPalette = Palette.dark(Palette.currentOrBaseline())
        M3Skin.apply(sheet, darkPalette, config.animations, config.reducedMotion)
        modal = CenterModal(activity, scrim, sheet, config) {
            cleanup()
            dialog.dismiss()
            onDismiss?.invoke()
        }
        scrim.setOnClickListener { modal.close() }
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                modal.close()
                true
            } else false
        }
        dialog.show()
        modal.show()

        val savedUrl = agentConfig.agentUrl.trim()
        val savedToken = agentConfig.agentToken.trim()
        if (savedUrl.isNotEmpty() && savedToken.isNotEmpty()) {
            showConnected(savedUrl, -1L, true)
            pingConnection(savedUrl, savedToken)
        } else {
            showConfig(allowCancel = false)
        }
    }



    private fun setupListeners(v: View) {
        btnReload.setOnClickListener {
            val u = agentConfig.agentUrl.trim()
            val t = agentConfig.agentToken.trim()
            subtitleText.text = "$u · Đang làm mới…"
            pingConnection(u, t)
        }
        btnSettings.setOnClickListener { showConfig(allowCancel = true) }
        val rescan = View.OnClickListener { startDiscovery() }
        v.findViewById<View>(R.id.scanningBanner).setOnClickListener(rescan)
        v.findViewById<View>(R.id.btnRescan).setOnClickListener(rescan)
        val target = foundUrl ?: agentConfig.agentUrl.ifBlank { AgentClient.baseUrl(activity) }
        val pairAction = View.OnClickListener { openQrPairing(target) }
        v.findViewById<View>(R.id.serverFoundCard).setOnClickListener(pairAction)
        v.findViewById<View>(R.id.btnConnectFound).setOnClickListener(pairAction)
        v.findViewById<View>(R.id.btnOpenQrManual).setOnClickListener {
            openQrPairing(agentConfig.agentUrl.ifBlank { AgentClient.baseUrl(activity) })
        }
        val cancelPair = View.OnClickListener {
            stopPairing()
            nsdHelper.stop()
            showConnected(agentConfig.agentUrl, lastLatency, true)
        }
        v.findViewById<View>(R.id.btnCancelDiscovery).setOnClickListener(cancelPair)
        v.findViewById<View>(R.id.btnCancelPairing).setOnClickListener(cancelPair)
        v.findViewById<View>(R.id.btnBackToDiscovery).setOnClickListener {
            stopPairing()
            qrPairingContainer.visibility = View.GONE
            discoveryContainer.visibility = View.VISIBLE
            startDiscovery()
        }
        v.findViewById<View>(R.id.voiceChoice).setOnClickListener {
            AgentSettingsHelper.chooseVoice(activity) {
                refreshVoiceOptions()
                if (it != AgentVoiceReply.VOICE_OFF) testVoice()
            }
        }
        v.findViewById<View>(R.id.speedChoice).setOnClickListener {
            AgentSettingsHelper.chooseSpeed(activity) {
                refreshVoiceOptions()
                testVoice()
            }
        }
        v.findViewById<View>(R.id.btnTestVoice).setOnClickListener { testVoice() }
    }

    private fun showConfig(allowCancel: Boolean) {
        btnReload.visibility = View.GONE
        btnSettings.visibility = View.GONE
        subtitleText.text = "Tìm kiếm và kết nối máy chủ Agent"
        stopPairing()
        qrPairingContainer.visibility = View.GONE
        discoveryContainer.visibility = View.VISIBLE
        configSection.visibility = View.VISIBLE
        connectedSection.visibility = View.GONE
        sheetRoot.findViewById<View>(R.id.btnCancelDiscovery).visibility =
            if (allowCancel) View.VISIBLE else View.GONE
        startDiscovery()
    }

    private fun setScanState(progress: Int, status: String, btn: String) {
        sheetRoot.findViewById<View>(R.id.scanProgress).visibility = progress
        sheetRoot.findViewById<TextView>(R.id.scanStatusText).text = status
        sheetRoot.findViewById<TextView>(R.id.btnRescan).text = btn
    }

    private fun startDiscovery() {
        nsdHelper.stop()
        foundUrl = null
        sheetRoot.findViewById<View>(R.id.scanningBanner).visibility = View.VISIBLE
        sheetRoot.findViewById<View>(R.id.serverFoundCard).visibility = View.GONE
        sheetRoot.findViewById<View>(R.id.noServerFallbackContainer).visibility = View.GONE
        setScanState(View.VISIBLE, "Đang quét tìm máy chủ Agent trong mạng…", "Đang quét…")

        worker.execute {
            AgentClient.discover(activity)
            val detected = AgentClient.baseUrl(activity)
            if (detected.isNotEmpty()) {
                main.post { if (dialog.isShowing) onServerFound(detected) }
            }
        }
        nsdHelper.start()
    }

    private fun onServerFound(url: String) {
        foundUrl = url
        setScanState(View.GONE, "Đã tìm thấy máy chủ trong mạng", "Quét lại")
        sheetRoot.findViewById<TextView>(R.id.serverFoundSubtitle).text = url
        sheetRoot.findViewById<View>(R.id.serverFoundCard).visibility = View.VISIBLE
        sheetRoot.findViewById<View>(R.id.noServerFallbackContainer).visibility = View.GONE
        sheetRoot.findViewById<View>(R.id.btnConnectFound).requestFocus()
    }

    private fun onScanTimeout() {
        if (foundUrl != null) return
        setScanState(View.GONE, "Quét hoàn tất", "Quét lại")
        sheetRoot.findViewById<View>(R.id.noServerFallbackContainer).visibility = View.VISIBLE
        sheetRoot.findViewById<View>(R.id.btnOpenQrManual).requestFocus()
    }

    private fun openQrPairing(targetServerUrl: String) {
        nsdHelper.stop()
        discoveryContainer.visibility = View.GONE
        qrPairingContainer.visibility = View.VISIBLE
        val disp = targetServerUrl.ifBlank { "Chưa xác định IP" }
        sheetRoot.findViewById<TextView>(R.id.qrServerTargetText).text = "Máy chủ: $disp"

        stopPairing()
        val (server, portal) = AgentSettingsHelper.startPairing(targetServerUrl) { u, t, d ->
            agentConfig.agentUrl = u
            agentConfig.agentToken = t
            stopPairing()
            showConnected(u, -1L, true)
            subtitleText.text = d
        }
        pairingServer = server
        sheetRoot.findViewById<TextView>(R.id.qrUrlText).text = portal
        val qrImg = sheetRoot.findViewById<ImageView>(R.id.qrImage)
        runCatching { qrImg.setImageBitmap(AgentQr.render(portal, 260)) }
        sheetRoot.findViewById<View>(R.id.btnBackToDiscovery).requestFocus()
    }

    private fun stopPairing() {
        pairingServer?.stop()
        pairingServer = null
    }

    private fun showConnected(url: String, latency: Long, online: Boolean) {
        stopPairing()
        nsdHelper.stop()
        btnReload.visibility = View.VISIBLE
        btnSettings.visibility = View.VISIBLE
        subtitleText.text = when {
            !online -> "$url · Không thể kết nối"
            latency >= 0 -> "$url · 200 OK (${latency}ms)"
            else -> "$url · Đang kiểm tra…"
        }
        configSection.visibility = View.GONE
        connectedSection.visibility = View.VISIBLE
        refreshVoiceOptions()
        btnReload.requestFocus()
    }

    private fun pingConnection(rawUrl: String, rawToken: String) {
        AgentSettingsHelper.ping(
            worker, main, rawUrl, rawToken,
            { latency ->
                if (!dialog.isShowing) return@ping
                lastLatency = latency
                showConnected(rawUrl, latency, true)
            },
            { msg ->
                if (dialog.isShowing) {
                    showConnected(rawUrl, -1L, false)
                    subtitleText.text = "$rawUrl · $msg"
                }
            }
        )
    }

    private fun refreshVoiceOptions() {
        val mode = AgentVoiceReply.voiceMode(activity)
        val modeName = AgentVoiceReply.voiceModeLabel(activity, mode)
        sheetRoot.findViewById<TextView>(R.id.voiceChoice).text = "Loại giọng: $modeName"
        val speed = AgentVoiceReply.speedLabel(AgentVoiceReply.speedIndex(activity))
        sheetRoot.findViewById<TextView>(R.id.speedChoice).text = "Tốc độ đọc: $speed"
        sheetRoot.findViewById<View>(R.id.btnTestVoice).visibility =
            if (mode == AgentVoiceReply.VOICE_OFF) View.GONE else View.VISIBLE
    }

    private fun testVoice() {
        val mode = AgentVoiceReply.voiceMode(activity)
        if (mode == AgentVoiceReply.VOICE_OFF) {
            AgentSettingsHelper.chooseVoice(activity) {
                refreshVoiceOptions()
                if (it != AgentVoiceReply.VOICE_OFF) testVoice()
            }
            return
        }
        val preview = voicePreview ?: AgentVoiceReply(activity, null).also { voicePreview = it }
        preview.speakLine("Xin chào, tôi là trợ lý thông minh của gia đình bạn.")
    }

    private fun cleanup() {
        nsdHelper.stop()
        stopPairing()
        voicePreview?.shutdown()
        voicePreview = null
    }
}
