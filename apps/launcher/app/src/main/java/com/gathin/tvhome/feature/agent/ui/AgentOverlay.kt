package com.gathin.tvhome.feature.agent.ui

import android.app.Activity
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.gathin.tvhome.R

class AgentOverlay(private val activity: Activity, private val container: ViewGroup) :
    AgentPanel.Host {

    private var panel: AgentPanel? = null
    private var previousFocus: View? = null
    private var panelFocus: View? = null

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
        showHomeHeader(false)
        created.start()
    }

    override fun closePanel() {
        val closing = panel ?: return
        panel = null
        panelFocus = null
        closing.release()
        container.removeView(closing)
        showHomeHeader(true)
        previousFocus?.requestFocus()
        previousFocus = null
    }

    private fun showHomeHeader(visible: Boolean) {
        val alpha = if (visible) 1f else 0f
        for (id in HOME_HEADER) container.findViewById<View>(id)?.animate()?.alpha(alpha)
    }

    fun onPermissionResult(requestCode: Int, results: IntArray) {
        if (requestCode != AgentSpeechInput.MIC_REQUEST) return
        val granted = results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED
        panel?.onPermissionResult(granted)
    }

    fun onResume() {
        current = this
        val pendingOpen = openWhenResumed
        openWhenResumed = false
        val retained = panel
        if (retained != null) {
            retained.resume()
            panelFocus?.takeIf { it.isAttachedToWindow && it.isShown }?.requestFocus()
            panelFocus = null
        } else if (pendingOpen) {
            open()
        }
    }

    fun onPause() {
        if (current === this) current = null
        pausedAt = SystemClock.uptimeMillis()
        panelFocus = panel?.findFocus()
        panel?.pause()
    }

    fun onDestroy() {
        if (current === this) current = null
        closePanel()
    }

    companion object {
        private val HOME_HEADER = intArrayOf(R.id.headerRow, R.id.greetingBlock)
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
