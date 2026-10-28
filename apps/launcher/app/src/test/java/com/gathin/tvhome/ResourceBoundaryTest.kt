package com.gathin.tvhome

import com.gathin.tvhome.core.util.BoundedInput
import com.gathin.tvhome.core.util.EndpointPolicy
import com.gathin.tvhome.core.util.LatestWorkQueue
import java.io.StringReader
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class ResourceBoundaryTest {
    @Test fun oversizedLineFailsBeforeReadingWholeResponse() {
        val reader = StringReader("x".repeat(100_000))
        assertThrows(IOException::class.java) { BoundedInput.line(reader, 16) }
        assertEquals('x'.code, reader.read())
    }

    @Test fun boundedTextAcceptsExactLimitAndRejectsOneExtraCharacter() {
        assertEquals("abcd", BoundedInput.text(StringReader("abcd"), 4))
        assertThrows(IOException::class.java) { BoundedInput.text(StringReader("abcde"), 4) }
        assertEquals("a", BoundedInput.line(StringReader("a\r\nrest"), 3))
        assertNull(BoundedInput.line(StringReader("")))
    }

    @Test fun remoteCredentialsRequireTlsWhilePrivateNasStillWorks() {
        assertEquals("http", EndpointPolicy.validated("http://192.168.1.37:8788").scheme)
        assertEquals("https", EndpointPolicy.validated("https://agent.example.com").scheme)
        for (url in listOf("http://8.8.8.8", "http://agent.example.com", "ftp://10.0.0.1",
            "https://user:pass@example.com", "http://192.168.1.37@evil.example")) {
            assertThrows(IllegalArgumentException::class.java) { EndpointPolicy.validated(url) }
        }
        assertFalse(EndpointPolicy.webLink("intent://settings"))
        assertFalse(EndpointPolicy.webLink("file:///data/private"))
        assertTrue(EndpointPolicy.webLink("https://example.com/movie?q=1"))
    }

    @Test fun pendingWorkIsCoalescedAndBoundedWhenServerIsSlow() {
        LatestWorkQueue(2).use { queue ->
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val done = CountDownLatch(2)
            val results = java.util.Collections.synchronizedList(ArrayList<Int>())
            queue.submit("blocked") { entered.countDown(); release.await(2, TimeUnit.SECONDS) }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            queue.submit("same") { results.add(1) }
            queue.submit("same") { results.add(2) }
            queue.submit("evicted") { results.add(3) }
            queue.submit("last") { results.add(4); done.countDown() }
            queue.submit("same") { results.add(5); done.countDown() }
            release.countDown()
            assertTrue(done.await(2, TimeUnit.SECONDS))
            assertEquals(listOf(4, 5), results)
        }
    }
}
