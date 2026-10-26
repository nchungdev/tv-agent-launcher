package com.gathin.tvhome.feature.settings.widget
import com.gathin.tvhome.feature.settings.data.*
import com.gathin.tvhome.feature.settings.ui.*

import android.app.Activity
import android.app.AlertDialog
import com.gathin.tvhome.feature.smarthome.data.HassClient
import com.gathin.tvhome.feature.smarthome.data.SmartHomeDevice
import com.gathin.tvhome.feature.agent.data.LocalPairingServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


data class HaPingResult(
    val count: Int,
    val lights: Int,
    val switches: Int,
    val sensors: Int,
    val tempDevice: SmartHomeDevice?
)

object HomeAssistantSettingsHelper {

    suspend fun ping(
        url: String,
        secret: String,
        tempId: String
    ): Result<HaPingResult> = withContext(Dispatchers.IO) {
        if (url.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("URL trống"))
        }
        val u = HassClient.normalize(url)
        runCatching {
            val client = HassClient(u, secret)
            val entities = client.use {
                it.verify()
                it.states()
            }
            val l = entities.count { it.domain == "light" && it.available }
            val sw = entities.count { it.domain in setOf("switch", "outlet") && it.available }
            val sn = entities.count { it.domain == "sensor" && it.available }
            val temp = entities.firstOrNull { it.id == tempId }
            HaPingResult(entities.size, l, sw, sn, temp)
        }
    }

    suspend fun chooseTemperature(
        activity: Activity,
        url: String,
        secret: String,
        onChosen: (chosenId: String) -> Unit
    ) {
        if (url.isBlank() || secret.isBlank()) return
        val u = url.trim()
        val sensors = withContext(Dispatchers.IO) {
            runCatching {
                val client = HassClient(u, secret)
                client.use { it.states().filter { e -> e.indoorSensor } }
            }.getOrDefault(emptyList())
        }
        val labels = arrayOf("Không chọn") + sensors.map { it.name }.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle("Chọn cảm biến nhiệt độ phòng")
            .setItems(labels) { _, idx ->
                onChosen(sensors.getOrNull(idx - 1)?.id ?: "")
            }.show()
    }

    fun startPairing(
        serverUrl: String,
        onPaired: (url: String, token: String, detail: String) -> Unit
    ): Pair<LocalPairingServer, String> {
        val tvIp = LocalPairingServer.getLocalIp()
        val s = LocalPairingServer(
            LocalPairingServer.ServiceType.HOME_ASSISTANT, serverUrl, onPaired
        )
        val port = s.start()
        val portalUrl = "http://$tvIp:$port"
        return Pair(s, portalUrl)
    }
}
