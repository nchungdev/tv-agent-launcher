package com.gathin.tvhome.feature.smarthome.data

import com.gathin.tvhome.feature.smarthome.data.SmartHomeAction
import com.gathin.tvhome.feature.smarthome.data.SmartHomeDevice
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap


class HassClient(address: String, private val token: String) : AutoCloseable {
    private val base = normalize(address)
    private val connections = ConcurrentHashMap.newKeySet<HttpURLConnection>()
    @Volatile private var closed = false

    fun verify() {
        require(token.isNotBlank()) { "Cần nhập token Home Assistant" }
        val reply = JSONObject(request("GET", "/api/"))
        require(reply.optString("message") == "API running.") { "Không phải API Home Assistant" }
    }

    fun states(): List<SmartHomeDevice> {
        val rows = JSONArray(request("GET", "/api/states"))
        return parseStates(rows)
    }

    fun areas(): Map<String, String> {
        val template = "[{% for s in states %}[{{ s.entity_id | tojson }}," +
            "{{ area_name(s.entity_id) | tojson }}]{% if not loop.last %},{% endif %}{% endfor %}]"
        val response = request("POST", "/api/template", JSONObject().put("template", template))
        val rows = JSONArray(response)
        return (0 until rows.length()).associate { index ->
            val item = rows.getJSONArray(index)
            item.getString(0) to if (item.isNull(1)) "" else item.getString(1)
        }
    }

    fun nextCalendarEvent(): String? {
        val rows = JSONArray(request("GET", "/api/states"))
        val now = java.time.LocalDateTime.now()
        var earliestTime: java.time.LocalDateTime? = null
        var earliestSummary: String? = null
        var isAllDay = false

        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val id = row.getString("entity_id")
            if (!id.startsWith("calendar.")) continue

            val attrs = row.optJSONObject("attributes") ?: continue
            val message = attrs.optString("message", "")
            if (message.isBlank()) continue

            val startTimeStr = attrs.optString("start_time", "")
            if (startTimeStr.isNotBlank()) {
                try {
                    val parsed = if (startTimeStr.contains("T")) {
                        java.time.OffsetDateTime.parse(startTimeStr).toLocalDateTime()
                    } else {
                        val parts = startTimeStr.split(" ")
                        val d = java.time.LocalDate.parse(parts[0])
                        val t = if (parts.size > 1) {
                            java.time.LocalTime.parse(parts[1])
                        } else {
                            java.time.LocalTime.MIDNIGHT
                        }
                        java.time.LocalDateTime.of(d, t)
                    }

                    if (parsed.isAfter(now.minusHours(1))) {
                        if (earliestTime == null || parsed.isBefore(earliestTime)) {
                            earliestTime = parsed
                            earliestSummary = message
                            isAllDay = attrs.optBoolean("all_day", false) ||
                                (parsed.hour == 0 && parsed.minute == 0)
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        if (earliestSummary != null && earliestTime != null) {
            val today = java.time.LocalDate.now()
            val eventDate = earliestTime.toLocalDate()
            val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(today, eventDate)
            val timePart = String.format("%02d:%02d", earliestTime.hour, earliestTime.minute)

            return when {
                daysBetween == 0L -> if (isAllDay) "Hôm nay: $earliestSummary"
                    else "$earliestSummary lúc $timePart"
                daysBetween == 1L -> if (isAllDay) "Ngày mai: $earliestSummary"
                    else "Ngày mai: $earliestSummary ($timePart)"
                daysBetween in 2L..7L -> "$earliestSummary (${daysBetween} ngày nữa)"
                else -> "$earliestSummary (${eventDate.dayOfMonth}/${eventDate.monthValue})"
            }
        }

        return null
    }

    fun call(entity: SmartHomeDevice, action: SmartHomeAction, values: JSONObject = JSONObject()) {
        require(action.domain == entity.domain) { "Sai loại thiết bị" }
        values.put("entity_id", entity.id)
        request("POST", "/api/services/${action.domain}/${action.service}", values)
    }

    fun callService(domain: String, service: String, entityIds: List<String>) {
        val values = JSONObject().put("entity_id", JSONArray(entityIds))
        request("POST", "/api/services/$domain/$service", values)
    }

    private fun request(method: String, path: String, body: JSONObject? = null): String {
        check(!closed) { "Kết nối đã đóng" }
        val connection = URL(base + path).openConnection() as HttpURLConnection
        connections.add(connection)
        try {
            check(!closed) { "Kết nối đã đóng" }
            connection.requestMethod = method
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 4000
            connection.readTimeout = 8000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use {
                    it.write(body.toString().toByteArray(Charsets.UTF_8))
                }
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IOException(when (status) {
                    401 -> "Token Home Assistant không hợp lệ hoặc đã hết hạn"
                    403 -> "Home Assistant từ chối quyền truy cập"
                    in 300..399 ->
                        "Địa chỉ chuyển hướng đăng nhập; hãy dùng URL HA nội bộ"
                    else -> "Home Assistant trả lỗi HTTP $status"
                })
            }
            return connection.inputStream.bufferedReader().use { reader ->
                val content = StringBuilder()
                val buffer = CharArray(4096)
                while (true) {
                    val length = reader.read(buffer)
                    if (length < 0) break
                    content.append(buffer, 0, length)
                    require(content.length <= 2_000_000) { "Phản hồi HA quá lớn" }
                }
                content.toString()
            }
        } finally {
            connections.remove(connection)
            connection.disconnect()
        }
    }

    override fun close() {
        closed = true
        connections.forEach { it.disconnect() }
        connections.clear()
    }

    companion object {
        fun normalize(raw: String): String {
            val url = URL(raw.trim())
            require(url.protocol in setOf("http", "https") && url.host.isNotBlank()) {
                "Địa chỉ phải bắt đầu bằng http:// hoặc https://"
            }
            require(url.userInfo == null && url.query == null && url.ref == null) {
                "Nhập địa chỉ máy chủ, không kèm thông tin đăng nhập"
            }
            require(url.path.isEmpty() || url.path == "/") {
                "Không thêm /lovelace vào địa chỉ API"
            }
            return "${url.protocol}://${url.authority}"
        }

        fun parseStates(rows: JSONArray): List<SmartHomeDevice> =
            (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val attributes = row.optJSONObject("attributes") ?: JSONObject()
            val id = row.getString("entity_id")
            val sensor = id.startsWith("sensor.") &&
                attributes.optString("device_class") == "temperature" &&
                attributes.optString("unit_of_measurement") in setOf("°C", "°F") &&
                !Regex("cpu|processor|battery|disk|gpu", RegexOption.IGNORE_CASE).containsMatchIn(
                    id + " " + attributes.optString("friendly_name"))

            val rawPercentage = if (attributes.has("percentage") &&
                !attributes.isNull("percentage")) {
                attributes.optInt("percentage", 0)
            } else null

            val presetMode = attributes.optString("preset_mode", "").takeIf { it.isNotBlank() }

            val rawPresetModes = ArrayList<String>()
            val modesArray = attributes.optJSONArray("preset_modes")
            if (modesArray != null) {
                for (m in 0 until modesArray.length()) {
                    val mode = modesArray.optString(m, "")
                    if (mode.isNotBlank()) rawPresetModes.add(mode)
                }
            }

            val oscillating = if (attributes.has("oscillating") &&
                !attributes.isNull("oscillating")) {
                attributes.optBoolean("oscillating", false)
            } else null

            val speedCount = attributes.optInt("speed_count", 4).coerceAtLeast(1)

            SmartHomeDevice(
                id, attributes.optString("friendly_name", id), row.optString("state", "unknown"),
                temperature = number(attributes, "temperature"),
                currentTemperature = number(attributes, "current_temperature"),
                minimum = number(attributes, "min_temp") ?: 7.0,
                maximum = number(attributes, "max_temp") ?: 35.0,
                unit = attributes.optString("unit_of_measurement", "°C"), indoorSensor = sensor,
                percentage = rawPercentage,
                presetMode = presetMode,
                presetModes = rawPresetModes,
                oscillating = oscillating,
                speedCount = speedCount
            )
        }

        private fun number(json: JSONObject, key: String): Double? =
            json.optDouble(key, Double.NaN).takeIf { it.isFinite() }
    }
}
