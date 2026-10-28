package com.gathin.tvhome

import com.gathin.tvhome.feature.main.data.WeatherRefreshPolicy
import com.gathin.tvhome.feature.main.data.WeatherSceneState
import com.gathin.tvhome.feature.system.data.Weather
import com.gathin.tvhome.feature.system.data.WeatherCache
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class WeatherSceneTest {
    private val now = Instant.parse("2026-10-28T10:59:00Z").toEpochMilli()
    private val rise = Instant.parse("2026-10-27T23:00:00Z").toEpochMilli()
    private val set = Instant.parse("2026-10-28T11:00:00Z").toEpochMilli()

    private fun report(code: Int = 2, fetched: Long = now, cover: Int = 40) = Weather.Report(
        "Hanoi", 28.0, 30.0, 24.0, code, true, 30.0, 70, 8.0, 29.0, 23.0, 3, 20,
        fetched, now, "Asia/Ho_Chi_Minh", rise, set, rise + DAY, set + DAY, cover
    )

    @Test fun unknownCodesNeverPretendClearOrRain() {
        for (code in listOf(-1, 4, 44, 70, 100, 999)) {
            assertEquals(Weather.Sky.UNKNOWN, Weather.sky(code))
        }
    }

    @Test fun sunsetUsesLocationTimeAndReevaluatesWithoutFetching() {
        val report = report()
        assertTrue(WeatherSceneState.from(report, set - 1).day)
        assertFalse(WeatherSceneState.from(report, set).day)
        assertTrue(WeatherSceneState.daytime(report, rise + DAY))
        assertFalse(WeatherSceneState.daytime(report, rise + DAY - 1))
    }

    @Test fun missingAstronomyHasLocalFallbackAndInvalidZoneDoesNotCrash() {
        val old = Weather.Report("", 0.0, 0.0, 0.0, 0, true, 0.0, 0, 0.0,
            0.0, 0.0, 0, 0, now, now - DAY, "Asia/Tokyo")
        assertFalse(WeatherSceneState.daytime(old, now))
        val json = WeatherCache.encode("", old).put("zoneId", "bad-zone")
        assertTrue(WeatherSceneState.daytime(WeatherCache.decode(json), now))
    }

    @Test fun cacheAgeBoundariesAndFutureTimestamps() {
        assertEquals(WeatherSceneState.Freshness.CURRENT,
            WeatherSceneState.from(report(), now + Weather.STALE_MS - 1).freshness)
        assertEquals(WeatherSceneState.Freshness.CACHED,
            WeatherSceneState.from(report(), now + Weather.STALE_MS).freshness)
        for (at in listOf(now - 1, now + WeatherSceneState.MAX_AGE_MS)) {
            assertEquals(Weather.Sky.UNKNOWN, WeatherSceneState.from(report(), at).sky)
        }
        assertEquals(Weather.Sky.UNKNOWN, WeatherSceneState.from(null, now).sky)
    }

    @Test fun oldSerializedCacheDefaultsNewFields() {
        val json = WeatherCache.encode("Hanoi|VN", report())
        for (key in listOf("zoneId", "sunrise", "sunset", "nextSunrise", "nextSunset",
            "observedAt", "cloudCover")) json.remove(key)
        val decoded = WeatherCache.decode(json)
        assertEquals(now, decoded.observedAt)
        assertEquals(0L, decoded.sunrise)
        assertEquals(-1, decoded.cloudCover)
        assertEquals(Weather.Sky.PARTLY, WeatherSceneState.from(decoded, now).sky)
    }

    @Test fun apiEpochTimesAreNotShiftedTwiceByTimezone() {
        val json = JSONObject("""{"timezone":"Asia/Ho_Chi_Minh",
            "current":{"time":1793185200,"temperature_2m":28,"weather_code":2,
            "cloud_cover":72,"is_day":1},"daily":{"temperature_2m_max":[30,31],
            "temperature_2m_min":[23,24],"weather_code":[2,3],
            "precipitation_probability_max":[10,null],"sunrise":[1793142000,1793228400],
            "sunset":[1793185200,1793271600]}}""")
        val report = Weather.parse(Weather.Place("Hanoi", 21.0, 105.0), json, now)
        assertEquals(1793185200000L, report.observedAt)
        assertEquals(1793142000000L, report.sunrise)
        assertEquals(now, report.fetchedAt)
        assertEquals(0, report.tomorrowRain)
        assertEquals(2, WeatherSceneState.from(report, now).clouds)
    }

    @Test fun retryUsesElapsedTimeAndResetsAfterSuccess() {
        val policy = WeatherRefreshPolicy()
        var elapsed = 100L
        for (minutes in listOf(5, 15, 30, 30)) {
            policy.failed(elapsed)
            val due = elapsed + minutes * 60_000L
            assertFalse(policy.due(null, now + DAY, due - 1))
            assertTrue(policy.due(null, now - DAY, due))
            elapsed = due
        }
        policy.reset()
        assertTrue(policy.due(null, now, elapsed))
        assertFalse(policy.due(report(), now, elapsed))
        assertTrue(policy.due(report(fetched = now + 1), now, elapsed))
    }

    companion object { private const val DAY = 86_400_000L }
}
