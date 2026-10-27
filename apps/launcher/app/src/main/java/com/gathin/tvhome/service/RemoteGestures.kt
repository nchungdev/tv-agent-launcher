package com.gathin.tvhome.service

import android.accessibilityservice.AccessibilityService
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.KeyEvent
import android.widget.Toast
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.main.ui.MainActivity
import com.gathin.tvhome.feature.runningapps.data.BackgroundApps

object RemoteGestures {
    private const val BACK_HOLD_MS = 700L
    private const val RESET_HOLD_MS = 10_000L
    private const val INJECTED_BACK_MS = 400L
    private const val RESTART_DELAY_MS = 800L

    private val main = Handler(Looper.getMainLooper())
    private var lastEvent = ""
    private var backHeld = false
    private var homeHeld = false
    private var injectedBackUntil = 0L
    private var holder: AccessibilityService? = null

    private val backHold = Runnable {
        backHeld = true
        holder?.let { openHome(it) }
    }

    private val homeHold = Runnable {
        homeHeld = true
        holder?.let { reset(it) }
    }

    fun onKeyEvent(service: AccessibilityService, event: KeyEvent): Boolean {
        val home = event.keyCode == KeyEvent.KEYCODE_HOME ||
            event.keyCode == KeyEvent.KEYCODE_ALL_APPS
        val back = event.keyCode == KeyEvent.KEYCODE_BACK
        if (!home && !back) return false
        if (back && SystemClock.uptimeMillis() < injectedBackUntil) return false
        if (isRepeatOf(event)) return true

        if (home) onHome(service, event) else onBack(service, event)
        return true
    }

    fun openHome(service: AccessibilityService) {
        if (service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)) return
        val home = Intent(service, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        try {
            service.startActivity(home)
        } catch (_: Exception) {}
    }

    private fun isRepeatOf(event: KeyEvent): Boolean {
        val identity = "${event.keyCode}:${event.action}:${event.downTime}:${event.eventTime}"
        if (identity == lastEvent) return true
        lastEvent = identity
        return false
    }

    private fun onHome(service: AccessibilityService, event: KeyEvent) {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount > 0) return
            holder = service
            homeHeld = false
            main.removeCallbacks(homeHold)
            main.postDelayed(homeHold, RESET_HOLD_MS)
            return
        }
        main.removeCallbacks(homeHold)
        if (!homeHeld && !event.isCanceled) openHome(service)
    }

    private fun onBack(service: AccessibilityService, event: KeyEvent) {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount > 0) return
            holder = service
            backHeld = false
            main.removeCallbacks(backHold)
            main.postDelayed(backHold, BACK_HOLD_MS)
            return
        }
        main.removeCallbacks(backHold)
        if (backHeld || event.isCanceled) return
        injectedBackUntil = SystemClock.uptimeMillis() + INJECTED_BACK_MS
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    private fun reset(service: AccessibilityService) {
        val context = service.applicationContext
        Toast.makeText(context, R.string.restarting_launcher, Toast.LENGTH_SHORT).show()
        Thread {
            BackgroundApps.stopAll(context)
            restartLauncher(context)
        }.start()
    }

    private fun restartLauncher(context: Context) {
        val home = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val pending = PendingIntent.getActivity(context, 0, home, flags)
        val alarms = context.getSystemService(AlarmManager::class.java)
        val at = SystemClock.elapsedRealtime() + RESTART_DELAY_MS
        alarms?.set(AlarmManager.ELAPSED_REALTIME, at, pending)
        Process.killProcess(Process.myPid())
    }
}
