package com.gathin.tvhome.feature.agent.data

import com.gathin.tvhome.core.util.EndpointPolicy
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.security.MessageDigest
import java.util.Base64
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

internal object AgentSocketTransport {
    fun port(uri: URI): Int = if (uri.port > 0) uri.port else
        if (uri.scheme == "https") 443 else 80

    fun connect(address: String, onSocket: (Socket) -> Unit): Socket {
        val uri = EndpointPolicy.validated(address)
        require(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") {
            "Agent WebSocket requires a server root URL"
        }
        val raw = Socket()
        onSocket(raw)
        raw.soTimeout = 75_000
        raw.tcpNoDelay = true
        raw.connect(InetSocketAddress(uri.host, port(uri)), 5000)
        if (uri.scheme != "https") return raw
        val secure = (SSLSocketFactory.getDefault() as SSLSocketFactory)
            .createSocket(raw, uri.host, port(uri), true) as SSLSocket
        onSocket(secure)
        secure.sslParameters = secure.sslParameters.apply {
            endpointIdentificationAlgorithm = "HTTPS"
        }
        secure.startHandshake()
        return secure
    }

    fun accepts(header: String, key: String): Boolean {
        val lines = header.split("\r\n")
        if (lines.firstOrNull()?.split(' ')?.getOrNull(1) != "101") return false
        val fields = lines.drop(1).filter { it.contains(':') }.associate {
            val pair = it.split(':', limit = 2)
            pair[0].trim().lowercase() to pair[1].trim()
        }
        val digest = MessageDigest.getInstance("SHA-1")
            .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(Charsets.US_ASCII))
        val expected = Base64.getEncoder().encodeToString(digest)
        return fields["sec-websocket-accept"] == expected &&
            fields["upgrade"].equals("websocket", true) &&
            fields["connection"].orEmpty().split(',').any { it.trim().equals("upgrade", true) }
    }
}
