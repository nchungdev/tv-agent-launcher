package com.gathin.tvhome.feature.system.data

import java.util.Calendar
import android.content.Context
import com.gathin.tvhome.R
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale


object Weather {

    enum class Sky { CLEAR, PARTLY, CLOUDY, FOG, RAIN, SNOW, THUNDER }

    const val STALE_MS = 30 * 60_000L
    private const val DAY_FROM_MINUTE = 360
    private const val DAY_UNTIL_MINUTE = 1110
    private const val PLACE_MS = 24 * 60 * 60_000L
    private const val TIMEOUT_MS = 8000
    private const val PREFS = "weather"

    class Report(
        @JvmField val place: String,
        @JvmField val temp: Double,
        @JvmField val high: Double,
        @JvmField val low: Double,
        @JvmField val code: Int,
        @JvmField val day: Boolean,
        @JvmField val feels: Double,
        @JvmField val humidity: Int,
        @JvmField val wind: Double,
        @JvmField val tomorrowHigh: Double,
        @JvmField val tomorrowLow: Double,
        @JvmField val tomorrowCode: Int,
        @JvmField val tomorrowRain: Int,
        @JvmField val fetchedAt: Long
    ) {
        fun stale(now: Long): Boolean = now - fetchedAt > STALE_MS
    }

    class Place(
        @JvmField val name: String,
        @JvmField val lat: Double,
        @JvmField val lon: Double
    )

    @JvmStatic
    fun sky(wmo: Int): Sky = when {
        wmo <= 0 -> Sky.CLEAR
        wmo <= 2 -> Sky.PARTLY
        wmo == 3 -> Sky.CLOUDY
        wmo == 45 || wmo == 48 -> Sky.FOG
        (wmo in 71..77) || wmo == 85 || wmo == 86 -> Sky.SNOW
        wmo >= 95 -> Sky.THUNDER
        else -> Sky.RAIN
    }

    @JvmStatic
    fun isDaytime(): Boolean {
        val now = Calendar.getInstance()
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return minute in DAY_FROM_MINUTE until DAY_UNTIL_MINUTE
    }

    fun icon(sky: Sky, day: Boolean): Int = when (sky) {
        Sky.CLEAR -> if (day) R.drawable.ic_w_sunny else R.drawable.ic_w_clear_night
        Sky.PARTLY -> if (day) R.drawable.ic_w_partly_day else R.drawable.ic_w_partly_night
        Sky.CLOUDY -> R.drawable.ic_w_cloud
        Sky.FOG -> R.drawable.ic_w_fog
        Sky.SNOW -> R.drawable.ic_w_snow
        Sky.THUNDER -> R.drawable.ic_w_thunder
        Sky.RAIN -> R.drawable.ic_w_rain
    }

    @JvmStatic
    fun label(sky: Sky): Int = when (sky) {
        Sky.CLEAR -> R.string.weather_clear
        Sky.PARTLY -> R.string.weather_partly
        Sky.CLOUDY -> R.string.weather_cloudy
        Sky.FOG -> R.string.weather_fog
        Sky.SNOW -> R.string.weather_snow
        Sky.THUNDER -> R.string.weather_thunder
        Sky.RAIN -> R.string.weather_rain
    }

    @JvmStatic
    fun cached(context: Context): Report? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains("fetched")) return null
        val fetched = if (p.contains("t_code")) p.getLong("fetched", 0) else 0
        return Report(
            p.getString("place", "") ?: "",
            p.getFloat("temp", 0f).toDouble(),
            p.getFloat("high", 0f).toDouble(),
            p.getFloat("low", 0f).toDouble(),
            p.getInt("code", 0),
            p.getBoolean("day", true),
            p.getFloat("feels", 0f).toDouble(),
            p.getInt("humidity", 0),
            p.getFloat("wind", 0f).toDouble(),
            p.getFloat("t_high", 0f).toDouble(),
            p.getFloat("t_low", 0f).toDouble(),
            p.getInt("t_code", 0),
            p.getInt("t_rain", 0),
            fetched
        )
    }

    @JvmStatic
    @Throws(IOException::class, JSONException::class)
    fun place(context: Context, city: String): Place {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lang = Locale.getDefault().language
        val now = System.currentTimeMillis()
        val placeKey = "$city|$lang"
        if (placeKey != p.getString("place_key", null) || now - p.getLong("place_at", 0) > PLACE_MS || !p.contains("place_name")) {
            val at = DoubleArray(2)
            val bar = city.indexOf('|')
            val name = if (city.isEmpty()) placeFromIp(lang, at)
            else if (bar < 0) geocode(city, lang, null, at)
            else geocode(city.substring(0, bar), lang, city.substring(bar + 1), at)

            p.edit().putString("place_key", placeKey).putLong("place_at", now).putString("place_name", name)
                .putFloat("lat", at[0].toFloat()).putFloat("lon", at[1].toFloat()).apply()
        }
        return Place(
            p.getString("place_name", "") ?: "",
            p.getFloat("lat", 0f).toDouble(),
            p.getFloat("lon", 0f).toDouble()
        )
    }

    @JvmStatic
    @Throws(IOException::class, JSONException::class)
    fun fetch(context: Context, city: String): Report {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val at = place(context, city)
        val f = get(
            String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                        "&current=temperature_2m,weather_code,is_day,apparent_temperature,relative_humidity_2m,wind_speed_10m" +
                        "&daily=temperature_2m_max,temperature_2m_min,weather_code,precipitation_probability_max" +
                        "&timezone=auto&forecast_days=2",
                at.lat, at.lon
            )
        )
        val current = f.getJSONObject("current")
        val daily = f.getJSONObject("daily")
        val max = daily.getJSONArray("temperature_2m_max")
        val min = daily.getJSONArray("temperature_2m_min")
        val codes = daily.getJSONArray("weather_code")
        val rain = daily.getJSONArray("precipitation_probability_max")

        val r = Report(
            at.name, current.getDouble("temperature_2m"), max.getDouble(0), min.getDouble(0),
            current.getInt("weather_code"), current.optInt("is_day", 1) == 1,
            current.optDouble("apparent_temperature", current.getDouble("temperature_2m")),
            current.optInt("relative_humidity_2m", 0), current.optDouble("wind_speed_10m", 0.0),
            max.getDouble(1), min.getDouble(1), codes.getInt(1), if (rain.isNull(1)) 0 else rain.getInt(1), now
        )

        p.edit().putString("place", r.place).putFloat("temp", r.temp.toFloat()).putFloat("high", r.high.toFloat())
            .putFloat("low", r.low.toFloat()).putInt("code", r.code).putBoolean("day", r.day)
            .putFloat("feels", r.feels.toFloat()).putInt("humidity", r.humidity).putFloat("wind", r.wind.toFloat())
            .putFloat("t_high", r.tomorrowHigh.toFloat()).putFloat("t_low", r.tomorrowLow.toFloat())
            .putInt("t_code", r.tomorrowCode).putInt("t_rain", r.tomorrowRain).putLong("fetched", now).apply()
        return r
    }

    private fun placeFromIp(lang: String, at: DoubleArray): String {
        var city: String
        var country: String?
        try {
            val ip = get("https://ipwho.is/")
            if (!ip.optBoolean("success", false)) throw IOException("ipwho.is: no answer")
            city = ip.getString("city")
            country = if (ip.has("country_code")) ip.getString("country_code") else null
            at[0] = ip.getDouble("latitude")
            at[1] = ip.getDouble("longitude")
        } catch (_: Exception) {
            val ip = get("https://get.geojs.io/v1/ip/geo.json")
            city = ip.getString("city")
            country = if (ip.has("country_code")) ip.getString("country_code") else null
            at[0] = ip.getString("latitude").toDouble()
            at[1] = ip.getString("longitude").toDouble()
        }
        return try {
            geocode(city, lang, country, DoubleArray(2))
        } catch (_: Exception) {
            city
        }
    }

    private fun geocode(city: String, lang: String, country: String?, at: DoubleArray): String {
        val g = get(
            "https://geocoding-api.open-meteo.com/v1/search?count=5&language=$lang&name=${URLEncoder.encode(city, "UTF-8")}"
        )
        val results = g.optJSONArray("results")
        if (results != null) {
            for (i in 0 until results.length()) {
                val r = results.getJSONObject(i)
                if (country != null && !country.equalsIgnoreCase(r.optString("country_code"))) continue
                at[0] = r.getDouble("latitude")
                at[1] = r.getDouble("longitude")
                return r.getString("name")
            }
        }
        throw IOException("no place called $city")
    }

    private fun String.equalsIgnoreCase(other: String?): Boolean =
        this.equals(other, ignoreCase = true)

    @JvmStatic
    @Throws(IOException::class, JSONException::class)
    fun get(url: String): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = TIMEOUT_MS
        c.readTimeout = TIMEOUT_MS
        c.setRequestProperty("User-Agent", "tvhome-launcher")
        try {
            val code = c.responseCode
            if (code != 200) throw IOException("$url: HTTP $code")
            c.inputStream.use { `in` ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(4096)
                var n: Int
                while (`in`.read(buf).also { n = it } > 0) out.write(buf, 0, n)
                return JSONObject(out.toString("UTF-8"))
            }
        } finally {
            c.disconnect()
        }
    }
}
