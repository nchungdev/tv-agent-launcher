package com.gathin.tvhome.feature.smarthome.widget
import com.gathin.tvhome.feature.smarthome.data.*
import com.gathin.tvhome.feature.smarthome.presenter.*
import com.gathin.tvhome.feature.smarthome.ui.*

import android.view.View
import android.widget.LinearLayout
import com.gathin.tvhome.feature.smarthome.data.SmartHomeDevice
import com.gathin.tvhome.feature.settings.data.AgentHomeConfig
import java.net.URL


object SmartHomeHelper {

    fun serverHost(raw: String): String = runCatching {
        val u = URL(raw)
        val p = if (u.port != -1 && u.port != 80 && u.port != 443) ":${u.port}" else ""
        "${u.host}$p"
    }.getOrDefault(raw.removePrefix("http://").removePrefix("https://").trimEnd('/'))

    fun resolveSubtitle(
        settings: AgentHomeConfig,
        rows: List<SmartHomeDevice>,
        host: String,
        editMode: Boolean
    ): String {
        val selected = rows.find { it.id == settings.homeAssistantTemperatureEntity }
        val value = selected?.takeIf { it.indoorSensor && it.available }
            ?.state?.toDoubleOrNull()
        return if (value != null && value.isFinite()) {
            val display = "$value${selected.unit}"
            settings.saveHaTemperature(display)
            if (!editMode) "Đã kết nối tới $host · Nhiệt độ: $display" else ""
        } else {
            settings.clearHaTemperature()
            if (!editMode) "Đã kết nối tới $host" else ""
        }
    }

    fun tryUpdateInPlace(
        devices: LinearLayout,
        visible: List<SmartHomeDevice>,
        editMode: Boolean
    ): Boolean {
        if (editMode || devices.childCount == 0) return false
        for (e in visible) {
            if (devices.findViewWithTag<View>(e.id) == null) return false
        }
        for (e in visible) {
            val card = devices.findViewWithTag<View>(e.id) ?: continue
            SmartHomeRegularCards.updateCardUi(card, e)
        }
        return true
    }

    fun nextToggleState(service: String, currentState: String): String = when (service) {
        "turn_on" -> "on"
        "turn_off" -> "off"
        else -> if (currentState == "on") "off" else "on"
    }
}
