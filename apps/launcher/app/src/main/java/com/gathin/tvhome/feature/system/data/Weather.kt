package com.gathin.tvhome.feature.system.data

import java.util.Calendar
import android.content.Context
import com.gathin.tvhome.R
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale


object Weather {

    enum class Sky { CLEAR, PARTLY, CLOUDY, FOG, RAIN, SNOW, THUNDER, UNKNOWN }

    const val STALE_MS = 30 * 60_000L
    private const val DAY_FROM_MINUTE = 360
    private const val DAY_UNTIL_MINUTE = 1110

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
        @JvmField val fetchedAt: Long,
        @JvmField val observedAt: Long = fetchedAt,
        @JvmField val zoneId: String = "UTC",
        @JvmField val sunrise: Long = 0,
        @JvmField val sunset: Long = 0,
        @JvmField val nextSunrise: Long = 0,
        @JvmField val nextSunset: Long = 0,
        @JvmField val cloudCover: Int = -1
    ) {
        fun stale(now: Long): Boolean = now < fetchedAt || now - fetchedAt >= STALE_MS
    }

    class Place(
        @JvmField val name: String,
        @JvmField val lat: Double,
        @JvmField val lon: Double
    )

    @JvmStatic
    fun sky(wmo: Int): Sky = when (wmo) {
        0 -> Sky.CLEAR
        1, 2 -> Sky.PARTLY
        3 -> Sky.CLOUDY
        45, 48 -> Sky.FOG
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> Sky.RAIN
        71, 73, 75, 77, 85, 86 -> Sky.SNOW
        95, 96, 99 -> Sky.THUNDER
        else -> Sky.UNKNOWN
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
        Sky.UNKNOWN -> R.drawable.ic_w_cloud
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
        Sky.UNKNOWN -> R.string.weather
        Sky.RAIN -> R.string.weather_rain
    }

    @JvmStatic
    fun cached(context: Context): Report? = WeatherCache(context).read(selectedCity(context))

    fun selectedCity(context: Context): String = context
        .getSharedPreferences("launcher_settings", Context.MODE_PRIVATE)
        .getString("weather_city", context.getString(R.string.weather_city).trim()) ?: ""

    @JvmStatic
    @Throws(IOException::class, JSONException::class)
    fun place(context: Context, city: String): Place =
        WeatherHttp().use { place(context, city, it) }

    private fun place(context: Context, city: String, http: WeatherHttp): Place =
        WeatherLocation.resolve(context, city) {
            val at = DoubleArray(2)
            val lang = Locale.getDefault().language
            val bar = city.indexOf('|')
            val name = if (city.isEmpty()) placeFromIp(lang, at, http)
            else if (bar < 0) geocode(city, lang, null, at, http)
            else geocode(city.substring(0, bar), lang, city.substring(bar + 1), at, http)
            Place(name, at[0], at[1])
        }

    @JvmStatic
    @Throws(IOException::class, JSONException::class)
    fun fetch(context: Context, city: String, http: WeatherHttp = WeatherHttp()): Report {
        val version = WeatherLocation.version(context)
        val at = place(context, city, http)
        val f = http.get(
            String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                        "&current=temperature_2m,weather_code,is_day,apparent_temperature" +
                        ",relative_humidity_2m,wind_speed_10m,cloud_cover" +
                        "&daily=temperature_2m_max,temperature_2m_min,weather_code" +
                        ",precipitation_probability_max,sunrise,sunset" +
                        "&timezone=auto&forecast_days=2&timeformat=unixtime",
                at.lat, at.lon
            )
        )
        val r = parse(at, f, System.currentTimeMillis())
        if (WeatherLocation.version(context) != version) {
            throw IOException("weather location changed")
        }
        return r
    }

    fun parse(at: Place, f: JSONObject, fetchedAt: Long): Report {
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
            max.getDouble(1), min.getDouble(1), codes.getInt(1),
            if (rain.isNull(1)) 0 else rain.getInt(1),
            fetchedAt, current.optLong("time", fetchedAt / 1000) * 1000,
            f.optString("timezone", "UTC"),
            daily.optJSONArray("sunrise")?.optLong(0)?.times(1000) ?: 0,
            daily.optJSONArray("sunset")?.optLong(0)?.times(1000) ?: 0,
            daily.optJSONArray("sunrise")?.optLong(1)?.times(1000) ?: 0,
            daily.optJSONArray("sunset")?.optLong(1)?.times(1000) ?: 0,
            current.optInt("cloud_cover", -1)
        )

        if (!r.temp.isFinite() || !r.high.isFinite() || !r.low.isFinite()) {
            throw IOException("invalid weather report")
        }
        return r
    }

    private fun placeFromIp(lang: String, at: DoubleArray, http: WeatherHttp): String {
        var city: String
        var country: String?
        try {
            val ip = http.get("https://ipwho.is/")
            if (!ip.optBoolean("success", false)) throw IOException("ipwho.is: no answer")
            city = ip.getString("city")
            country = if (ip.has("country_code")) ip.getString("country_code") else null
            at[0] = ip.getDouble("latitude")
            at[1] = ip.getDouble("longitude")
        } catch (_: Exception) {
            val ip = http.get("https://get.geojs.io/v1/ip/geo.json")
            city = ip.getString("city")
            country = if (ip.has("country_code")) ip.getString("country_code") else null
            at[0] = ip.getString("latitude").toDouble()
            at[1] = ip.getString("longitude").toDouble()
        }
        return try {
            geocode(city, lang, country, DoubleArray(2), http)
        } catch (_: Exception) {
            city
        }
    }

    private fun geocode(
        city: String, lang: String, country: String?, at: DoubleArray, http: WeatherHttp
    ): String {
        val name = URLEncoder.encode(city, "UTF-8")
        val g = http.get(
            "https://geocoding-api.open-meteo.com/v1/search?count=5&language=$lang&name=$name"
        )
        val results = g.optJSONArray("results")
        if (results != null) {
            for (i in 0 until results.length()) {
                val r = results.getJSONObject(i)
                if (country != null && !country.equalsIgnoreCase(r.optString("country_code"))) {
                    continue
                }
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
    fun get(url: String): JSONObject = WeatherHttp().use { it.get(url) }
}
