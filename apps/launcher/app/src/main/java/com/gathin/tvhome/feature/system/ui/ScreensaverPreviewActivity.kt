package com.gathin.tvhome.feature.system.ui
import com.gathin.tvhome.feature.system.data.*
import com.gathin.tvhome.feature.system.coordinator.*
import com.gathin.tvhome.feature.system.widget.*

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.Window
import android.view.WindowManager
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.system.widget.SleepingRobotScreensaverView


class ScreensaverPreviewActivity : Activity() {

    private var sleepingSaver: SleepingRobotScreensaverView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        setContentView(R.layout.dream_clock)
        sleepingSaver = findViewById(R.id.sleepingSaver)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val saver = sleepingSaver
            if (saver != null) {
                saver.triggerWakeUp(Runnable { finish() })
            } else {
                finish()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
