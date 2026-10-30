package com.gathin.tvhome.feature.agent.coordinator

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import com.gathin.tvhome.R
import com.gathin.tvhome.core.motion.FocusScrollView
import com.gathin.tvhome.core.view.AgentCardData
import java.util.WeakHashMap

object MediaProviderSheet {
    private val open = WeakHashMap<Activity, Dialog>()

    fun close(activity: Activity) { open.remove(activity)?.dismiss() }

    fun add(activity: Activity, parent: LinearLayout, card: AgentCardData, source: String,
        render: (MediaDetailView) -> Unit) {
        val density = activity.resources.displayMetrics.density
        val entry = MediaActionView(activity, source.uppercase()).apply {
            tag = "media-provider:$source"
            contentDescription = activity.getString(R.string.media_provider_expand, source)
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            textSize = 15f
            minHeight = (48 * density).toInt()
            setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_chevron_right, 0)
            compoundDrawablesRelative[2]?.mutate()?.setTint(currentTextColor)
            compoundDrawablePadding = (16 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
                topMargin = (12 * density).toInt()
            }
        }
        entry.setOnClickListener {
            close(activity)
            val view = MediaDetailView(activity, card.copy(sourceId = source), compact = true)
            view.content.setPadding(0, (16 * density).toInt(), 0, 0)
            val scroll = FocusScrollView(activity).apply {
                isFillViewport = true
                addView(view)
            }
            val dialog = Dialog(activity, android.R.style.Theme_Material_NoActionBar_Fullscreen)
            dialog.setContentView(scroll)
            dialog.window?.setWindowAnimations(0)
            view.back.setOnClickListener { dialog.dismiss() }
            render(view)
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
                if (entry.isAttachedToWindow) entry.requestFocus()
            }
            open[activity] = dialog
            dialog.show()
            dialog.window?.setLayout(-1, -1)
            view.focusInitial()
        }
        parent.addView(entry)
    }
}
