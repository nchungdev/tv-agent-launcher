package com.gathin.tvhome.feature.system.data

import android.content.Context
import org.json.JSONObject
import java.util.Locale

class WeatherCache(context: Context) {
    private val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)

    fun read(city: String): Weather.Report? = runCatching {
        val key = "$city|${Locale.getDefault().language}"
        val raw = prefs.getString("report_v2", null)
        if (raw != null) {
            val json = JSONObject(raw)
            if (json.optString("key") != key) return null
            decode(json)
        } else {
            if (prefs.getString("place_key", null) != key || !prefs.contains("fetched")) return null
            Weather.Report(
                prefs.getString("place", "") ?: "",
                prefs.getFloat("temp", 0f).toDouble(), prefs.getFloat("high", 0f).toDouble(),
                prefs.getFloat("low", 0f).toDouble(), prefs.getInt("code", -1),
                prefs.getBoolean("day", true), prefs.getFloat("feels", 0f).toDouble(),
                prefs.getInt("humidity", 0), prefs.getFloat("wind", 0f).toDouble(),
                prefs.getFloat("t_high", 0f).toDouble(), prefs.getFloat("t_low", 0f).toDouble(),
                prefs.getInt("t_code", -1), prefs.getInt("t_rain", 0),
                if (prefs.contains("t_code")) prefs.getLong("fetched", 0) else 0
            )
        }
    }.getOrNull()

    fun write(city: String, report: Weather.Report) {
        prefs.edit().putString("report_v2", encode(city, report).toString()).apply()
    }

    companion object {
        fun encode(city: String, r: Weather.Report): JSONObject = JSONObject().apply {
            put("key", "$city|${Locale.getDefault().language}")
            put("place", r.place)
            put("temp", r.temp)
            put("high", r.high)
            put("low", r.low)
            put("code", r.code)
            put("day", r.day)
            put("feels", r.feels)
            put("humidity", r.humidity)
            put("wind", r.wind)
            put("tomorrowHigh", r.tomorrowHigh)
            put("tomorrowLow", r.tomorrowLow)
            put("tomorrowCode", r.tomorrowCode)
            put("tomorrowRain", r.tomorrowRain)
            put("fetchedAt", r.fetchedAt)
            put("observedAt", r.observedAt)
            put("zoneId", r.zoneId)
            put("sunrise", r.sunrise)
            put("sunset", r.sunset)
            put("nextSunrise", r.nextSunrise)
            put("nextSunset", r.nextSunset)
            put("cloudCover", r.cloudCover)
        }

        fun decode(j: JSONObject): Weather.Report = Weather.Report(
            j.optString("place"), j.getDouble("temp"), j.getDouble("high"), j.getDouble("low"),
            j.optInt("code", -1), j.optBoolean("day", true), j.optDouble("feels"),
            j.optInt("humidity"), j.optDouble("wind"), j.optDouble("tomorrowHigh"),
            j.optDouble("tomorrowLow"), j.optInt("tomorrowCode", -1), j.optInt("tomorrowRain"),
            j.optLong("fetchedAt"), j.optLong("observedAt", j.optLong("fetchedAt")),
            j.optString("zoneId", "UTC"), j.optLong("sunrise"), j.optLong("sunset"),
            j.optLong("nextSunrise"), j.optLong("nextSunset"), j.optInt("cloudCover", -1)
        )
    }
}
