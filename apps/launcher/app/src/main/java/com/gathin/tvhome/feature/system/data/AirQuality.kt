package com.gathin.tvhome.feature.system.data

import android.content.Context
import com.gathin.tvhome.R
import org.json.JSONArray
import org.json.JSONException
import java.io.IOException
import java.time.LocalDate
import java.util.Locale


object AirQuality {

    enum class Level(val upTo: Int) {
        GOOD(50),
        MODERATE(100),
        SENSITIVE(150),
        UNHEALTHY(200),
        VERY_UNHEALTHY(300),
        HAZARDOUS(Int.MAX_VALUE)
    }

    const val STALE_MS = 30 * 60_000L
    private const val PREFS = "air"

    class Report(
        @JvmField val aqi: Int,
        @JvmField val pm25: Int,
        @JvmField val tomorrowMax: Int,
        @JvmField val fetchedAt: Long
    ) {
        fun stale(now: Long): Boolean = now - fetchedAt > STALE_MS
    }

    @JvmStatic
    fun level(aqi: Int): Level {
        for (l in Level.entries) {
            if (aqi <= l.upTo) return l
        }
        return Level.HAZARDOUS
    }

    @JvmStatic
    fun label(level: Level): Int = when (level) {
        Level.GOOD -> R.string.aqi_good
        Level.MODERATE -> R.string.aqi_moderate
        Level.SENSITIVE -> R.string.aqi_sensitive
        Level.UNHEALTHY -> R.string.aqi_unhealthy
        Level.VERY_UNHEALTHY -> R.string.aqi_very_unhealthy
        Level.HAZARDOUS -> R.string.aqi_hazardous
    }

    @JvmStatic
    fun color(level: Level): Int = when (level) {
        Level.GOOD -> -0xff0100
        Level.MODERATE -> -0x100
        Level.SENSITIVE -> -0x8200
        Level.UNHEALTHY -> -0x10000
        Level.VERY_UNHEALTHY -> -0x70c069
        Level.HAZARDOUS -> -0x81ffdd
    }

    @JvmStatic
    fun cached(context: Context): Report? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains("fetched")) return null
        return Report(p.getInt("aqi", 0), p.getInt("pm25", 0), p.getInt("tomorrow", -1), p.getLong("fetched", 0))
    }

    @JvmStatic
    @Throws(IOException::class, JSONException::class)
    fun fetch(context: Context, city: String): Report {
        val at = Weather.place(context, city)
        val f = Weather.get(
            String.format(
                Locale.US,
                "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.4f&longitude=%.4f" +
                        "&current=us_aqi,pm2_5&hourly=us_aqi&timezone=auto&forecast_days=2",
                at.lat, at.lon
            )
        )
        val current = f.getJSONObject("current")
        val aqi = Math.round(current.getDouble("us_aqi")).toInt()
        val pm25 = Math.round(current.getDouble("pm2_5")).toInt()
        val today = current.getString("time").substring(0, 10)
        val tomorrow = LocalDate.parse(today).plusDays(1).toString()
        val hourly = f.getJSONObject("hourly")
        val worst = worstOn(tomorrow, hourly.getJSONArray("time"), hourly.getJSONArray("us_aqi"))
        val now = System.currentTimeMillis()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("aqi", aqi).putInt("pm25", pm25)
            .putInt("tomorrow", worst).putLong("fetched", now).apply()
        return Report(aqi, pm25, worst, now)
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun worstOn(date: String, times: JSONArray, values: JSONArray): Int {
        var worst = -1
        var i = 0
        while (i < times.length() && i < values.length()) {
            if (times.getString(i).startsWith(date) && !values.isNull(i)) {
                worst = maxOf(worst, Math.round(values.getDouble(i)).toInt())
            }
            i++
        }
        return worst
    }
}
