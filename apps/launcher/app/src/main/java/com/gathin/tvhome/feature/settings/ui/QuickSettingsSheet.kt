package com.gathin.tvhome.feature.settings.ui
import com.gathin.tvhome.feature.settings.data.*
import com.gathin.tvhome.feature.settings.widget.*

import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.settings.data.Config
import com.gathin.tvhome.feature.system.data.TvSettings
import com.gathin.tvhome.feature.main.ui.MainActivity
import com.gathin.tvhome.core.design.M3Skin
import com.gathin.tvhome.core.design.Palette


class QuickSettingsSheet(
    private val activity: Activity,
    private val config: Config,
    private val onLauncherSettings: (() -> Unit)? = null,
    private val onClosed: (() -> Unit)? = null
) {
    private val dialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
    private var isClosing = false

    fun show() {
        Palette.loadDarkMode(activity)
        val view = LayoutInflater.from(activity).inflate(
            R.layout.activity_quick_settings, null
        )
        dialog.setContentView(view)
        view.findViewById<View>(R.id.scrim).setOnClickListener { close() }
        val rows = view.findViewById<LinearLayout>(R.id.rows)
        addRow(rows, R.drawable.ic_settings, R.string.tv_settings, 0, TvSettings.main(activity))
        addRow(rows, R.drawable.ic_picture, R.string.quick_picture, 0, TvSettings.picture(activity))
        addRow(
            rows, R.drawable.ic_wifi, R.string.quick_network, 0,
            Intent(Settings.ACTION_WIFI_SETTINGS)
        )
        addRow(
            rows, R.drawable.ic_apps, R.string.quick_apps, R.string.quick_apps_hint,
            Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
        )
        addLauncherSettingsRow(rows)
        val panel = view.findViewById<View>(R.id.panel)
        M3Skin.apply(
            panel, Palette.currentOrBaseline(),
            config.animations, config.reducedMotion
        )
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                close()
                true
            } else false
        }
        dialog.setOnDismissListener {
            onClosed?.invoke()
        }
        dialog.show()
        rows.getChildAt(0)?.requestFocus()
    }

    private fun addLauncherSettingsRow(rows: LinearLayout) {
        val row = LayoutInflater.from(activity).inflate(R.layout.item_input, rows, false)
        row.findViewById<ImageView>(R.id.icon).setImageResource(R.drawable.ic_home_settings)
        row.findViewById<TextView>(R.id.title).setText(R.string.launcher_settings)
        row.findViewById<View>(R.id.dot).visibility = View.GONE
        row.findViewById<View>(R.id.status).visibility = View.GONE
        row.setOnClickListener {
            close()
            if (onLauncherSettings != null) {
                onLauncherSettings.invoke()
            } else {
                val intent = Intent(activity, MainActivity::class.java)
                intent.action = MainActivity.ACTION_LAUNCHER_SETTINGS
                activity.startActivity(intent)
            }
        }
        rows.addView(row)
    }

    private fun addRow(
        rows: LinearLayout, icon: Int, title: Int, status: Int, target: Intent
    ) {
        val row = LayoutInflater.from(activity).inflate(R.layout.item_input, rows, false)
        row.findViewById<ImageView>(R.id.icon).setImageResource(icon)
        row.findViewById<TextView>(R.id.title).setText(title)
        row.findViewById<View>(R.id.dot).visibility = View.GONE
        val hint = row.findViewById<TextView>(R.id.status)
        if (status != 0) hint.setText(status) else hint.visibility = View.GONE
        row.setOnClickListener {
            try {
                activity.startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(activity, R.string.not_available, Toast.LENGTH_SHORT).show()
            }
            close()
        }
        rows.addView(row)
    }

    fun close() {
        if (isClosing) return
        isClosing = true
        if (dialog.isShowing) {
            dialog.dismiss()
        }
    }

    fun isShowing(): Boolean = dialog.isShowing
}
