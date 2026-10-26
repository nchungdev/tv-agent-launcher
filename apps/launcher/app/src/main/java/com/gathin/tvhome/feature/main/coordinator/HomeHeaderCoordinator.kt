package com.gathin.tvhome.feature.main.coordinator

import android.app.Activity
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.settings.data.Config
import com.gathin.tvhome.feature.system.data.Weather


class HomeHeaderCoordinator(
    private val activity: Activity,
    private val onAction: (HeaderAction) -> Unit
) {
    sealed interface HeaderAction {
        object OpenInputs : HeaderAction
        object OpenNetwork : HeaderAction
        object ShowNetworkMenu : HeaderAction
        object OpenRunningApps : HeaderAction
        object OpenNotifications : HeaderAction
        object OpenSettings : HeaderAction
        object ShowSettingsMenu : HeaderAction
        object ToggleReorder : HeaderAction
        object FocusGridFirst : HeaderAction
        object FocusGridFromHeader : HeaderAction
    }

    private var btnSysInfo: View? = null
    private var btnWeather: View? = null
    private var btnInputs: View? = null
    private var btnWifi: View? = null
    private var btnNotifications: TextView? = null
    private var btnReorder: View? = null
    private var btnSettings: View? = null

    fun setup(root: View, cfg: Config) {
        btnSysInfo = root.findViewById(R.id.btnSysInfo)
        btnWeather = root.findViewById(R.id.btnWeather)
        btnInputs = root.findViewById(R.id.btnInputs)
        btnWifi = root.findViewById(R.id.btnWifi)
        btnNotifications = root.findViewById(R.id.btnNotifications)
        btnReorder = root.findViewById(R.id.btnReorder)
        btnSettings = root.findViewById(R.id.btnSettings)

        btnSysInfo?.setOnClickListener { onAction(HeaderAction.OpenRunningApps) }
        btnWeather?.setOnClickListener {
            val report = Weather.cached(activity)
            if (report != null) {
                val detail = "${report.place}: ${report.temp.toInt()}°C"
                Toast.makeText(activity, detail, Toast.LENGTH_SHORT).show()
            }
        }
        btnInputs?.setOnClickListener { onAction(HeaderAction.OpenInputs) }
        btnWifi?.setOnClickListener { onAction(HeaderAction.OpenNetwork) }
        btnWifi?.setOnLongClickListener {
            onAction(HeaderAction.ShowNetworkMenu)
            true
        }
        btnNotifications?.setOnClickListener { onAction(HeaderAction.OpenNotifications) }
        btnReorder?.setOnClickListener { onAction(HeaderAction.ToggleReorder) }
        btnReorder?.visibility = if (cfg.reorderButton) View.VISIBLE else View.GONE
        btnSettings?.setOnClickListener { onAction(HeaderAction.OpenSettings) }
        btnSettings?.setOnLongClickListener {
            onAction(HeaderAction.ShowSettingsMenu)
            true
        }

        setupKeyNavigation()
    }

    private fun setupKeyNavigation() {
        val pillKeys = View.OnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@OnKeyListener false
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return@OnKeyListener true
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                onAction(HeaderAction.FocusGridFromHeader)
                return@OnKeyListener true
            }
            false
        }
        btnSysInfo?.setOnKeyListener(pillKeys)
        btnWeather?.setOnKeyListener(pillKeys)
        btnInputs?.setOnKeyListener(pillKeys)
        btnWifi?.setOnKeyListener(pillKeys)
        btnNotifications?.setOnKeyListener(pillKeys)
        btnReorder?.setOnKeyListener(pillKeys)
        btnSettings?.setOnKeyListener(pillKeys)
    }

    fun focusDefault() {
        val target = btnInputs ?: btnWifi ?: btnSettings
        target?.requestFocus()
    }

    fun updateNotificationBadge(count: Int) {
        btnNotifications?.let { badge ->
            val iconRes = if (count > 0) R.drawable.ic_notifications_dot else R.drawable.ic_notifications
            badge.setCompoundDrawablesWithIntrinsicBounds(
                activity.getDrawable(iconRes), null, null, null
            )
        }
    }

    fun updateReorderVisibility(visible: Boolean) {
        btnReorder?.visibility = if (visible) View.VISIBLE else View.GONE
    }
}
