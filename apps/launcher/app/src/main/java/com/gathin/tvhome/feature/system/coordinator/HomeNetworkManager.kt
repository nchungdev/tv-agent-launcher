package com.gathin.tvhome.feature.system.coordinator
import com.gathin.tvhome.feature.system.data.*
import com.gathin.tvhome.feature.system.widget.*

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import com.gathin.tvhome.R


data class HomeNetworkState(
    val online: Boolean = false,
    val isEthernet: Boolean = false,
    val isWifi: Boolean = false,
    val isVpn: Boolean = false,
    val isValidated: Boolean = false,
    val iconRes: Int = R.drawable.ic_wifi_off,
    val rawText: String = "",
    val flame: Int = 0,
    val isSteady: Boolean = false,
    val isWarning: Boolean = false
)

class HomeNetworkManager(
    private val context: Context,
    private val onNetworkChanged: () -> Unit
) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val defaultCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { onNetworkChanged() }
        override fun onLost(network: Network) { onNetworkChanged() }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            onNetworkChanged()
        }
    }

    private val vpnCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { onNetworkChanged() }
        override fun onLost(network: Network) { onNetworkChanged() }
    }

    fun start() {
        try {
            cm?.registerDefaultNetworkCallback(defaultCallback)
            val vpnRequest = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
            cm?.registerNetworkCallback(vpnRequest, vpnCallback)
        } catch (_: Exception) {}
    }

    fun stop() {
        try {
            cm?.unregisterNetworkCallback(defaultCallback)
            cm?.unregisterNetworkCallback(vpnCallback)
        } catch (_: Exception) {}
    }

    fun queryState(): HomeNetworkState {
        var text = context.getString(R.string.wifi)
        var vpn = false
        var online = false
        var icon = R.drawable.ic_wifi_off
        var warn = false
        var validated = false
        var flame = 0
        var steady = false
        var wifi = false
        var ethernet = false

        try {
            for (net in cm?.allNetworks ?: emptyArray()) {
                val caps = cm?.getNetworkCapabilities(net) ?: continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    vpn = true
                    continue
                }
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
                online = true
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                    validated = true
                }
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) ethernet = true
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifi = true
            }

            if (!online) {
                text = context.getString(R.string.offline)
                vpn = false
                warn = true
            } else if (ethernet) {
                text = context.getString(R.string.ethernet)
                icon = R.drawable.ic_ethernet
                flame = 4
                steady = true
            } else if (wifi) {
                val wm = context.applicationContext
                    .getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val info = wm?.connectionInfo
                val ssid = info?.ssid
                flame = if (info != null) {
                    maxOf(1, minOf(4, WifiManager.calculateSignalLevel(info.rssi, 5)))
                } else 3
                if (ssid != null) {
                    val clean = ssid.replace("\"", "").trim()
                    if (clean.isNotEmpty() && !clean.contains("unknown") && clean != "0x") {
                        text = clean
                    }
                }
                val rssi = info?.rssi ?: -55
                val level = when {
                    rssi >= -60 -> 3
                    rssi >= -70 -> 2
                    else -> 1
                }
                icon = when (level) {
                    3 -> R.drawable.ic_wifi
                    2 -> R.drawable.ic_wifi_2
                    else -> R.drawable.ic_wifi_1
                }
                val mhz = info?.frequency ?: 0
                val band = when {
                    mhz <= 0 -> ""
                    mhz < 3000 -> "2.4 GHz"
                    mhz < 5925 -> "5 GHz"
                    else -> "6 GHz"
                }
                if (level == 1) {
                    warn = true
                    val weakText = context.getString(R.string.signal_weak)
                    text = context.getString(R.string.wifi_label_detail, text, weakText)
                } else if (band.isNotEmpty()) {
                    text = context.getString(R.string.wifi_label_detail, text, band)
                }
            }
            if (online && !validated) {
                warn = true
                val noNetText = context.getString(R.string.wifi_no_internet)
                text = context.getString(R.string.wifi_label_detail, text, noNetText)
            }
        } catch (_: Exception) {}

        return HomeNetworkState(
            online = online,
            isEthernet = ethernet,
            isWifi = wifi,
            isVpn = vpn,
            isValidated = validated,
            iconRes = icon,
            rawText = text,
            flame = flame,
            isSteady = steady,
            isWarning = warn
        )
    }
}
