package com.gathin.tvhome.feature.agent.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper


class NsdDiscoveryHelper(
    context: Context,
    private val serviceType: String,
    private val onFound: (String) -> Unit,
    private val onTimeout: () -> Unit
) {
    private val manager = context.getSystemService(NsdManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var listener: NsdManager.DiscoveryListener? = null
    private var active = false

    private val timeoutTask = Runnable {
        if (active) {
            stop()
            onTimeout()
        }
    }

    fun start(timeoutMs: Long = 8_000L) {
        stop()
        active = true
        main.postDelayed(timeoutTask, timeoutMs)

        val discListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onStartDiscoveryFailed(type: String, code: Int) = Unit
            override fun onStopDiscoveryFailed(type: String, code: Int) = Unit

            override fun onServiceFound(info: NsdServiceInfo) {
                manager.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(service: NsdServiceInfo, code: Int) = Unit
                    override fun onServiceResolved(service: NsdServiceInfo) {
                        val host = service.host?.hostAddress ?: return
                        val formatted = if (host.contains(':')) "[$host]" else host
                        val url = "http://$formatted:${service.port}"
                        main.post {
                            if (active) {
                                main.removeCallbacks(timeoutTask)
                                onFound(url)
                            }
                        }
                    }
                })
            }
        }
        listener = discListener
        runCatching {
            manager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discListener)
        }
    }

    fun stop() {
        active = false
        main.removeCallbacks(timeoutTask)
        listener?.let { runCatching { manager.stopServiceDiscovery(it) } }
        listener = null
    }
}
