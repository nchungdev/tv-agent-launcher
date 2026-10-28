package com.gathin.tvhome

import android.app.Activity
import android.app.Dialog
import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import com.gathin.tvhome.feature.agent.data.LocalPairingServer
import com.gathin.tvhome.feature.notification.data.NotificationRepositoryImpl
import com.gathin.tvhome.feature.notification.ui.NotificationSheet
import com.gathin.tvhome.feature.settings.data.Config
import java.net.HttpURLConnection
import java.net.URL
import java.net.Socket
import java.net.InetSocketAddress

class RiskRegressionChecks(private val runner: Instrumentation) {
    fun run(): String {
        val host = runner as AgentRenderInstrumentation
        runner.targetContext.startActivity(Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setClassName(runner.targetContext, "com.gathin.tvhome.feature.main.ui.MainActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (host.activeHome == null && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(20)
        }
        val activity = host.activeHome ?: error("Home not ready")
        runner.waitForIdleSync()
        val repository = NotificationRepositoryImpl.getInstance()
        val probe: (Int) -> Unit = {}
        var listeners: Collection<*>? = null
        runner.runOnMainSync {
            repository.addListener(probe)
            listeners = repository.javaClass.declaredFields.mapNotNull {
                it.isAccessible = true
                it.get(repository) as? Collection<*>
            }.first { it.contains(probe) }
        }
        val before = listeners!!.size
        try {
            repeat(8) {
                var dialog: Dialog? = null
                runner.runOnMainSync {
                    val sheet = NotificationSheet(activity, Config.load(activity))
                    sheet.show()
                    dialog = sheet.javaClass.declaredFields.first {
                        Dialog::class.java.isAssignableFrom(it.type)
                    }.apply { isAccessible = true }.get(sheet) as Dialog
                    check(listeners!!.size == before + 1) { "Sheet did not register listener" }
                }
                runner.waitForIdleSync()
                runner.runOnMainSync { dialog!!.dismiss() }
                runner.waitForIdleSync()
                check(listeners!!.size == before) { "Dismissed sheet retained listener" }
            }
        } finally {
            runner.runOnMainSync { repository.removeListener(probe) }
        }
        pairingGuards()
        return "8 notification dismiss cycles: listener count stable; pairing rejects missing nonce; " +
            "pairing stop closes listening socket"
    }

    private fun pairingGuards() {
        var server: LocalPairingServer? = null
        runner.runOnMainSync {
            server = LocalPairingServer(LocalPairingServer.ServiceType.AGENT,
                "http://192.168.1.37:8788") { _, _, _ -> error("Unexpected pairing") }
            server!!.start()
        }
        val port = server!!.port
        try {
            val connection = URL("http://127.0.0.1:$port/save")
                .openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 2000
                connection.readTimeout = 2000
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(2)
                connection.outputStream.use { it.write("{}".toByteArray()) }
                check(connection.responseCode == 403) { "Pairing accepted missing nonce" }
            } finally { connection.disconnect() }
        } finally {
            runner.runOnMainSync { server!!.stop() }
        }
        val connected = runCatching {
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 1000) }
        }.isSuccess
        check(!connected) { "Pairing socket remained open after stop" }
    }
}
