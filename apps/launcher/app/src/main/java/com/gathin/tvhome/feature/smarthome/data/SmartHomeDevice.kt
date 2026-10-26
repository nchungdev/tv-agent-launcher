package com.gathin.tvhome.feature.smarthome.data



data class SmartHomeDevice(
    val id: String,
    val name: String,
    val state: String,
    val area: String = "",
    val temperature: Double? = null,
    val currentTemperature: Double? = null,
    val minimum: Double = 7.0,
    val maximum: Double = 35.0,
    val unit: String = "°C",
    val indoorSensor: Boolean = false,
    val percentage: Int? = null,
    val presetMode: String? = null,
    val presetModes: List<String> = emptyList(),
    val oscillating: Boolean? = null,
    val speedCount: Int = 4
) {
    val domain: String get() = id.substringBefore('.')
    val available: Boolean
        get() = state.lowercase().trim() !in setOf("unknown", "unavailable", "")
}

data class SmartHomeAction(val domain: String, val service: String)

object SmartHomeActions {
    fun toggle(device: SmartHomeDevice): SmartHomeAction? {
        if (!device.available) return null
        return when (device.domain) {
            "light", "switch", "fan", "input_boolean" -> SmartHomeAction(device.domain, "toggle")
            "climate" -> SmartHomeAction(
                "climate",
                if (device.state == "off") "turn_on" else "turn_off"
            )

            "cover" -> SmartHomeAction(
                "cover",
                if (device.state == "closed") "open_cover" else "close_cover"
            )

            "scene", "script" -> SmartHomeAction(device.domain, "turn_on")
            else -> null
        }
    }
}
