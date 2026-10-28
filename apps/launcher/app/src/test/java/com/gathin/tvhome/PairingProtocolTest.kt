package com.gathin.tvhome

import com.gathin.tvhome.feature.agent.data.PairingRequest
import com.gathin.tvhome.feature.agent.data.AgentSocketTransport
import java.io.ByteArrayInputStream
import java.net.URI
import org.junit.Assert.*
import org.junit.Test

class PairingProtocolTest {
    private fun request(headers: String, body: String = "") =
        ByteArrayInputStream(("POST /save HTTP/1.1\r\nHost: 127.0.0.1:8791\r\n" +
            headers + "\r\n" + body).toByteArray(Charsets.UTF_8))

    @Test fun invalidLengthsAndDuplicateHeadersAreRejectedBeforeAllocation() {
        for (length in listOf("-1", "999999999", "bad")) {
            assertThrows(Exception::class.java) {
                PairingRequest.read(request("Content-Length: $length\r\n"))
            }
        }
        assertThrows(Exception::class.java) {
            PairingRequest.read(request("Content-Length: 2\r\nContent-Length: 2\r\n", "{}"))
        }
        assertThrows(Exception::class.java) {
            PairingRequest.read(request("Transfer-Encoding: chunked\r\n"))
        }
    }

    @Test fun utf8BodyUsesByteLengthAndTruncationFails() {
        val body = "{\"text\":\"Xin chào\"}"
        val size = body.toByteArray(Charsets.UTF_8).size
        assertEquals(body, PairingRequest.read(request("Content-Length: $size\r\n", body)).body)
        assertThrows(Exception::class.java) {
            PairingRequest.read(request("Content-Length: 20\r\n", "{}"))
        }
    }

    @Test fun oversizedHeadersCannotAllocateAnUnboundedLine() {
        assertThrows(Exception::class.java) {
            PairingRequest.read(request("X-Padding: " + "x".repeat(9000) + "\r\n"))
        }
    }

    @Test fun websocketRequiresValidAcceptAndProtocolHeaders() {
        val key = "dGhlIHNhbXBsZSBub25jZQ=="
        val header = "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\n" +
            "Connection: Upgrade\r\nSec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=\r\n\r\n"
        assertTrue(AgentSocketTransport.accepts(header, key))
        assertFalse(AgentSocketTransport.accepts(header, "wrong"))
        assertFalse(AgentSocketTransport.accepts("HTTP/1.1 101 OK\r\n\r\n", key))
        assertEquals(443, AgentSocketTransport.port(URI("https://example.com")))
        assertEquals(80, AgentSocketTransport.port(URI("http://192.168.1.37")))
        assertEquals(8788, AgentSocketTransport.port(URI("https://example.com:8788")))
    }
}
