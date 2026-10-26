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

    private var btnInputs: View? = null
    private var btnWifi: View? = null
    private var btnWeather: View? = null
    private var btnSysInfo: View? = null
    private var btnNotifications: TextView? = null
    private var btnReorder: View? = null
    private var btnSettings: View? = null

    fun setup(root: View, cfg: Config) {
        btnInputs = root.findViewById(R.id.btnInputs)
        btnWifi = root.findViewById(R.id.btnWifi)
        btnWeather = root.findViewById(R.id.btnWeather)
        btnSysInfo = root.findViewById(R.id.btnSysInfo)
        btnNotifications = root.findViewById(R.id.btnNotifications)
        btnReorder = root.findViewById(R.id.btnReorder)
        btnSettings = root.findViewById(R.id.btnSettings)

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
        btnSysInfo?.setOnClickListener { onAction(HeaderAction.OpenRunningApps) }
        btnNotifications?.setOnClickListener { onAction(HeaderAction.OpenNotifications) }
        btnSettings?.setOnClickListener { onAction(HeaderAction.OpenSettings) }
        btnSettings?.setOnLongClickListener {
            onAction(HeaderAction.ShowSettingsMenu)
            true
        }
        btnReorder?.setOnClickListener { onAction(HeaderAction.ToggleReorder) }
        btnReorder?.visibility = if (cfg.reorderButton) View.VISIBLE else View.GONE

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
        btnWeather?.setOnKeyListener(pillKeys)
        btnInputs?.setOnKeyListener(pillKeys)
        btnWifi?.setOnKeyListener(pillKeys)
        btnNotifications?.setOnKeyListener(pillKeys)
        btnSysInfo?.setOnKeyListener(pillKeys)
        btnSettings?.setOnKeyListener(pillKeys)
        btnReorder?.setOnKeyListener(pillKeys)
    }

    fun updateNotificationBadge(count: Int) {
        btnNotifications?.let { badge ->
            if (count > 0) {
                badge.text = count.toString()
                badge.visibility = View.VISIBLE
            } else {
                badge.text = ""
                badge.visibility = View.GONE
            }
        }
    }

    fun updateReorderVisibility(visible: Boolean) {
        btnReorder?.visibility = if (visible) View.VISIBLE else View.GONE
    }
}
