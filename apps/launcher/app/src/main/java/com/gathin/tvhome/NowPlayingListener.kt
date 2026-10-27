package com.gathin.tvhome

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import com.gathin.tvhome.feature.watching.data.PlaybackTracker

class NowPlayingListener : NotificationListenerService() {

    private var tracker: PlaybackTracker? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        tracker?.stop()
        val self = ComponentName(this, NowPlayingListener::class.java)
        tracker = PlaybackTracker(applicationContext, self).also { it.start() }
    }

    override fun onListenerDisconnected() {
        tracker?.stop()
        tracker = null
        super.onListenerDisconnected()
    }
}
