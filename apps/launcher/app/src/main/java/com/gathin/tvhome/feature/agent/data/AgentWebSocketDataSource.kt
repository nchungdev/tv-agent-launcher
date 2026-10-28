package com.gathin.tvhome.feature.agent.data

import android.content.Context
import android.util.Base64
import com.gathin.tvhome.feature.agent.data.AgentClient
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.SecureRandom


class AgentWebSocketDataSource(private val context: Context) {
    private val appContext = context.applicationContext
    private val scratchBuffer = ByteArray(MAX_PAYLOAD_BYTES)
    private val pongFrame = byteArrayOf(
        0x8A.toByte(), 0x80.toByte(), 0x12, 0x34, 0x56, 0x78
    )

    fun observeEvents(): Flow<String> = flow {
        var activeSocket: Socket? = null
        try {
            var backoffMs = RECONNECT_DELAY_MS
            while (currentCoroutineContext().isActive) {
                var connected = false
                try {
                    val base = AgentClient.baseUrl(appContext)
                    if (base.isBlank()) {
                        delay(backoffMs)
                        continue
                    }
                    val parsed = URI.create(base)
                    val host = parsed.host ?: run {
                        delay(backoffMs)
                        return@flow
                    }
                    val port = if (parsed.port > 0) parsed.port else DEFAULT_PORT

                    val socket = Socket()
                    activeSocket = socket
                    socket.receiveBufferSize = SOCKET_RX_BYTES
                    socket.sendBufferSize = SOCKET_TX_BYTES
                    socket.tcpNoDelay = true
                    socket.soTimeout = READ_TIMEOUT_MS
                    socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)

                    val input = BufferedInputStream(socket.getInputStream(), SOCKET_RX_BYTES)
                    val output = socket.getOutputStream()

                    sendHandshake(output, host, port, AgentClient.token(appContext))
                    if (readHandshakeResponse(input)) {
                        connected = true
                        backoffMs = RECONNECT_DELAY_MS
                        while (currentCoroutineContext().isActive && !socket.isClosed) {
                            var framePayload: String? = null
                            val readOk = readNextFrame(input, output) { payload ->
                                framePayload = payload
                            }
                            if (!readOk) break
                            framePayload?.let { emit(it) }
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    try { activeSocket?.close() } catch (_: Exception) {}
                    activeSocket = null
                }

                if (!currentCoroutineContext().isActive) break
                delay(backoffMs)
                if (!connected) {
                    backoffMs = minOf(backoffMs * 2, MAX_BACKOFF_MS)
                }
            }
        } finally {
            try { activeSocket?.close() } catch (_: Exception) {}
        }
    }

    private fun sendHandshake(output: OutputStream, host: String, port: Int, token: String?) {
        val nonce = ByteArray(16)
        SecureRandom().nextBytes(nonce)
        val secKey = Base64.encodeToString(nonce, Base64.NO_WRAP)
        val request = "GET /ws/duinch HTTP/1.1\r\n" +
            "Host: $host:$port\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Key: $secKey\r\n" +
            "Sec-WebSocket-Version: 13\r\n" +
            authorizationHeader(token) + "\r\n"
        output.write(request.toByteArray(StandardCharsets.US_ASCII))
        output.flush()
    }

    private fun authorizationHeader(token: String?): String {
        return if (token.isNullOrEmpty()) "" else "Authorization: Bearer $token\r\n"
    }

    private fun readHandshakeResponse(input: InputStream): Boolean {
        var length = 0
        var matched = 0
        while (length < MAX_PAYLOAD_BYTES) {
            val byteRead = input.read()
            if (byteRead < 0) return false
            scratchBuffer[length++] = byteRead.toByte()
            val ch = byteRead.toChar()
            matched = when {
                matched == 0 && ch == '\r' -> 1
                matched == 1 && ch == '\n' -> 2
                matched == 2 && ch == '\r' -> 3
                matched == 3 && ch == '\n' -> 4
                ch == '\r' -> 1
                else -> 0
            }
            if (matched == 4) {
                val header = String(scratchBuffer, 0, length, StandardCharsets.US_ASCII)
                return header.startsWith("HTTP/1.1 101")
            }
        }
        return false
    }

    private fun readNextFrame(
        input: InputStream,
        output: OutputStream,
        onText: (String) -> Unit
    ): Boolean {
        val firstByte = input.read()
        val secondByte = input.read()
        if (firstByte < 0 || secondByte < 0) return false

        val opcode = firstByte and 0x0F
        val masked = (secondByte and 0x80) != 0
        var payloadLength = secondByte and 0x7F

        if (payloadLength == 126) {
            val high = input.read()
            val low = input.read()
            if (high < 0 || low < 0) return false
            payloadLength = (high shl 8) or low
        } else if (payloadLength == 127) {
            return false
        }

        val maskKey = ByteArray(4)
        if (masked) readFully(input, maskKey, 4)

        if (payloadLength > MAX_PAYLOAD_BYTES) {
            skipFully(input, payloadLength)
            return true
        }

        readFully(input, scratchBuffer, payloadLength)
        if (masked) {
            for (index in 0 until payloadLength) {
                scratchBuffer[index] =
                    (scratchBuffer[index].toInt() xor maskKey[index and 3].toInt()).toByte()
            }
        }

        when (opcode) {
            OPCODE_TEXT -> {
                if (payloadLength > 0) {
                    val text = String(scratchBuffer, 0, payloadLength, StandardCharsets.UTF_8)
                    onText(text)
                }
            }
            OPCODE_PING -> {
                output.write(pongFrame)
                output.flush()
            }
            OPCODE_CLOSE -> return false
        }
        return true
    }

    private fun readFully(input: InputStream, target: ByteArray, count: Int) {
        var offset = 0
        while (offset < count) {
            val read = input.read(target, offset, count - offset)
            if (read < 0) throw EOFException()
            offset += read
        }
    }

    private fun skipFully(input: InputStream, count: Int) {
        var remaining = count
        while (remaining > 0) {
            val chunk = minOf(remaining, scratchBuffer.size)
            val read = input.read(scratchBuffer, 0, chunk)
            if (read < 0) throw EOFException()
            remaining -= read
        }
    }

    companion object {
        private const val DEFAULT_PORT = 8788
        private const val MAX_PAYLOAD_BYTES = 32 * 1024
        private const val SOCKET_RX_BYTES = 8192
        private const val SOCKET_TX_BYTES = 1024
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val READ_TIMEOUT_MS = 75_000
        private const val RECONNECT_DELAY_MS = 4000L
        private const val MAX_BACKOFF_MS = 60_000L
        private const val OPCODE_TEXT = 1
        private const val OPCODE_CLOSE = 8
        private const val OPCODE_PING = 9
    }
}
