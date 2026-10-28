package com.gathin.tvhome

import com.gathin.tvhome.feature.main.coordinator.HomeWeatherCoordinator
import com.gathin.tvhome.feature.system.data.Weather
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class HomeWeatherCoordinatorTest {
    private fun report(place: String) = Weather.Report(place, 28.0, 30.0, 20.0, 2, true,
        29.0, 50, 10.0, 31.0, 22.0, 2, 0, 1_000_000L)

    private class Pending(val result: Weather.Report) : HomeWeatherCoordinator.Request {
        val gate = CountDownLatch(1)
        var cancelled = false
        override fun load(): Weather.Report {
            check(gate.await(5, TimeUnit.SECONDS))
            return result
        }
        override fun cancel() { cancelled = true; gate.countDown() }
    }

    @Test fun coalescesRefreshCancelsOnPauseAndReloadsOnResume() = runBlocking {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val requests = Channel<Pending>(Channel.UNLIMITED)
        val changes = Channel<Weather.Report?>(Channel.UNLIMITED)
        val saved = ArrayList<Weather.Report>()
        val source = object : HomeWeatherCoordinator.Source {
            override fun cached(city: String): Weather.Report? = null
            override fun save(city: String, report: Weather.Report) { saved.add(report) }
            override fun request(city: String): HomeWeatherCoordinator.Request =
                Pending(report(city)).also { requests.trySend(it) }
        }
        val coordinator = HomeWeatherCoordinator(scope, source, { "Hanoi" },
            { value, _ -> changes.trySend(value) }, { 1_000_000L }, { 100L })
        try {
            withContext(dispatcher) { coordinator.start(); coordinator.start() }
            val first = withTimeout(3000) { requests.receive() }
            withContext(dispatcher) { repeat(4) { coordinator.tick(true) } }
            assertTrue(requests.tryReceive().isFailure)
            withContext(dispatcher) { coordinator.stop(); coordinator.stop() }
            assertTrue(first.cancelled)
            withContext(dispatcher) { coordinator.start() }
            val second = withTimeout(3000) { requests.receive() }
            second.gate.countDown()
            val shown = withTimeout(3000) {
                var value: Weather.Report? = null
                while (value == null) value = changes.receive()
                value
            }
            assertEquals("Hanoi", shown.place)
            withContext(dispatcher) { assertEquals(1, saved.size) }
        } finally {
            withContext(dispatcher) { coordinator.stop() }
            scope.cancel()
            dispatcher.close()
        }
    }

    @Test fun citySwitchNeverSavesOrPublishesLateResultFromOldLocation() = runBlocking {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val requests = Channel<Pending>(Channel.UNLIMITED)
        val shown = Channel<Weather.Report>(Channel.UNLIMITED)
        val saved = ArrayList<String>()
        var city = "Hanoi"
        val source = object : HomeWeatherCoordinator.Source {
            override fun cached(city: String): Weather.Report? = null
            override fun save(city: String, report: Weather.Report) { saved.add(city) }
            override fun request(city: String): HomeWeatherCoordinator.Request =
                Pending(report(city)).also { requests.trySend(it) }
        }
        val coordinator = HomeWeatherCoordinator(scope, source, { city },
            { value, _ -> if (value != null) shown.trySend(value) }, { 1_000_000L }, { 100L })
        try {
            withContext(dispatcher) { coordinator.start() }
            val old = withTimeout(3000) { requests.receive() }
            withContext(dispatcher) { city = "Da Nang"; coordinator.tick() }
            val current = withTimeout(3000) { requests.receive() }
            assertTrue(old.cancelled)
            current.gate.countDown()
            assertEquals("Da Nang", withTimeout(3000) { shown.receive() }.place)
            withContext(dispatcher) { assertEquals(listOf("Da Nang"), saved) }
        } finally {
            withContext(dispatcher) { coordinator.stop() }
            scope.cancel()
            dispatcher.close()
        }
    }
}
