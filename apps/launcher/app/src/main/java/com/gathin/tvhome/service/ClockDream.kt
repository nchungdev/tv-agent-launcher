package com.gathin.tvhome.service

import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.agent.widget.DuinchView
import com.gathin.tvhome.feature.system.widget.SleepingRobotScreensaverView
import com.gathin.tvhome.feature.agent.ui.AgentVoiceReply
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.nio.charset.StandardCharsets
import java.util.Locale

class ClockDream : DreamService() {

    private val handler = Handler(Looper.getMainLooper())
    private var sleepingSaver: SleepingRobotScreensaverView? = null
    private var dreamBot: DuinchView? = null
    private var noticeCard: View? = null
    private var noticeBadge: TextView? = null
    private var noticeTitle: TextView? = null
    private var noticeMessage: TextView? = null

    private var voiceReply: AgentVoiceReply? = null
    private var udpSocket: DatagramSocket? = null
    private var udpThread: Thread? = null

    private val hideNoticeTask = Runnable { hideNotice() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isDreaming = true
        isInteractive = true
        isFullscreen = true
        isScreenBright = false
        setBrightness(DEEP_AMBIENT_BRIGHTNESS)
        setContentView(R.layout.dream_clock)

        sleepingSaver = findViewById(R.id.sleepingSaver)
        dreamBot = findViewById(R.id.dreamBot)
        dreamBot?.setMood(DuinchView.SLEEP)
        noticeCard = findViewById(R.id.dreamNoticeCard)
        noticeBadge = findViewById(R.id.dreamNoticeBadge)
        noticeTitle = findViewById(R.id.dreamNoticeTitle)
        noticeMessage = findViewById(R.id.dreamNoticeMessage)
    }

    private fun setBrightness(brightness: Float) {
        val w = window ?: return
        val lp = w.attributes
        lp.screenBrightness = brightness
        w.attributes = lp
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        isDreaming = true
        sleepingSaver?.let {
            it.alpha = 0f
            it.animate().alpha(1f).setDuration(900L).start()
        }
        startUdpListener()
    }

    override fun onDreamingStopped() {
        isDreaming = false
        stopUdpListener()
        stopVoice()
        handler.removeCallbacks(hideNoticeTask)
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        isDreaming = false
        stopUdpListener()
        stopVoice()
        handler.removeCallbacks(hideNoticeTask)
        super.onDetachedFromWindow()
    }

    override fun onDestroy() {
        isDreaming = false
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event)
        }
        val saver = sleepingSaver
        if (saver != null) {
            saver.triggerWakeUp(Runnable { finish() })
        } else {
            finish()
        }
        return true
    }

    private fun startUdpListener() {
        stopUdpListener()
        val thread = Thread({ listenUdp() }, "ClockDream-UDP")
        udpThread = thread
        thread.start()
    }

    private fun listenUdp() {
        try {
            val socket = DatagramSocket(NOTIFY_PORT)
            udpSocket = socket
            val buffer = ByteArray(BUFFER_SIZE)
            while (!Thread.currentThread().isInterrupted && !socket.isClosed) {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                val raw = String(packet.data, 0, packet.length, StandardCharsets.UTF_8)
                val json = JSONObject(raw)
                handler.post { onNotificationReceived(json) }
            }
        } catch (_: Exception) {}
    }

    private fun stopUdpListener() {
        try {
            udpSocket?.close()
        } catch (_: Exception) {}
        udpSocket = null
        udpThread?.interrupt()
        udpThread = null
    }

    private fun onNotificationReceived(json: JSONObject) {
        val type = json.optString("type", "glance")
        val title = json.optString("title", "")
        val message = json.optString("message", "")
        val tts = json.optBoolean("tts", false)
        val timeoutSeconds = json.optInt("timeout_seconds", 15)

        showNotice(type, title, message)
        if (tts && message.isNotEmpty()) speakNotice(message)

        handler.removeCallbacks(hideNoticeTask)
        handler.postDelayed(hideNoticeTask, maxOf(3, timeoutSeconds) * 1000L)
    }

    private fun showNotice(type: String, title: String, message: String) {
        val card = noticeCard ?: return
        setBrightness(NORMAL_BRIGHTNESS)

        noticeBadge?.text = type.uppercase(Locale.ROOT)
        noticeTitle?.text = title
        noticeTitle?.visibility = if (title.isEmpty()) View.GONE else View.VISIBLE
        noticeMessage?.text = message
        noticeMessage?.visibility = if (message.isEmpty()) View.GONE else View.VISIBLE

        card.visibility = View.VISIBLE
        card.animate().alpha(1f).setDuration(500).start()
        dreamBot?.setMood(DuinchView.EXCITED)
    }

    private fun hideNotice() {
        val card = noticeCard
        if (card == null || card.visibility != View.VISIBLE) return
        card.animate().alpha(0f).setDuration(600).withEndAction {
            card.visibility = View.GONE
            setBrightness(DEEP_AMBIENT_BRIGHTNESS)
            dreamBot?.setMood(DuinchView.SLEEP)
        }.start()
    }

    private fun speakNotice(text: String) {
        var vr = voiceReply
        if (vr == null) {
            vr = AgentVoiceReply(this) {}
            vr.setBot(dreamBot)
            voiceReply = vr
        }
        vr.startAnswer()
        vr.feed(text, true)
    }

    private fun stopVoice() {
        voiceReply?.stop()
        voiceReply?.shutdown()
        voiceReply = null
    }

    companion object {
        @Volatile @JvmField
        var isDreaming = false

        private const val DEEP_AMBIENT_BRIGHTNESS = 0.01f
        private const val NORMAL_BRIGHTNESS = -1.0f
        private const val NOTIFY_PORT = 8790
        private const val BUFFER_SIZE = 4096
    }
}
