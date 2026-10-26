package com.gathin.tvhome.feature.smarthome.data
import com.gathin.tvhome.feature.smarthome.data.HassClient

import com.gathin.tvhome.feature.smarthome.data.SmartHomeActions
import java.net.ServerSocket
import java.net.Socket
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HassTransportTest {
    @Test fun authenticatesAndCallsCorrectServiceThenReadsRealStates() {
        val server = TestServer()
        val calls = mutableListOf<String>()
        var authorization = ""
        var payload = ""
        server.createContext("/api/") { exchange ->
            calls.add(exchange.path)
            authorization = exchange.headers["authorization"].orEmpty()
            val response = when (exchange.path) {
                "/api/" -> """{"message":"API running."}"""
                "/api/states" -> """[{"entity_id":"fan.living","state":"on"}]"""
                "/api/services/fan/toggle" -> {
                    payload = exchange.body
                    "[]"
                }
                else -> "[]"
            }.toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.output.write(response)
            exchange.close()
        }
        server.start()
        try {
            HassClient("http://127.0.0.1:${server.port}", "test-only-token").use { client ->
                client.verify()
                val entity = client.states().single()
                client.call(entity, SmartHomeActions.toggle(entity)!!)
            }
            assertEquals("Bearer test-only-token", authorization)
            assertEquals("fan.living", JSONObject(payload).getString("entity_id"))
            assertEquals(listOf("/api/", "/api/states", "/api/services/fan/toggle"), calls)
        } finally {
            server.stop()
        }
    }

    @Test fun doesNotForwardTokenToRedirectedLogin() {
        val server = TestServer()
        var redirected = false
        server.createContext("/api/") { exchange ->
            exchange.location = "/login"
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/login") { exchange ->
            redirected = true
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()
        try {
            HassClient("http://127.0.0.1:${server.port}", "test-only-token").use { client ->
                try {
                    client.verify()
                    fail("Expected redirect rejection")
                } catch (error: java.io.IOException) {
                    assertTrue(error.message!!.contains("chuyển hướng"))
                }
            }
            assertFalse(redirected)
        } finally {
            server.stop()
        }
    }

    @Test fun invalidTokenIsAnErrorInsteadOfEmptyDevices() {
        val server = TestServer()
        server.createContext("/api/") { exchange ->
            exchange.sendResponseHeaders(401, -1)
            exchange.close()
        }
        server.start()
        try {
            HassClient("http://127.0.0.1:${server.port}", "invalid-token").use { client ->
                try {
                    client.states()
                    fail("Expected authentication error")
                } catch (error: java.io.IOException) {
                    assertTrue(error.message!!.contains("Token"))
                }
            }
        } finally {
            server.stop()
        }
    }
}

private class TestServer {
    private val socket = ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1"))
    private val handlers = linkedMapOf<String, (Exchange) -> Unit>()
    val port: Int get() = socket.localPort
    private var thread: Thread? = null

    fun createContext(path: String, handler: (Exchange) -> Unit) {
        handlers[path] = handler
    }

    fun start() {
        thread = Thread {
            while (!socket.isClosed) {
                try {
                    val exchange = Exchange(socket.accept())
                    val handler = handlers.entries.filter { exchange.path.startsWith(it.key) }
                        .maxByOrNull { it.key.length }?.value
                    handler?.invoke(exchange)
                } catch (error: java.io.IOException) {
                    if (!socket.isClosed) throw error
                }
            }
        }.apply { isDaemon = true; start() }
    }

    fun stop() {
        socket.close()
        thread?.join(2000)
    }
}

private class Exchange(private val socket: Socket) {
    val headers = linkedMapOf<String, String>()
    var location = ""
    val path: String
    val body: String
    val output = socket.getOutputStream()

    init {
        socket.soTimeout = 3000
        val reader = socket.getInputStream().bufferedReader()
        path = reader.readLine().split(' ')[1]
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            headers[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim()
        }
        val chars = CharArray(headers["content-length"]?.toIntOrNull() ?: 0)
        var count = 0
        while (count < chars.size) {
            val read = reader.read(chars, count, chars.size - count)
            if (read < 0) break
            count += read
        }
        body = String(chars)
    }

    fun sendResponseHeaders(status: Int, length: Long) {
        val header = "HTTP/1.1 $status Response\r\nContent-Length: ${length.coerceAtLeast(0)}\r\n" +
            (if (location.isNotEmpty()) "Location: $location\r\n" else "") +
            "Connection: close\r\n\r\n"
        output.write(header.toByteArray())
    }

    fun close() = socket.close()
}
