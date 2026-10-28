package com.gathin.tvhome

import android.content.ContextWrapper
import android.content.SharedPreferences
import com.gathin.tvhome.feature.system.data.Weather
import com.gathin.tvhome.feature.system.data.WeatherCache
import com.gathin.tvhome.feature.system.data.WeatherLocation
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.Locale

class WeatherLocationTest {
    private val entries = HashMap<String, Any>()
    private val editor = Proxy.newProxyInstance(javaClass.classLoader,
        arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
        when {
            method.name.startsWith("put") -> { entries[args!![0] as String] = args[1]; proxy }
            method.name == "remove" -> { entries.remove(args!![0]); proxy }
            method.name == "clear" -> { entries.clear(); proxy }
            method.name == "commit" -> true
            else -> null
        }
    } as SharedPreferences.Editor
    private val prefs = Proxy.newProxyInstance(javaClass.classLoader,
        arrayOf(SharedPreferences::class.java)) { _, method, args ->
        when {
            method.name == "edit" -> editor
            method.name == "contains" -> entries.containsKey(args!![0])
            method.name.startsWith("get") && args != null -> entries[args[0]] ?: args[1]
            else -> null
        }
    } as SharedPreferences
    private val context = object : ContextWrapper(null) {
        override fun getSharedPreferences(name: String, mode: Int) = prefs
    }

    @Test fun locationIsResolvedOnceUntilUserExplicitlyResetsIt() {
        var lookups = 0
        val lookup = { lookups++; Weather.Place("Hanoi", 21.0, 105.0) }
        WeatherLocation.resolve(context, "", lookup)
        entries["place_at"] = 1L
        val language = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPANESE)
            assertEquals("Hanoi", WeatherLocation.resolve(context, "", lookup).name)
        } finally {
            Locale.setDefault(language)
        }
        assertEquals(1, lookups)
        WeatherLocation.reset(context)
        WeatherLocation.resolve(context, "", lookup)
        assertEquals(2, lookups)
        WeatherLocation.resolve(context, "Da Nang|VN", lookup)
        assertEquals(3, lookups)
    }

    @Test fun weatherAndAirQualityShareAnInitialLocationLookup() {
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val lookup = {
            calls.incrementAndGet()
            entered.countDown()
            check(release.await(3, java.util.concurrent.TimeUnit.SECONDS))
            Weather.Place("Hanoi", 21.0, 105.0)
        }
        try {
            val first = executor.submit<Weather.Place> {
                WeatherLocation.resolve(context, "", lookup)
            }
            check(entered.await(3, java.util.concurrent.TimeUnit.SECONDS))
            val second = executor.submit<Weather.Place> {
                WeatherLocation.resolve(context, "", lookup)
            }
            release.countDown()
            assertEquals("Hanoi", first.get(3, java.util.concurrent.TimeUnit.SECONDS).name)
            assertEquals("Hanoi", second.get(3, java.util.concurrent.TimeUnit.SECONDS).name)
            assertEquals(1, calls.get())
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun staleLookupCannotRestoreLocationAfterReset() {
        assertThrows(java.io.IOException::class.java) {
            WeatherLocation.resolve(context, "") {
                WeatherLocation.reset(context)
                Weather.Place("old", 0.0, 0.0)
            }
        }
        assertFalse(entries.containsKey("place_name"))
    }

    @Test fun legacyLocationAndReportRemainReadableOnlyForTheirCity() {
        entries.putAll(mapOf("place_key" to "Hanoi|VN|${Locale.getDefault().language}",
            "place_name" to "Hanoi", "lat" to 21f, "lon" to 105f,
            "place" to "Hanoi", "fetched" to 1_000_000L, "t_code" to 2))
        assertEquals("Hanoi", WeatherLocation.resolve(context, "Hanoi|VN") {
            error("legacy location should not be detected again")
        }.name)
        assertNotNull(WeatherCache(context).read("Hanoi|VN"))
        assertNull(WeatherCache(context).read("Da Nang|VN"))
    }
}
