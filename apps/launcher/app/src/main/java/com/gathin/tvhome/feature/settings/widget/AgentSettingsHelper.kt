package com.gathin.tvhome.feature.settings.widget
import com.gathin.tvhome.feature.settings.data.*
import com.gathin.tvhome.feature.settings.ui.*

import android.app.Activity
import android.app.AlertDialog
import android.os.Handler
import com.gathin.tvhome.feature.agent.ui.AgentVoiceReply
import com.gathin.tvhome.feature.agent.data.LocalPairingServer
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService


object AgentSettingsHelper {

    fun ping(
        worker: ExecutorService,
        main: Handler,
        rawUrl: String,
        rawToken: String,
        onSuccess: (latency: Long) -> Unit,
        onError: (message: String) -> Unit
    ) {
        worker.execute {
            val startTime = System.currentTimeMillis()
            val url = if (rawUrl.endsWith("/")) rawUrl.dropLast(1) else rawUrl
            try {
                val conn = URL("$url/health").openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                if (rawToken.isNotEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer $rawToken")
                }
                val code = conn.responseCode
                val latency = System.currentTimeMillis() - startTime
                conn.disconnect()
                main.post {
                    if (code == 200) onSuccess(latency)
                    else if (code == 401) onError("Token sai (401)")
                    else onError("Lỗi HTTP $code")
                }
            } catch (err: Exception) {
                main.post { onError(err.message ?: "Mất kết nối") }
            }
        }
    }

    fun chooseVoice(activity: Activity, onChosen: (Int) -> Unit) {
        val labels = Array<CharSequence>(AgentVoiceReply.VOICE_COUNT) {
            AgentVoiceReply.voiceModeLabel(activity, it)
        }
        val current = AgentVoiceReply.voiceMode(activity)
        AlertDialog.Builder(activity)
            .setTitle("Chọn loại giọng nói")
            .setSingleChoiceItems(labels, current) { d, which ->
                AgentVoiceReply.setVoiceMode(activity, which)
                d.dismiss()
                onChosen(which)
            }.show()
    }

    fun chooseSpeed(activity: Activity, onChosen: () -> Unit) {
        val opts = AgentVoiceReply.SPEED_OPTIONS
        val labels = Array<CharSequence>(opts.size) { AgentVoiceReply.speedLabel(it) }
        val cur = AgentVoiceReply.speedIndex(activity)
        AlertDialog.Builder(activity)
            .setTitle("Chọn tốc độ đọc")
            .setSingleChoiceItems(labels, cur) { d, which ->
                AgentVoiceReply.setSpeedIndex(activity, which)
                d.dismiss()
                onChosen()
            }.show()
    }

    fun startPairing(
        serverUrl: String,
        onPaired: (url: String, token: String, detail: String) -> Unit
    ): Pair<LocalPairingServer, String> {
        val tvIp = LocalPairingServer.getLocalIp()
        val s = LocalPairingServer(
            LocalPairingServer.ServiceType.AGENT, serverUrl, onPaired
        )
        val port = s.start()
        val portalUrl = "http://$tvIp:$port"
        return Pair(s, portalUrl)
    }
}
