package com.gathin.tvhome.feature.main.data

import com.gathin.tvhome.feature.system.data.Weather

class WeatherRefreshPolicy {
    private var failures = 0
    private var retryAt = 0L

    fun due(report: Weather.Report?, wall: Long, elapsed: Long): Boolean =
        elapsed >= retryAt && (report == null || report.stale(wall))

    fun failed(elapsed: Long) {
        val minutes = when (failures++) { 0 -> 5; 1 -> 15; else -> 30 }
        retryAt = elapsed + minutes * 60_000L
    }

    fun reset() {
        failures = 0
        retryAt = 0
    }
}
