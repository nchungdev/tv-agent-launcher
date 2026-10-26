package com.gathin.tvhome.feature.agent.ui

import android.app.Activity
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

class AgentOverlay(private val activity: Activity, private val container: ViewGroup) :
    AgentPanel.Host {

    private var panel: AgentPanel? = null
    private var previousFocus: View? = null

    val isOpen: Boolean get() = panel != null

    fun open() {
        panel?.let {
            it.restart()
            return
        }
        previousFocus = activity.currentFocus
        val created = AgentPanel(activity, this)
        panel = created
        val fill = FrameLayout.LayoutParams.MATCH_PARENT
        container.addView(created, FrameLayout.LayoutParams(fill, fill))
        created.start()
    }

    override fun closePanel() {
        val closing = panel ?: return
        panel = null
        closing.release()
        container.removeView(closing)
        previousFocus?.requestFocus()
        previousFocus = null
    }

    fun onPermissionResult(requestCode: Int, results: IntArray) {
        if (requestCode != AgentSpeechInput.MIC_REQUEST) return
        val granted = results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED
        panel?.onPermissionResult(granted)
    }

    fun onResume() {
        current = this
        if (openWhenResumed) {
            openWhenResumed = false
            open()
        }
    }

    fun onPause() {
        if (current === this) current = null
        pausedAt = SystemClock.uptimeMillis()
        closePanel()
    }

    companion object {
        private const val RECENT_PAUSE_MS = 1_500L

        @Volatile
        private var current: AgentOverlay? = null

        @Volatile
        private var pausedAt = 0L

        @Volatile
        private var openWhenResumed = false

        @JvmStatic
        fun openIfHomeVisible(): Boolean {
            val overlay = current ?: return false
            overlay.activity.runOnUiThread { overlay.open() }
            return true
        }

        @JvmStatic
        fun openWhenHomeReturns(): Boolean {
            if (SystemClock.uptimeMillis() - pausedAt > RECENT_PAUSE_MS) return false
            openWhenResumed = true
            return true
        }
    }
}
