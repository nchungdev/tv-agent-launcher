package com.gathin.tvhome.feature.notification.widget
import com.gathin.tvhome.feature.notification.data.*

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.notification.data.TvNotification
import com.gathin.tvhome.service.InputKeyService
import com.gathin.tvhome.feature.agent.widget.DuinchView
import java.util.Locale


object NotificationHudOverlay {

    private const val CHAR_DELAY_MS = 18L
    private const val BUBBLE_DELAY_MS = 320L
    private const val DISPLAY_DURATION_MS = 5500L
    private const val EXIT_DURATION_MS = 320L

    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private var activeView: View? = null
    private var activeWindowManager: WindowManager? = null
    private var typingRunnable: Runnable? = null
    private var dismissRunnable: Runnable? = null

    fun show(context: Context, notification: TvNotification) {
        handler.post { showOnMainThread(context, notification) }
    }

    fun dismiss() {
        handler.post { removeActiveImmediate() }
    }

    fun resolveMood(severity: String, source: String): Int {
        val normSeverity = severity.lowercase(Locale.ROOT)
        if (normSeverity == "warning" || normSeverity == "error" || normSeverity == "alert") {
            return DuinchView.SURPRISED
        }
        val normSource = source.lowercase(Locale.ROOT)
        if (normSource == "suggestion" || normSeverity == "suggestion") {
            return DuinchView.WINK
        }
        return DuinchView.EXCITED
    }

    fun formatTag(notification: TvNotification): String {
        val src = notification.source.trim().uppercase(Locale.ROOT)
        val title = notification.title.trim()
        if (src.isEmpty()) return title.uppercase(Locale.ROOT)
        if (title.isEmpty() || title.equals("Thông báo", ignoreCase = true)) return src
        return "$src • ${title.uppercase(Locale.ROOT)}"
    }

    private fun showOnMainThread(context: Context, notification: TvNotification) {
        removeActiveImmediate()
        val target = resolveWindowTarget(context) ?: return
        val windowManager = target.first
        val windowType = target.second
        val hostContext = target.third

        val root = LayoutInflater.from(hostContext).inflate(R.layout.layout_hud_banner, null)
        val bubble = root.findViewById<View>(R.id.hudBubble)
        val tagView = root.findViewById<TextView>(R.id.hudTag)
        val messageView = root.findViewById<TextView>(R.id.hudMessage)
        val botView = root.findViewById<DuinchView>(R.id.hudDuinchBot)

        val density = hostContext.resources.displayMetrics.density
        val fullMessage = notification.message.ifEmpty { notification.title }.trim()
        if (fullMessage.isEmpty()) return

        tagView.text = formatTag(notification)
        messageView.text = ""
        bubble.alpha = 0f
        bubble.scaleX = 0.9f
        bubble.scaleY = 0.9f

        botView.allowUnfocusedAnimation = true
        botView.wake()
        botView.setMood(resolveMood(notification.severity, notification.source))
        botView.lookAt(-0.35f, 0.1f)
        botView.translationX = 96f * density
        botView.alpha = 0f

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = Math.round(36f * density)
            y = Math.round(28f * density)
        }

        try {
            windowManager.addView(root, params)
            activeView = root
            activeWindowManager = windowManager
        } catch (_: Exception) {
            return
        }

        botView.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(320L)
            .setInterpolator(OvershootInterpolator(1.1f))
            .start()

        handler.postDelayed({
            if (activeView !== root) return@postDelayed
            bubble.pivotX = bubble.width.toFloat()
            bubble.pivotY = bubble.height / 2f
            bubble.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220L)
                .start()
            startTypewriter(root, botView, messageView, fullMessage)
        }, BUBBLE_DELAY_MS)

        val exitTask = Runnable { animateExit(root, bubble, botView, density) }
        dismissRunnable = exitTask
        handler.postDelayed(exitTask, DISPLAY_DURATION_MS)
    }

    private fun startTypewriter(
        root: View,
        botView: DuinchView,
        messageView: TextView,
        fullText: String
    ) {
        botView.setTalking(true)
        var charIndex = 0
        val step = object : Runnable {
            override fun run() {
                if (activeView !== root) return
                charIndex = minOf(fullText.length, charIndex + 1)
                messageView.text = fullText.substring(0, charIndex)
                if (charIndex < fullText.length) {
                    handler.postDelayed(this, CHAR_DELAY_MS)
                } else {
                    botView.setTalking(false)
                    botView.setMood(DuinchView.HAPPY)
                }
            }
        }
        typingRunnable = step
        handler.post(step)
    }

    private fun animateExit(root: View, bubble: View, botView: DuinchView, density: Float) {
        if (activeView !== root) return
        typingRunnable?.let { handler.removeCallbacks(it) }
        botView.setTalking(false)
        botView.lookAt(0f, 0f)
        bubble.animate()
            .alpha(0f)
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(200L)
            .start()
        botView.animate()
            .translationX(96f * density)
            .alpha(0f)
            .setDuration(EXIT_DURATION_MS)
            .withEndAction { if (activeView === root) removeActiveImmediate() }
            .start()
    }

    private fun removeActiveImmediate() {
        typingRunnable?.let { handler.removeCallbacks(it) }
        dismissRunnable?.let { handler.removeCallbacks(it) }
        typingRunnable = null
        dismissRunnable = null
        val view = activeView
        val wm = activeWindowManager
        activeView = null
        activeWindowManager = null
        if (view != null && wm != null) {
            try {
                wm.removeViewImmediate(view)
            } catch (_: Exception) {}
        }
    }

    private fun resolveWindowTarget(context: Context): Triple<WindowManager, Int, Context>? {
        val service = InputKeyService.activeInstance()
        if (service != null) {
            val wm = service.getSystemService(WindowManager::class.java)
            if (wm != null) {
                return Triple(wm, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, service)
            }
        }
        val appContext = context.applicationContext
        if (Settings.canDrawOverlays(appContext)) {
            val wm = appContext.getSystemService(WindowManager::class.java)
            if (wm != null) {
                return Triple(wm, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, appContext)
            }
        }
        return null
    }
}
