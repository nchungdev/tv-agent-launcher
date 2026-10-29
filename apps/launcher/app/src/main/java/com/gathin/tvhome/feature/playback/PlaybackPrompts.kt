package com.gathin.tvhome.feature.playback

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.app.Dialog
import android.os.Bundle
import java.util.WeakHashMap

object PlaybackPrompts {
    private val open = WeakHashMap<Activity, Dialog>()

    fun close(activity: Activity) {
        open.remove(activity)?.dismiss()
    }

    fun show(activity: Activity, builder: AlertDialog.Builder) {
        showDialog(activity, builder.create())
    }

    fun showDialog(activity: Activity, dialog: Dialog) {
        close(activity)
        val lifecycle = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStopped(host: Activity) {
                if (host === activity) dialog.dismiss()
            }
            override fun onActivityDestroyed(host: Activity) {
                if (host === activity) dialog.dismiss()
            }
            override fun onActivityCreated(host: Activity, state: Bundle?) {}
            override fun onActivityStarted(host: Activity) {}
            override fun onActivityResumed(host: Activity) {}
            override fun onActivityPaused(host: Activity) {}
            override fun onActivitySaveInstanceState(host: Activity, state: Bundle) {}
        }
        activity.application.registerActivityLifecycleCallbacks(lifecycle)
        dialog.setOnDismissListener {
            activity.application.unregisterActivityLifecycleCallbacks(lifecycle)
            if (open[activity] === dialog) open.remove(activity)
        }
        open[activity] = dialog
        dialog.show()
    }
}
