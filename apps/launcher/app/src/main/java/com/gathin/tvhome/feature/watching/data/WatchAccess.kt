package com.gathin.tvhome.feature.watching.data

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings

object WatchAccess {
    fun mediaSessions(context: Context): Boolean {
        val listener = ComponentName(context.packageName, "com.gathin.tvhome.NowPlayingListener")
        if (Build.VERSION.SDK_INT >= 27) {
            return context.getSystemService(NotificationManager::class.java)
                ?.isNotificationListenerAccessGranted(listener) == true
        }
        val enabled = Settings.Secure.getString(context.contentResolver,
            "enabled_notification_listeners").orEmpty()
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == listener }
    }
}
