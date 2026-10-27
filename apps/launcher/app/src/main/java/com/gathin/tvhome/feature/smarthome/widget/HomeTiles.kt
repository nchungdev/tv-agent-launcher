package com.gathin.tvhome.feature.smarthome.widget

import android.content.SharedPreferences
import com.gathin.tvhome.feature.smarthome.data.SmartHomeDevice
import kotlin.math.roundToInt

data class HomeTile(
    val icon: String,
    val value: String,
    val caption: String,
    val action: String = "",
    val entities: List<String> = emptyList()
) {
    val domain: String get() = action.substringBefore('/')
    val service: String get() = action.substringAfter('/')

    fun encode(): String =
        listOf(icon, value, caption, action, entities.joinToString(",")).joinToString(SEPARATOR)

    companion object {
        private const val SEPARATOR = "\u001F"

        fun decode(line: String): HomeTile? {
            val parts = line.split(SEPARATOR)
            if (parts.size != 5) return null
            val entities = parts[4].split(',').filter { it.isNotBlank() }
            return HomeTile(parts[0], parts[1], parts[2], parts[3], entities)
        }
    }
}

object HomeTiles {
    private const val KEY = "home_tiles"

    val EMPTY = listOf(
        HomeTile("🌡", "—", "Trong nhà"),
        HomeTile("❄️", "—", "Điều hòa"),
        HomeTile("💡", "—", "Đèn")
    )

    fun load(prefs: SharedPreferences): List<HomeTile> {
        val tiles = prefs.getString(KEY, null)?.split('\n')?.mapNotNull { HomeTile.decode(it) }
        return if (tiles?.size == EMPTY.size) tiles else EMPTY
    }

    fun save(prefs: SharedPreferences, tiles: List<HomeTile>) {
        prefs.edit().putString(KEY, tiles.joinToString("\n") { it.encode() }).apply()
    }

    fun pick(states: List<SmartHomeDevice>, temperatureEntity: String): List<HomeTile> =
        listOf(indoor(states, temperatureEntity), comfort(states), lighting(states))

    private fun indoor(states: List<SmartHomeDevice>, temperatureEntity: String): HomeTile {
        val chosen = states.find { it.id == temperatureEntity && it.available }
        val sensor = chosen ?: states.find { it.indoorSensor && it.state.toDoubleOrNull() != null }
        val climate = states.find { it.domain == "climate" && it.currentTemperature != null }
        val temperature = sensor?.let { it.currentTemperature ?: it.state.toDoubleOrNull() }
            ?: climate?.currentTemperature
        val humidity = states.find {
            it.domain == "sensor" && it.unit == "%" &&
                it.id.contains("humidity", ignoreCase = true) && it.state.toDoubleOrNull() != null
        }?.state?.toDoubleOrNull()
        val value = temperature?.let { "${it.roundToInt()}°C" } ?: "—"
        val caption = humidity?.let { "Độ ẩm ${it.roundToInt()}%" } ?: "Trong nhà"
        return HomeTile("🌡", value, caption)
    }

    private fun comfort(states: List<SmartHomeDevice>): HomeTile {
        val climate = states.filter { it.domain == "climate" && it.available }
            .sortedBy { it.state == "off" }.firstOrNull()
        if (climate != null) {
            val off = climate.state == "off"
            val target = climate.temperature?.let { "${it.roundToInt()}°" } ?: "Bật"
            val action = if (off) "climate/turn_on" else "climate/turn_off"
            val value = if (off) "Tắt" else target
            return HomeTile("❄️", value, label(climate, "Điều hòa"), action, listOf(climate.id))
        }
        val device = states.find { it.domain == "fan" && it.available }
            ?: states.find { it.domain == "switch" && it.available }
            ?: return EMPTY[1]
        val icon = if (device.domain == "fan") "🌀" else "⚡"
        val fallback = if (device.domain == "fan") "Quạt" else "Công tắc"
        val value = if (device.state == "on") "Bật" else "Tắt"
        return HomeTile(icon, value, label(device, fallback), "${device.domain}/toggle",
            listOf(device.id))
    }

    private fun lighting(states: List<SmartHomeDevice>): HomeTile {
        val lit = states.filter { it.domain == "light" && it.state == "on" }
        if (lit.isNotEmpty()) {
            return HomeTile("💡", "${lit.size} bật", "Tắt hết đèn", "light/turn_off",
                lit.map { it.id })
        }
        val scene = states.find { it.domain == "scene" && it.available }
        if (scene != null) {
            return HomeTile("🎬", label(scene, "Cảnh"), "Bật cảnh", "scene/turn_on",
                listOf(scene.id))
        }
        val lights = states.filter { it.domain == "light" && it.available }
        if (lights.isEmpty()) return EMPTY[2]
        return HomeTile("💡", "Tắt", "Bật hết đèn", "light/turn_on", lights.map { it.id })
    }

    private fun label(device: SmartHomeDevice, fallback: String): String {
        val name = device.name.trim()
        val looksLikeId = !name.contains(' ') && name.any { it.isDigit() }
        return if (name.isBlank() || looksLikeId) fallback else name
    }
}
