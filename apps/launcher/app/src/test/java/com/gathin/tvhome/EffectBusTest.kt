package com.gathin.tvhome

import com.gathin.tvhome.core.util.EffectBus
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class EffectBusTest {

    @Test
    fun testEmitBuffersUntilCollected() = runBlocking {
        val bus = EffectBus<String>()
        bus.emit("First")
        bus.emit("Second")

        val received = mutableListOf<String>()
        val job = launch {
            bus.collect { received.add(it) }
        }

        // Wait brief moment for collection
        kotlinx.coroutines.yield()
        assertEquals(listOf("First", "Second"), received)

        bus.emit("Third")
        kotlinx.coroutines.yield()
        assertEquals(listOf("First", "Second", "Third"), received)

        job.cancel()
    }

    @Test
    fun testFirstReturnsImmediateBuffered() = runBlocking {
        val bus = EffectBus<Int>()
        bus.emit(42)

        val item = bus.first()
        assertEquals(42, item)
    }

    @Test
    fun testFirstSuspendsUntilEmitted() = runBlocking {
        val bus = EffectBus<String>()
        val deferred = async { bus.first() }

        kotlinx.coroutines.yield()
        bus.emit("Hello World")

        val result = deferred.await()
        assertEquals("Hello World", result)
    }
}
