package com.gathin.tvhome.feature.agent.ui

import android.app.Activity

object AgentLogin {
    private var open: AgentLoginDialog? = null

    fun request(activity: Activity, onSignedIn: () -> Unit = {}) {
        if (open != null || activity.isFinishing || activity.isDestroyed) return
        val login = AgentLoginDialog(activity) { signedIn ->
            open = null
            if (signedIn) onSignedIn()
        }
        open = login
        login.show()
    }
}
