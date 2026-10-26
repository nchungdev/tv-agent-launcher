package com.gathin.tvhome.feature.system.coordinator
import com.gathin.tvhome.feature.system.data.*
import com.gathin.tvhome.feature.system.widget.*

import android.view.KeyEvent


class HomeKeyHandler(
    private val settingsKeycodes: IntArray,
    private val settingsKeyPanel: Boolean,
    private val onOpenQuickSettings: () -> Unit,
    private val onOpenTvSettings: () -> Unit,
    private val onWakeBackdrop: () -> Unit,
    private val onDuinchLook: (keyCode: Int) -> Unit
) {

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            onWakeBackdrop()
            onDuinchLook(event.keyCode)
        }
        return handleSettingsKey(event)
    }

    private fun handleSettingsKey(event: KeyEvent): Boolean {
        for (code in settingsKeycodes) {
            if (event.keyCode != code) continue
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                if (settingsKeyPanel) {
                    onOpenQuickSettings()
                } else {
                    onOpenTvSettings()
                }
            }
            return true
        }
        return false
    }
}
