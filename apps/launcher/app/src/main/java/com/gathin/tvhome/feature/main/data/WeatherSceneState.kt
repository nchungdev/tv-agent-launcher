package com.gathin.tvhome.feature.main.data

import com.gathin.tvhome.feature.system.data.Weather
import java.time.Instant
import java.time.ZoneId

data class WeatherSceneState(
    val sky: Weather.Sky,
    val day: Boolean,
    val clouds: Int,
    val freshness: Freshness
) {
    enum class Freshness { CURRENT, CACHED, EXPIRED }

    companion object {
        const val MAX_AGE_MS = 3 * 60 * 60_000L

        fun from(report: Weather.Report?, now: Long): WeatherSceneState {
            val age = report?.let { now - it.fetchedAt } ?: -1
            if (report == null || age < 0 || age >= MAX_AGE_MS) {
                return WeatherSceneState(Weather.Sky.UNKNOWN, false, 0, Freshness.EXPIRED)
            }
            val sky = Weather.sky(report.code)
            val clouds = if (report.cloudCover in 0..100) when {
                report.cloudCover < 20 -> 0
                report.cloudCover < 65 -> 1
                else -> 2
            } else when (sky) {
                Weather.Sky.CLEAR, Weather.Sky.UNKNOWN -> 0
                Weather.Sky.PARTLY -> 1
                else -> 2
            }
            return WeatherSceneState(
                sky, daytime(report, now), clouds,
                if (age < Weather.STALE_MS) Freshness.CURRENT else Freshness.CACHED
            )
        }

        fun daytime(report: Weather.Report, now: Long): Boolean {
            val zone = runCatching { ZoneId.of(report.zoneId) }.getOrDefault(ZoneId.of("UTC"))
            val local = Instant.ofEpochMilli(now).atZone(zone)
            val today = local.toLocalDate()
            val rise = when {
                report.sunrise > 0 && Instant.ofEpochMilli(report.sunrise)
                    .atZone(zone).toLocalDate() == today -> report.sunrise
                report.nextSunrise > 0 && Instant.ofEpochMilli(report.nextSunrise)
                    .atZone(zone).toLocalDate() == today -> report.nextSunrise
                else -> 0
            }
            val set = if (rise == report.sunrise) report.sunset else report.nextSunset
            if (rise > 0 && set > rise) return now in rise until set
            val observed = Instant.ofEpochMilli(report.observedAt).atZone(zone)
            if (observed.toLocalDate() == today && kotlin.math.abs(now - report.observedAt)
                < Weather.STALE_MS) return report.day
            return local.hour in 6 until 18
        }
    }
}
