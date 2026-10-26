package com.gathin.tvhome.feature.system.coordinator
import com.gathin.tvhome.feature.system.data.*
import com.gathin.tvhome.feature.system.widget.*

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper


class HomeNetworkObserver(
    context: Context,
    private val onChange: () -> Unit
) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            main.post { onChange() }
        }

        override fun onLost(network: Network) {
            main.post { onChange() }
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            main.post { onChange() }
        }
    }

    fun start() {
        try {
            val req = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm?.registerNetworkCallback(req, callback)
        } catch (_: Exception) {}
    }

    fun stop() {
        try {
            cm?.unregisterNetworkCallback(callback)
        } catch (_: Exception) {}
    }
}
