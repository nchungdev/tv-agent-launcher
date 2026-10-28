package com.gathin.tvhome.feature.agent.data

import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.charset.StandardCharsets

internal object AgentDiscovery {
    private const val DISCOVERY_PROBE = "TV_AGENT_DISCOVER"
    private const val DISCOVERY_PORT = 8789
    private const val DISCOVERY_TIMEOUT_MS = 1200
    @JvmStatic
    fun discover(): String? {
        return try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = DISCOVERY_TIMEOUT_MS
                val probe = DISCOVERY_PROBE.toByteArray(StandardCharsets.UTF_8)
                val broadcast = InetAddress.getByName("255.255.255.255")
                val packet = DatagramPacket(probe, probe.size, broadcast, DISCOVERY_PORT)
                socket.send(packet)

                val buffer = ByteArray(256)
                val reply = DatagramPacket(buffer, buffer.size)
                socket.receive(reply)

                val text = String(reply.data, 0, reply.length, StandardCharsets.UTF_8)
                val json = JSONObject(text)
                val port = json.optInt("port", 8788)
                val host = reply.address.hostAddress
                val url = "http://$host:$port"
                url
            }
        } catch (_: Exception) {
            null
        }
    }

}
