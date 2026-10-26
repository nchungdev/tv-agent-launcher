package com.gathin.tvhome.service

import android.accessibilityservice.AccessibilityService
import android.content.*
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.agent.ui.AgentAssistantShortcut
import com.gathin.tvhome.feature.main.ui.MainActivity
import com.gathin.tvhome.feature.runningapps.data.BackgroundApps
import com.gathin.tvhome.feature.settings.data.Config
import com.gathin.tvhome.feature.system.data.RemoteKeys
import com.gathin.tvhome.feature.system.data.XiaomiInputs
import java.lang.ref.WeakReference

open class InputKeyService : AccessibilityService() {

    private var assistantShortcut: AgentAssistantShortcut? = null
    private var inputKeycodes = IntArray(0)
    private var appKeys: Map<Int, String> = emptyMap()
    private var runningAppsKeycodes = IntArray(0)
    private var frontPackage: CharSequence = ""
    private var screenOffRegistered = false

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (Intent.ACTION_SCREEN_OFF == intent.action) {
                val appContext = context.applicationContext
                Thread { BackgroundApps.stopAll(appContext) }.start()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = WeakReference(this)
        assistantShortcut?.close()
        assistantShortcut = AgentAssistantShortcut(this)
        AgentAssistantShortcut.syncSystemSettings(this, AgentAssistantShortcut.target(this))
        val config = Config.load(this)
        inputKeycodes = config.inputKeycodes
        appKeys = RemoteKeys.parseAppKeys(config.appKeys)
        runningAppsKeycodes = resources.getIntArray(R.array.running_apps_keycodes)
        if (!screenOffRegistered) {
            registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
            screenOffRegistered = true
        }
    }

    private fun isRunningAppsKey(keyCode: Int): Boolean {
        for (code in runningAppsKeycodes) if (keyCode == code) return true
        return false
    }

    private fun isHomeKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_HOME || keyCode == KeyEvent.KEYCODE_ALL_APPS
    }

    private fun isInputKey(keyCode: Int): Boolean {
        if (keyCode == KeyEvent.KEYCODE_TV || keyCode == KeyEvent.KEYCODE_TV_INPUT) return true
        if (keyCode == KeyEvent.KEYCODE_MENU && !XiaomiInputs.isPlayer(frontPackage)
            && getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE).getBoolean(MENU_OPENS_INPUTS, false)) {
            return true
        }
        for (code in inputKeycodes) if (keyCode == code) return true
        return false
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (assistantShortcut?.onKeyEvent(event) == true) return true

        if (isHomeKey(event.keyCode)) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                openHome()
            }
            return true
        }

        if (isRunningAppsKey(event.keyCode)) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                val runningIntent = Intent(this, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_SHOW_RUNNING_APPS
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(runningIntent)
            }
            return true
        }

        val app = appKeys[event.keyCode]
        if (app == null && !isInputKey(event.keyCode)) return false
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
            if (app != null) {
                RemoteKeys.launch(this, app)
            } else {
                openInputs()
            }
        }
        return true
    }

    private fun openHome() {
        if (performGlobalAction(GLOBAL_ACTION_HOME)) return
        val home = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        try {
            startActivity(home)
        } catch (_: ActivityNotFoundException) {}
    }

    private fun openInputs() {
        var inputs = Intent(ACTION_VIEW_INPUTS)
        if (inputs.resolveActivity(packageManager) == null) {
            inputs = Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_SHOW_INPUTS)
        }
        try {
            startActivity(inputs.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {}
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val app = event.packageName
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || app == null) return
        for (overlay in OVERLAYS) if (overlay == app) return
        frontPackage = app
    }

    override fun onDestroy() {
        if (screenOffRegistered) {
            try {
                unregisterReceiver(screenOffReceiver)
            } catch (_: Exception) {}
            screenOffRegistered = false
        }
        assistantShortcut?.close()
        if (activeService?.get() === this) activeService = null
        super.onDestroy()
    }

    override fun onInterrupt() {}

    companion object {
        private const val ACTION_VIEW_INPUTS = "com.android.tv.action.VIEW_INPUTS"
        const val MENU_OPENS_INPUTS = "menu_opens_inputs"
        private val OVERLAYS = arrayOf("com.android.systemui", "com.xiaomi.mitv.systemui")
        private var activeService: WeakReference<AccessibilityService>? = null

        fun activeInstance(): AccessibilityService? = activeService?.get()
    }
}
