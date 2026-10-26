package com.gathin.tvhome.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.settings.data.Config
import com.gathin.tvhome.feature.system.data.TvSettings
import com.gathin.tvhome.feature.main.ui.MainActivity
import com.gathin.tvhome.feature.agent.ui.AgentAssistantShortcut
import com.gathin.tvhome.feature.system.data.RemoteKeys

class HomeKeyService : AccessibilityService() {

    private var assistantShortcut: AgentAssistantShortcut? = null
    private var settingsScancodes = IntArray(0)
    private var settingsKeycodes = IntArray(0)
    private var settingsPanel = false
    private var appKeys: Map<Int, String> = emptyMap()
    private val settle = Handler(Looper.getMainLooper())
    private val replaceOtherHome = Runnable {
        rememberStandingIn()
        openHome()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        assistantShortcut?.close()
        assistantShortcut = AgentAssistantShortcut(this)
        AgentAssistantShortcut.syncSystemSettings(this, AgentAssistantShortcut.target(this))
        settingsScancodes = resources.getIntArray(R.array.settings_key_scancodes)
        val config = Config.load(this)
        settingsKeycodes = config.settingsKeycodes
        settingsPanel = config.settingsKeyPanel
        appKeys = RemoteKeys.parseAppKeys(config.appKeys)
        if (SystemClock.elapsedRealtime() < JUST_BOOTED_MS) openHome()
    }

    private fun isSettingsKey(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_SETTINGS) return true
        for (code in settingsScancodes) if (event.scanCode == code) return true
        for (code in settingsKeycodes) if (event.keyCode == code) return true
        return false
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (assistantShortcut?.onKeyEvent(event) == true) return true

        if (isSettingsKey(event)) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                val open = if (settingsPanel) {
                    Intent(this, MainActivity::class.java).apply {
                        action = MainActivity.ACTION_SHOW_QUICK_SETTINGS
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                } else {
                    TvSettings.main(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(open)
            }
            return true
        }
        val app = appKeys[event.keyCode]
        if (app != null) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) RemoteKeys.launch(this, app)
            return true
        }
        val home = event.keyCode == KeyEvent.KEYCODE_ALL_APPS || event.keyCode == KeyEvent.KEYCODE_HOME
        if (!home) return false
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) openHome()
        return true
    }

    private fun isOtherHomeWindow(event: AccessibilityEvent): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val homes = packageManager.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
        for (candidate in homes) {
            val activity = candidate.activityInfo ?: continue
            if (packageName == activity.packageName) continue
            if (isWindowOf(activity, event)) return true
        }
        return false
    }

    private fun isActivityWindow(event: AccessibilityEvent): Boolean {
        val pkg = event.packageName ?: return false
        val cls = event.className ?: return false
        val window = ComponentName(pkg.toString(), cls.toString())
        return try {
            packageManager.getActivityInfo(window, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun standsInForHome(): Boolean {
        return getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE).getBoolean(STANDS_IN_FOR_HOME, false)
    }

    private fun rememberStandingIn() {
        if (standsInForHome()) return
        getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE).edit().putBoolean(STANDS_IN_FOR_HOME, true).apply()
    }

    private fun openHome() {
        if (performGlobalAction(GLOBAL_ACTION_HOME)) return
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setPackage(packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        try {
            startActivity(home)
        } catch (_: Exception) {}
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.packageName == null) return
        if (isOtherHomeWindow(event)) {
            settle.removeCallbacks(replaceOtherHome)
            settle.postDelayed(replaceOtherHome, SETTLE_MS)
        } else if (isActivityWindow(event)) {
            settle.removeCallbacks(replaceOtherHome)
        }
    }

    override fun onDestroy() {
        settle.removeCallbacks(replaceOtherHome)
        assistantShortcut?.close()
        super.onDestroy()
    }

    override fun onInterrupt() {
        settle.removeCallbacks(replaceOtherHome)
    }

    companion object {
        private const val JUST_BOOTED_MS = 3 * 60_000L
        private const val STANDS_IN_FOR_HOME = "stands_in_for_home"
        private const val SETTLE_MS = 300L

        private fun isWindowOf(activity: ActivityInfo, event: AccessibilityEvent): Boolean {
            return activity.packageName == event.packageName?.toString() &&
                    event.className != null && activity.name == event.className?.toString()
        }
    }
}
