package com.gathin.tvhome.core.util

import java.net.URI

object EndpointPolicy {
    fun validated(raw: String): URI {
        val uri = URI(raw)
        require(uri.scheme == "https" || uri.scheme == "http") { "Unsupported protocol" }
        require(!uri.host.isNullOrBlank() && uri.rawUserInfo == null) { "Invalid server URL" }
        require(uri.rawFragment == null && uri.rawQuery == null) { "Invalid server URL" }
        require(uri.scheme == "https" || isLocal(uri.host)) { "Remote servers require HTTPS" }
        return uri
    }

    fun isLocal(host: String): Boolean {
        val clean = host.removePrefix("[").removeSuffix("]").lowercase()
        if (clean == "localhost" || clean == "::1") return true
        if (clean.contains(':')) return clean.startsWith("fd") || clean.startsWith("fc") ||
            clean.startsWith("fe80:")
        val parts = clean.split('.').map { it.toIntOrNull() ?: return false }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        return parts[0] == 10 || parts[0] == 127 ||
            (parts[0] == 192 && parts[1] == 168) ||
            (parts[0] == 172 && parts[1] in 16..31) ||
            (parts[0] == 169 && parts[1] == 254)
    }

    fun webLink(raw: String): Boolean = runCatching {
        val uri = URI(raw)
        (uri.scheme == "https" || uri.scheme == "http") &&
            !uri.host.isNullOrBlank() && uri.rawUserInfo == null
    }.getOrDefault(false)
}
