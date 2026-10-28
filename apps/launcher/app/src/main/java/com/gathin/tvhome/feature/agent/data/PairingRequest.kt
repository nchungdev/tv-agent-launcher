package com.gathin.tvhome.feature.agent.data

import java.io.InputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

internal data class PairingRequest(
    val method: String, val path: String, val headers: Map<String, String>, val body: String
) {
    companion object {
        const val MAX_BODY = 16 * 1024
        fun read(input: InputStream): PairingRequest {
            var headerBytes = 0
            fun line(): String {
                val result = StringBuilder()
                while (true) {
                    val value = input.read()
                    if (value < 0) throw IOException("Incomplete request")
                    if (++headerBytes > 8192) throw IOException("Headers too large")
                    if (value == 10) return result.toString().removeSuffix("\r")
                    result.append(value.toChar())
                }
            }
            val first = line().split(' ')
            require(first.size == 3) { "Invalid request" }
            val headers = LinkedHashMap<String, String>()
            while (true) {
                val header = line()
                if (header.isEmpty()) break
                val pair = header.split(':', limit = 2)
                require(pair.size == 2) { "Invalid header" }
                val name = pair[0].lowercase()
                require(!headers.containsKey(name)) { "Duplicate header" }
                headers[name] = pair[1].trim()
            }
            require(!headers.containsKey("transfer-encoding")) { "Unsupported encoding" }
            val declared = headers["content-length"]
            val size = if (declared == null) 0 else
                declared.toIntOrNull() ?: throw IOException("Invalid body length")
            require(size in 0..MAX_BODY) { "Invalid body length" }
            val bytes = ByteArray(size)
            var offset = 0
            while (offset < size) {
                val count = input.read(bytes, offset, size - offset)
                if (count < 0) throw IOException("Incomplete body")
                offset += count
            }
            return PairingRequest(first[0], first[1], headers,
                String(bytes, StandardCharsets.UTF_8))
        }
    }
}
