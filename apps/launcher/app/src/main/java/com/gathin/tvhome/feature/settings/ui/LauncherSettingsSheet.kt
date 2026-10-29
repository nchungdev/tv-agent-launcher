package com.gathin.tvhome.feature.settings.ui
import com.gathin.tvhome.feature.settings.data.*
import com.gathin.tvhome.feature.settings.widget.*

import android.app.Activity
import android.app.Dialog
import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.settings.data.Config
import com.gathin.tvhome.core.design.M3Skin
import com.gathin.tvhome.core.design.M3Switch
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.core.design.Shapes
import com.gathin.tvhome.core.motion.FocusScrollView
import com.gathin.tvhome.core.view.SideSheet
import com.gathin.tvhome.feature.system.ui.Screensaver
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class LauncherSettingsSheet(
    private val activity: Activity,
    private val config: Config,
    private val watching: com.gathin.tvhome.feature.watching.data.ContinueWatching? = null,
    private val onClosed: ((needsRecreate: Boolean) -> Unit)? = null
) {
    private val choices: SharedPreferences = activity.getSharedPreferences(
        Config.SETTINGS_NAME, Activity.MODE_PRIVATE
    )
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val dialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
    private val palette: Palette = Palette.currentOrBaseline()
    private val density: Float = activity.resources.displayMetrics.density

    private var sideSheet: SideSheet? = null
    private var needsRecreate = false
    private lateinit var layoutSection: SettingsLayoutSection

    fun show() {
        val view = LayoutInflater.from(activity).inflate(
            R.layout.activity_launcher_settings, null
        )
        dialog.setContentView(view)
        setupSections(view)

        val sheet = view.findViewById<View>(R.id.sheet)
        val badge = view.findViewById<com.gathin.tvhome.core.view.ShapeBadge>(R.id.settingsBadge)
        badge?.setIcon(activity.getDrawable(R.drawable.ic_home_settings))
        val sheetBg = GradientDrawable().apply {
            setColor(palette.surface)
            cornerRadius = Shapes.EXTRA_LARGE * density
        }
        sheet.background = sheetBg
        sheet.clipToOutline = true
        M3Skin.apply(sheet, palette, config.animations, config.reducedMotion)

        val scrim = view.findViewById<View>(R.id.scrim)
        sideSheet = SideSheet(activity, scrim, sheet, config) {
            worker.shutdown()
            dialog.dismiss()
            onClosed?.invoke(needsRecreate)
        }
        scrim.setOnClickListener { close() }

        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                close()
                true
            } else {
                false
            }
        }

        val scroll = view.findViewById<FocusScrollView>(R.id.settingsScroll)
        scroll.setMotion(config.animations, config.reducedMotion)
        dialog.show()
        sideSheet?.slideIn(scroll.getChildAt(0) as ViewGroup)
        layoutSection.requestInitialFocus()
    }

    fun close() {
        sideSheet?.close()
    }

    fun isShowing(): Boolean = dialog.isShowing

    private fun setupSections(view: View) {
        layoutSection = SettingsLayoutSection(activity, config, choices, palette) {
            needsRecreate = true
        }
        layoutSection.bind(view)

        val agentSection = SettingsAgentSection(activity, config, choices, palette)
        agentSection.bind(view)

        initLookAndScreensaver(view)
        view.findViewById<TextView>(R.id.playbackChoice).setText(R.string.playback_settings)
        view.findViewById<TextView>(R.id.playbackChoice).setOnClickListener {
            com.gathin.tvhome.feature.playback.PlaybackChoices.settings(activity)
        }
        val watchSources = view.findViewById<TextView>(R.id.watchSourcesChoice)
        watchSources.setText(R.string.watch_sources)
        watchSources.setCompoundDrawablesRelativeWithIntrinsicBounds(
            SettingsIcons.icon(activity, palette, R.drawable.ic_videocam), null, null, null
        )
        watchSources.setOnClickListener {
            val feed = watching ?: com.gathin.tvhome.feature.watching.data.ContinueWatching(activity)
            com.gathin.tvhome.feature.watching.ui.WatchNextSourceSheet(activity, config, feed).show()
        }
        WeatherLocationPicker.bind(activity, config, view.findViewById(R.id.weatherLocationChoice)) {
            needsRecreate = true
        }

        val systemSection = SettingsSystemSection(
            activity, config, choices, palette, worker,
            { needsRecreate = true }, { close() }
        )
        systemSection.bind(view)
    }

    private fun initLookAndScreensaver(view: View) {
        val colorChoice = view.findViewById<TextView>(R.id.colorChoice)
        colorChoice.setCompoundDrawablesRelativeWithIntrinsicBounds(
            SettingsIcons.icon(activity, palette, R.drawable.ic_picture), null, null, null
        )
        val backdropChoice = view.findViewById<TextView>(R.id.backdropChoice)
        backdropChoice.setCompoundDrawablesRelativeWithIntrinsicBounds(
            SettingsIcons.icon(activity, palette, R.drawable.ic_picture), null, null, null
        )

        colorChoice.setOnClickListener {
            SettingsLookPicker.chooseColor(activity, config, choices) {
                needsRecreate = true
                SettingsLookPicker.updateLook(activity, choices, colorChoice, backdropChoice)
            }
        }
        backdropChoice.setOnClickListener {
            SettingsLookPicker.chooseBackdrop(activity, config, choices) {
                needsRecreate = true
                SettingsLookPicker.updateLook(activity, choices, colorChoice, backdropChoice)
            }
        }
        SettingsLookPicker.updateLook(activity, choices, colorChoice, backdropChoice)

        initScreensaverControls(view)
    }

    private fun initScreensaverControls(view: View) {
        val saverSwitch = M3Switch(density, palette, config.animations)
        val toggleChoice = view.findViewById<TextView>(R.id.screensaverToggleChoice)
        toggleChoice.setCompoundDrawablesRelativeWithIntrinsicBounds(
            SettingsIcons.icon(activity, palette, R.drawable.ic_timer), null, saverSwitch, null
        )
        val timeChoice = view.findViewById<TextView>(R.id.screensaverTimeChoice)
        timeChoice.setCompoundDrawablesRelativeWithIntrinsicBounds(
            SettingsIcons.icon(activity, palette, R.drawable.ic_timer), null, null, null
        )
        val styleChoice = view.findViewById<TextView>(R.id.screensaverStyleChoice)
        styleChoice.setCompoundDrawablesRelativeWithIntrinsicBounds(
            SettingsIcons.icon(activity, palette, R.drawable.ic_timer), null, null, null
        )

        toggleChoice.setOnClickListener {
            val enabled = !Screensaver.isEnabled(activity)
            if (Screensaver.setEnabled(activity, enabled)) {
                saverSwitch.setChecked(enabled)
            } else {
                Toast.makeText(activity, R.string.screensaver_denied, Toast.LENGTH_SHORT).show()
            }
        }
        timeChoice.setOnClickListener {
            SettingsLookPicker.chooseScreensaverTime(activity, config) {
                SettingsLookPicker.updateScreensaver(
                    activity, choices, saverSwitch, toggleChoice, timeChoice, styleChoice
                )
            }
        }
        styleChoice.setOnClickListener {
            SettingsLookPicker.chooseScreensaverStyle(activity, config, choices) {
                SettingsLookPicker.updateScreensaver(
                    activity, choices, saverSwitch, toggleChoice, timeChoice, styleChoice
                )
            }
        }
        val preview = view.findViewById<TextView>(R.id.screensaverPreviewChoice)
        preview.text = activity.getString(R.string.screensaver_preview)
        preview.setCompoundDrawablesRelativeWithIntrinsicBounds(
            SettingsIcons.icon(activity, palette, R.drawable.ic_open_in_new), null, null, null
        )
        preview.setOnClickListener { Screensaver.preview(activity) }
        SettingsLookPicker.updateScreensaver(
            activity, choices, saverSwitch, toggleChoice, timeChoice, styleChoice
        )
    }
}
