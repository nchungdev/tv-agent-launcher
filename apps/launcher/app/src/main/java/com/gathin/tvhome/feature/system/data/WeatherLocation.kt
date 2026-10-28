package com.gathin.tvhome.feature.system.data

import android.content.Context
import java.io.IOException
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

object WeatherLocation {
    private val pending = HashMap<String, CompletableFuture<Weather.Place>>()
    fun resolve(context: Context, city: String, lookup: () -> Weather.Place): Weather.Place {
        val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)
        val version: Long
        val future: CompletableFuture<Weather.Place>
        val owner: Boolean
        val key: String
        synchronized(this) {
            val saved = prefs.getString("place_query", null)
            val legacy = prefs.getString("place_key", null) ==
                "$city|${Locale.getDefault().language}"
            if ((saved == city || (saved == null && legacy)) && prefs.contains("place_name")) {
                return Weather.Place(prefs.getString("place_name", "") ?: "",
                    prefs.getFloat("lat", 0f).toDouble(), prefs.getFloat("lon", 0f).toDouble())
            }
            version = prefs.getLong("place_version", 0)
            key = "$version:$city"
            val existing = pending[key]
            owner = existing == null
            future = existing ?: CompletableFuture<Weather.Place>().also { pending[key] = it }
        }
        if (!owner) return try {
            future.get()
        } catch (error: ExecutionException) {
            throw IOException("weather location unavailable", error.cause)
        }
        try {
            val place = lookup()
            synchronized(this) {
                if (prefs.getLong("place_version", 0) != version) {
                    throw IOException("weather location changed")
                }
                prefs.edit().putString("place_query", city).putString("place_name", place.name)
                    .putString("place_key", "$city|${Locale.getDefault().language}")
                    .putFloat("lat", place.lat.toFloat()).putFloat("lon", place.lon.toFloat())
                    .apply()
            }
            future.complete(place)
            return place
        } catch (error: Exception) {
            future.completeExceptionally(error)
            throw error
        } finally {
            synchronized(this) { pending.remove(key) }
        }
    }

    fun version(context: Context): Long = context
        .getSharedPreferences("weather", Context.MODE_PRIVATE).getLong("place_version", 0)

    @Synchronized
    fun reset(context: Context) {
        val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)
        prefs.edit().putLong("place_version", prefs.getLong("place_version", 0) + 1)
            .remove("place_query").remove("place_key").remove("place_name")
            .remove("report_v2").remove("fetched").apply()
    }
}
