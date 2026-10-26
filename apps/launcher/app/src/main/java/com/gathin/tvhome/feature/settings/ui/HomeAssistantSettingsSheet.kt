package com.gathin.tvhome.feature.settings.ui
import com.gathin.tvhome.feature.settings.data.*
import com.gathin.tvhome.feature.settings.widget.*

import android.app.Activity
import android.app.Dialog
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.settings.data.Config
import com.gathin.tvhome.core.view.ShapeBadge
import com.gathin.tvhome.core.design.M3Skin
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.feature.settings.data.AgentHomeConfig
import com.gathin.tvhome.feature.agent.ui.AgentQr
import com.gathin.tvhome.feature.agent.data.LocalPairingServer
import com.gathin.tvhome.feature.agent.data.NsdDiscoveryHelper
import com.gathin.tvhome.core.util.dialogScope
import kotlinx.coroutines.launch


class HomeAssistantSettingsSheet(
    private val activity: Activity,
    private val config: Config,
    private val onOpenSmartHome: (() -> Unit)? = null,
    private val onDismiss: (() -> Unit)? = null
) {
    private val agentConfig = AgentHomeConfig(activity)
    private val dialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)

    private lateinit var modal: CenterModal
    private lateinit var btnReload: ImageView
    private lateinit var btnSettings: ImageView
    private lateinit var subtitleText: TextView
    private lateinit var configSection: LinearLayout
    private lateinit var discoveryContainer: LinearLayout
    private lateinit var qrPairingContainer: LinearLayout
    private lateinit var connectedSection: LinearLayout
    private lateinit var temperatureChoice: TextView
    private lateinit var sheetRoot: View

    private var pairingServer: LocalPairingServer? = null
    private var foundUrl: String? = null
    private var lastCount = -1
    private val nsdHelper = NsdDiscoveryHelper(
        activity, "_home-assistant._tcp.",
        { onServerFound(it) },
        { onScanTimeout() }
    )

    fun show() {
        val view = LayoutInflater.from(activity).inflate(
            R.layout.activity_home_assistant_settings, null
        )
        sheetRoot = view
        dialog.setContentView(view)
        bindViews(view)
        setupBadge(view)
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

        val savedUrl = agentConfig.homeAssistantUrl.trim()
        val savedToken = agentConfig.homeAssistantToken.trim()
        if (savedUrl.isNotEmpty() && savedToken.isNotEmpty()) {
            showConnected(savedUrl, -1, true)
            pingConnection(savedUrl, savedToken)
        } else {
            showConfig(allowCancel = false)
        }
    }

    private fun bindViews(v: View) {
        btnReload = v.findViewById(R.id.btnReload)
        btnSettings = v.findViewById(R.id.btnSettings)
        subtitleText = v.findViewById(R.id.haSettingsSubtitle)
        configSection = v.findViewById(R.id.configSection)
        discoveryContainer = v.findViewById(R.id.discoveryContainer)
        qrPairingContainer = v.findViewById(R.id.qrPairingContainer)
        connectedSection = v.findViewById(R.id.connectedSection)
        temperatureChoice = v.findViewById(R.id.temperatureChoice)
    }

    private fun setupBadge(v: View) {
        v.findViewById<ShapeBadge>(R.id.haSettingsBadge).apply {
            setIcon(activity.getDrawable(R.drawable.ic_thermostat))
            val p = Palette.dark(Palette.currentOrBaseline())
            setColors(p.primaryContainer, p.onPrimaryContainer)
        }
    }

    private fun setupListeners(v: View) {
        btnReload.setOnClickListener {
            val u = agentConfig.homeAssistantUrl.trim()
            val t = agentConfig.homeAssistantToken.trim()
            subtitleText.text = "$u · Đang làm mới…"
            pingConnection(u, t)
        }
        btnSettings.setOnClickListener { showConfig(allowCancel = true) }
        val rescan = View.OnClickListener { startDiscovery() }
        v.findViewById<View>(R.id.scanningBanner).setOnClickListener(rescan)
        v.findViewById<View>(R.id.btnRescan).setOnClickListener(rescan)

        val target = foundUrl ?: agentConfig.homeAssistantUrl.ifBlank { DEFAULT_URL }
        val pairAction = View.OnClickListener { openQrPairing(target) }
        v.findViewById<View>(R.id.serverFoundCard).setOnClickListener(pairAction)
        v.findViewById<View>(R.id.btnConnectFound).setOnClickListener(pairAction)
        v.findViewById<View>(R.id.btnOpenQrManual).setOnClickListener {
            openQrPairing(agentConfig.homeAssistantUrl.ifBlank { DEFAULT_URL })
        }
        v.findViewById<View>(R.id.btnCancelDiscovery).setOnClickListener {
            nsdHelper.stop()
            showConnected(agentConfig.homeAssistantUrl, lastCount, true)
        }
        v.findViewById<View>(R.id.btnBackToDiscovery).setOnClickListener {
            stopPairing()
            qrPairingContainer.visibility = View.GONE
            discoveryContainer.visibility = View.VISIBLE
            startDiscovery()
        }
        v.findViewById<View>(R.id.btnCancelPairing).setOnClickListener {
            stopPairing()
            nsdHelper.stop()
            showConnected(agentConfig.homeAssistantUrl, lastCount, true)
        }
        temperatureChoice.setOnClickListener { chooseTemperature() }
        v.findViewById<View>(R.id.btnOpenSmartHome).setOnClickListener {
            modal.close()
            onOpenSmartHome?.invoke()
        }
    }

    private fun showConfig(allowCancel: Boolean) {
        btnReload.visibility = View.GONE
        btnSettings.visibility = View.GONE
        subtitleText.text = "Tìm kiếm và kết nối Home Assistant"
        stopPairing()
        qrPairingContainer.visibility = View.GONE
        discoveryContainer.visibility = View.VISIBLE
        configSection.visibility = View.VISIBLE
        connectedSection.visibility = View.GONE
        sheetRoot.findViewById<View>(R.id.btnCancelDiscovery).visibility =
            if (allowCancel) View.VISIBLE else View.GONE
        startDiscovery()
    }

    private fun startDiscovery() {
        nsdHelper.stop()
        foundUrl = null
        sheetRoot.findViewById<View>(R.id.scanningBanner).visibility = View.VISIBLE
        sheetRoot.findViewById<View>(R.id.scanProgress).visibility = View.VISIBLE
        sheetRoot.findViewById<TextView>(R.id.scanStatusText).text =
            "Đang quét tìm Home Assistant trong mạng…"
        sheetRoot.findViewById<TextView>(R.id.btnRescan).text = "Đang quét…"
        sheetRoot.findViewById<View>(R.id.serverFoundCard).visibility = View.GONE
        sheetRoot.findViewById<View>(R.id.noServerFallbackContainer).visibility = View.GONE
        nsdHelper.start()
    }

    private fun onServerFound(url: String) {
        foundUrl = url
        sheetRoot.findViewById<View>(R.id.scanProgress).visibility = View.GONE
        sheetRoot.findViewById<TextView>(R.id.scanStatusText).text =
            "Đã tìm thấy máy chủ trong mạng"
        sheetRoot.findViewById<TextView>(R.id.btnRescan).text = "Quét lại"
        sheetRoot.findViewById<TextView>(R.id.serverFoundSubtitle).text = url
        sheetRoot.findViewById<View>(R.id.serverFoundCard).visibility = View.VISIBLE
        sheetRoot.findViewById<View>(R.id.noServerFallbackContainer).visibility = View.GONE
        sheetRoot.findViewById<View>(R.id.btnConnectFound).requestFocus()
    }

    private fun onScanTimeout() {
        if (foundUrl != null) return
        sheetRoot.findViewById<View>(R.id.scanProgress).visibility = View.GONE
        sheetRoot.findViewById<TextView>(R.id.scanStatusText).text = "Quét hoàn tất"
        sheetRoot.findViewById<TextView>(R.id.btnRescan).text = "Quét lại"
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
        val (server, portal) = HomeAssistantSettingsHelper.startPairing(
            targetServerUrl
        ) { u, t, d ->
            agentConfig.homeAssistantUrl = u
            agentConfig.homeAssistantToken = t
            stopPairing()
            showConnected(u, -1, true)
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

    private fun showConnected(url: String, count: Int, online: Boolean) {
        stopPairing()
        nsdHelper.stop()
        btnReload.visibility = View.VISIBLE
        btnSettings.visibility = View.VISIBLE
        subtitleText.text = when {
            !online -> "$url · Không thể kết nối"
            count >= 0 -> "$url · 200 OK (${count} entity)"
            else -> "$url · Đang kiểm tra…"
        }
        configSection.visibility = View.GONE
        connectedSection.visibility = View.VISIBLE
        val selId = agentConfig.homeAssistantTemperatureEntity
        val defaultTempText = "Chưa chọn cảm biến"
        temperatureChoice.text = "Cảm biến nhiệt độ: ${selId.ifBlank { defaultTempText }}"
        btnReload.requestFocus()
    }

    private fun pingConnection(rawUrl: String, rawSecret: String) {
        dialog.dialogScope.launch {
            val result = HomeAssistantSettingsHelper.ping(
                rawUrl, rawSecret, agentConfig.homeAssistantTemperatureEntity
            )
            if (!dialog.isShowing) return@launch
            result.onSuccess { data ->
                sheetRoot.findViewById<TextView>(R.id.haLightsCount).text = "${data.lights} Đèn"
                sheetRoot.findViewById<TextView>(R.id.haSwitchesCount).text =
                    "${data.switches} Công tắc"
                sheetRoot.findViewById<TextView>(R.id.haSensorsCount).text =
                    "${data.sensors} Cảm biến"
                val dev = data.tempDevice
                if (dev != null) {
                    temperatureChoice.text = "Cảm biến: ${dev.name} (${dev.state} ${dev.unit})"
                }
                showConnected(rawUrl, data.count, true)
            }.onFailure { err ->
                showConnected(rawUrl, -1, false)
                subtitleText.text = "$rawUrl · ${err.message ?: "Lỗi kết nối"}"
            }
        }
    }

    private fun chooseTemperature() {
        val u = agentConfig.homeAssistantUrl.ifBlank { DEFAULT_URL }
        val secret = agentConfig.homeAssistantToken
        dialog.dialogScope.launch {
            HomeAssistantSettingsHelper.chooseTemperature(activity, u, secret) { chosenId ->
                agentConfig.homeAssistantTemperatureEntity = chosenId
                agentConfig.clearHaTemperature()
                val sel = agentConfig.homeAssistantTemperatureEntity
                temperatureChoice.text = "Cảm biến: ${sel.ifBlank { "Chưa chọn" }}"
            }
        }
    }

    private fun cleanup() {
        nsdHelper.stop()
        stopPairing()
    }

    companion object {
        const val DEFAULT_URL = "http://192.168.1.50:8123"
    }
}
