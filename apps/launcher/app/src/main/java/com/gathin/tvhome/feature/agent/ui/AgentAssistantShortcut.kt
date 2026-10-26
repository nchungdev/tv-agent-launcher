package com.gathin.tvhome.feature.agent.ui
import com.gathin.tvhome.feature.agent.data.*
import com.gathin.tvhome.feature.agent.coordinator.*
import com.gathin.tvhome.feature.agent.widget.*
import com.gathin.tvhome.feature.agent.adapter.*

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.settings.data.Config
import com.gathin.tvhome.feature.agent.ui.AgentActivity


class AgentAssistantShortcut(private val context: Context) {

    private var capturedKey = KeyEvent.KEYCODE_UNKNOWN
    private var closed = false

    fun onKeyEvent(event: KeyEvent): Boolean {
        if (com.gathin.tvhome.service.ClockDream.isDreaming) return false
        val key = event.keyCode
        if (key != KeyEvent.KEYCODE_ASSIST && key != KeyEvent.KEYCODE_VOICE_ASSIST && key != KeyEvent.KEYCODE_MENU) return false
        if (closed) return false

        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount == 0 && capturedKey == KeyEvent.KEYCODE_UNKNOWN) {
                capturedKey = key
                Log.i(TAG, "Captured assistant key $key")
            }
            return capturedKey == key
        }
        if (event.action != KeyEvent.ACTION_UP || capturedKey != key) return false

        capturedKey = KeyEvent.KEYCODE_UNKNOWN
        if (!event.isCanceled) openTarget()
        return true
    }

    private fun openTarget() {
        if (closed) return
        if (target(context) == TARGET_SYSTEM) {
            openSystemAssistant()
        } else {
            openAgent()
        }
    }

    private fun openAgent(): Boolean {
        val agent = Intent(context, AgentActivity::class.java)
        val opened = open(agent)
        if (opened) Log.i(TAG, "Opened Duinch AI Agent")
        return opened
    }

    private fun openSystemAssistant() {
        Log.i(TAG, "Opening system assistant")
        val katniss = Intent(Intent.ACTION_ASSIST)
            .setComponent(ComponentName(KATNISS_PKG, KATNISS_TRAMPOLINE))
        if (open(katniss)) return

        if (!open(Intent("android.intent.action.VOICE_ASSIST"))) {
            open(Intent(Intent.ACTION_ASSIST))
        }
    }

    private fun open(intent: Intent): Boolean {
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    fun close() {
        closed = true
        capturedKey = KeyEvent.KEYCODE_UNKNOWN
    }

    companion object {
        private const val TAG = "TvHomeAssistant"

        const val TARGET_KEY = "assistant_target"
        const val TARGET_DUINCH = 0
        const val TARGET_SYSTEM = 1
        const val TARGET_COUNT = 2

        private const val KATNISS_SERVICE = "com.google.android.katniss/.search.serviceapi.KatnissVoiceInteractionService"
        private const val KATNISS_PKG = "com.google.android.katniss"
        private const val KATNISS_TRAMPOLINE = "com.google.android.apps.tvsearch.app.launch.trampoline.SearchActivityTrampoline"

        @JvmStatic
        fun target(context: Context): Int {
            val prefs = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(TARGET_KEY, TARGET_DUINCH)
        }

        @JvmStatic
        fun setTarget(context: Context, target: Int) {
            val prefs = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt(TARGET_KEY, target).apply()
            syncSystemSettings(context, target)
        }

        @JvmStatic
        fun syncSystemSettings(context: Context, target: Int) {
            try {
                if (target == TARGET_SYSTEM) {
                    Settings.Secure.putString(context.contentResolver, "assistant", KATNISS_SERVICE)
                } else {
                    val launcherAssist = ComponentName(context, AgentActivity::class.java)
                        .flattenToShortString()
                    Settings.Secure.putString(context.contentResolver, "assistant", launcherAssist)
                }
                Settings.Secure.putString(
                    context.contentResolver,
                    "voice_interaction_service",
                    KATNISS_SERVICE
                )
            } catch (_: SecurityException) {}
        }

        @JvmStatic
        fun targetLabel(context: Context, target: Int): String {
            return context.getString(if (target == TARGET_SYSTEM) R.string.assistant_target_system else R.string.assistant_target_duinch)
        }
    }
}
