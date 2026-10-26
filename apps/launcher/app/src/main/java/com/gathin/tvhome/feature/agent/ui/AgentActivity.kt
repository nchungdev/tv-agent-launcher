package com.gathin.tvhome.feature.agent.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.gathin.tvhome.service.ClockDream

class AgentActivity : Activity(), AgentPanel.Host {

    private var panel: AgentPanel? = null

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        if (ClockDream.isDreaming || AgentOverlay.openWhenHomeReturns()) {
            finishWithoutAnimation()
            return
        }
        val created = AgentPanel(this, this)
        panel = created
        setContentView(created)
        created.start()
    }

    override fun onResume() {
        super.onResume()
        panel?.resume()
    }

    override fun onPause() {
        panel?.pause()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        panel?.restart()
    }

    override fun onRequestPermissionsResult(
        code: Int,
        permissions: Array<out String>,
        results: IntArray
    ) {
        if (code != AgentSpeechInput.MIC_REQUEST) return
        panel?.onPermissionResult(results.isNotEmpty() && results[0] == PERMISSION_GRANTED)
    }

    override fun closePanel() = finish()

    override fun onDestroy() {
        panel?.release()
        super.onDestroy()
    }

    private fun finishWithoutAnimation() {
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val PERMISSION_GRANTED = android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
