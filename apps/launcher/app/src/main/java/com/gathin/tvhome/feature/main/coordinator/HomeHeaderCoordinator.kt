package com.gathin.tvhome.feature.main.coordinator

import android.app.Activity
import android.content.Intent
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.account.coordinator.GoogleAccountCoordinator
import com.gathin.tvhome.feature.settings.data.Config
import com.gathin.tvhome.feature.system.data.Weather
import com.gathin.tvhome.feature.system.widget.SysInfoView


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

    var onWeatherRefresh: (() -> Unit)? = null
    private var weatherReport: Weather.Report? = null

    private var btnSysInfo: SysInfoView? = null
    private var btnWeather: TextView? = null
    private var btnInputs: View? = null
    private var btnWifi: View? = null
    private var btnNotifications: TextView? = null
    private var btnReorder: View? = null
    private var btnSettings: View? = null
    private var btnAccount: TextView? = null
    private lateinit var googleAccount: GoogleAccountCoordinator

    fun setup(root: View, cfg: Config) {
        btnSysInfo = root.findViewById(R.id.btnSysInfo)
        btnWeather = root.findViewById(R.id.btnWeather)
        btnInputs = root.findViewById(R.id.btnInputs)
        btnWifi = root.findViewById(R.id.btnWifi)
        btnNotifications = root.findViewById(R.id.btnNotifications)
        btnReorder = root.findViewById(R.id.btnReorder)
        btnSettings = root.findViewById(R.id.btnSettings)
        btnAccount = root.findViewById(R.id.btnAccount)
        googleAccount = GoogleAccountCoordinator(activity, root.findViewById(R.id.btnAccount), cfg)

        btnSysInfo?.init(cfg.animations, cfg.reducedMotion)
        btnSysInfo?.visibility = View.VISIBLE
        btnSysInfo?.setOnClickListener { onAction(HeaderAction.OpenRunningApps) }
        updateWeather(null, false)

        btnWeather?.setOnClickListener {
            val report = weatherReport
            if (report != null) {
                val sky = Weather.sky(report.code)
                val status = activity.getString(Weather.label(sky))
                val detail = "${report.place}: ${report.temp.toInt()}°C • $status"
                Toast.makeText(activity, detail, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(activity, "Đang nạp thời tiết…", Toast.LENGTH_SHORT).show()
            }
            onWeatherRefresh?.invoke()
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

    fun updateSysInfo(cpuPercent: Int) {
        btnSysInfo?.updateStats(cpuPercent)
    }

    fun updateWeather(report: Weather.Report?, day: Boolean) {
        weatherReport = report
        val tv = btnWeather ?: return
        if (tv.visibility == View.GONE) return
        if (report != null) {
            tv.visibility = View.VISIBLE
            tv.text = "${report.temp.toInt()}°"
            val iconRes = Weather.icon(Weather.sky(report.code), day)
            val d = activity.getDrawable(iconRes)?.mutate()
            if (d != null) {
                val size = (16 * activity.resources.displayMetrics.density).toInt()
                d.setBounds(0, 0, size, size)
                d.setTint(tv.currentTextColor)
                tv.setCompoundDrawables(d, null, null, null)
            }
        } else {
            tv.visibility = View.VISIBLE
            tv.text = "--°"
            val d = activity.getDrawable(R.drawable.ic_w_partly_day)?.mutate()
            if (d != null) {
                val size = (16 * activity.resources.displayMetrics.density).toInt()
                d.setBounds(0, 0, size, size)
                d.setTint(tv.currentTextColor)
                tv.setCompoundDrawables(d, null, null, null)
            }
        }
    }

    private fun setupKeyNavigation() {
        val pillKeys = View.OnKeyListener { v, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@OnKeyListener false
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return@OnKeyListener true
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                val pills = listOfNotNull(btnSysInfo, btnWeather, btnInputs, btnWifi, btnNotifications, btnReorder, btnSettings, btnAccount)
                    .filter { it.visibility == View.VISIBLE }
                if (pills.firstOrNull() === v) {
                    val bot = activity.findViewById<View>(R.id.duinchBot)
                    if (bot != null && bot.isShown && bot.requestFocus()) return@OnKeyListener true
                }
            }
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
        btnAccount?.setOnKeyListener(pillKeys)
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

    fun start() = googleAccount.start()

    fun stop() = googleAccount.stop()

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        googleAccount.onActivityResult(requestCode, resultCode, data)
    }
}
