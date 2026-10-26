package com.gathin.tvhome.feature.settings.widget
import com.gathin.tvhome.feature.settings.data.*
import com.gathin.tvhome.feature.settings.ui.*

import android.app.Activity
import android.content.SharedPreferences
import android.view.View
import android.widget.TextView
import com.gathin.tvhome.Contract
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.settings.data.Config
import com.gathin.tvhome.core.design.M3Switch
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.core.util.SoundEffects
import com.gathin.tvhome.feature.main.widget.WidgetShelf


class SettingsLayoutSection(
    private val activity: Activity,
    private val config: Config,
    private val choices: SharedPreferences,
    private val palette: Palette,
    private val onNeedsRecreate: () -> Unit
) {
    private val density: Float = activity.resources.displayMetrics.density
    private var layoutMode = config.layoutMode
    private var columns = config.columns
    private var animations = config.animations
    private var advancedEffects = config.animations && config.advancedEffects

    private lateinit var layoutModeChoice: TextView
    private lateinit var columnsChoice: TextView
    private lateinit var animationsChoice: TextView
    private lateinit var advancedChoice: TextView
    private lateinit var animationsSwitch: M3Switch
    private lateinit var advancedSwitch: M3Switch

    fun bind(view: View) {
        initLayoutAndEffects(view)
        initWidgetsAndSounds(view)
        refreshLayoutState()
    }

    fun requestInitialFocus() {
        layoutModeChoice.requestFocus()
    }

    private fun initLayoutAndEffects(view: View) {
        layoutModeChoice = view.findViewById(R.id.layoutModeChoice)
        layoutModeChoice.setCompoundDrawablesRelativeWithIntrinsicBounds(
            SettingsIcons.icon(activity, palette, R.drawable.ic_apps), null, null, null
        )
        layoutModeChoice.setOnClickListener { chooseLayoutMode() }

        columnsChoice = view.findViewById(R.id.columnsChoice)
        columnsChoice.setCompoundDrawablesRelativeWithIntrinsicBounds(
            SettingsIcons.icon(activity, palette, R.drawable.ic_grid_view), null, null, null
        )
        columnsChoice.setOnClickListener { chooseColumns() }

        animationsSwitch = M3Switch(density, palette, config.animations)
        advancedSwitch = M3Switch(density, palette, config.animations)

        animationsChoice = view.findViewById(R.id.animationsChoice)
        animationsChoice.setCompoundDrawablesRelativeWithIntrinsicBounds(
            SettingsIcons.icon(activity, palette, R.drawable.ic_animation),
            null, animationsSwitch, null
        )
        animationsChoice.setOnClickListener {
            animations = !animations
            if (!animations) advancedEffects = false
            choices.edit().putBoolean("animations", animations)
                .putBoolean("advanced_effects", advancedEffects).apply()
            onNeedsRecreate()
            refreshLayoutState()
        }

        advancedChoice = view.findViewById(R.id.advancedChoice)
        advancedChoice.setCompoundDrawablesRelativeWithIntrinsicBounds(
            SettingsIcons.icon(activity, palette, R.drawable.ic_auto_awesome),
            null, advancedSwitch, null
        )
        advancedChoice.setOnClickListener {
            advancedEffects = !advancedEffects
            choices.edit().putBoolean("advanced_effects", advancedEffects).apply()
            onNeedsRecreate()
            refreshLayoutState()
        }
    }

    private fun refreshLayoutState() {
        val modeLabel = activity.getString(
            if (layoutMode == Config.LAYOUT_DOCK) R.string.layout_mode_dock
            else R.string.layout_mode_grid
        )
        layoutModeChoice.text = SettingsChoiceDialog.item(
            activity, activity.getString(R.string.layout_mode_label), modeLabel
        )
        columnsChoice.visibility = if (layoutMode == Config.LAYOUT_DOCK) View.GONE else View.VISIBLE
        columnsChoice.text = SettingsChoiceDialog.item(
            activity, activity.getString(R.string.columns_label), "$columns"
        )
        animationsChoice.text = SettingsChoiceDialog.item(
            activity,
            activity.getString(R.string.animations_state, if (animations) "On" else "Off"),
            activity.getString(R.string.animations_desc)
        )
        advancedChoice.text = SettingsChoiceDialog.item(
            activity,
            activity.getString(R.string.advanced_effects),
            activity.getString(R.string.advanced_effects_warning)
        )
        animationsSwitch.setChecked(animations)
        advancedSwitch.setChecked(advancedEffects)
        advancedChoice.visibility = if (animations) View.VISIBLE else View.GONE
    }

    private fun chooseLayoutMode() {
        val labels = arrayOf<CharSequence>(
            activity.getString(R.string.layout_mode_grid),
            activity.getString(R.string.layout_mode_dock)
        )
        val selected = if (layoutMode == Config.LAYOUT_DOCK) 1 else 0
        SettingsChoiceDialog.chooseOne(
            activity, R.string.layout_mode_label, labels, null, selected, { index ->
                layoutMode = if (index == 1) Config.LAYOUT_DOCK else Config.LAYOUT_GRID
                choices.edit().putString(Contract.Key.LAYOUT_MODE, layoutMode).apply()
                onNeedsRecreate()
                refreshLayoutState()
            }, config
        )
    }

    private fun chooseColumns() {
        val labels = Array<CharSequence>(COLUMN_OPTIONS.size) { COLUMN_OPTIONS[it].toString() }
        var selected = 0
        for (i in COLUMN_OPTIONS.indices) {
            if (COLUMN_OPTIONS[i] == columns) {
                selected = i
                break
            }
        }
        SettingsChoiceDialog.chooseOne(
            activity, R.string.columns_label, labels, null, selected, { index ->
                columns = COLUMN_OPTIONS[index]
                choices.edit().putInt("columns", columns).apply()
                onNeedsRecreate()
                refreshLayoutState()
            }, config
        )
    }

    private fun initWidgetsAndSounds(view: View) {
        val widgetsChoice = view.findViewById<TextView>(R.id.widgetsChoice)
        widgetsChoice.text = SettingsChoiceDialog.item(
            activity, activity.getString(R.string.widgets),
            activity.getString(R.string.widgets_desc)
        )
        val widgetsDefault = activity.resources.getBoolean(R.bool.widgets)
        val widgetsSwitch = M3Switch(density, palette, config.animations)
        widgetsSwitch.setChecked(choices.getBoolean(WidgetShelf.PREF_KEY, widgetsDefault))
        widgetsChoice.setCompoundDrawablesRelativeWithIntrinsicBounds(
            SettingsIcons.icon(activity, palette, R.drawable.ic_widgets),
            null, widgetsSwitch, null
        )
        widgetsChoice.setOnClickListener {
            val on = !choices.getBoolean(WidgetShelf.PREF_KEY, widgetsDefault)
            choices.edit().putBoolean(WidgetShelf.PREF_KEY, on).apply()
            widgetsSwitch.setChecked(on)
            onNeedsRecreate()
        }

        val soundChoice = view.findViewById<TextView>(R.id.soundEffectsChoice)
        soundChoice.text = SettingsChoiceDialog.item(
            activity, activity.getString(R.string.sound_effects),
            activity.getString(R.string.sound_effects_desc)
        )
        val soundSwitch = M3Switch(density, palette, config.animations)
        soundSwitch.setChecked(SoundEffects.isEnabled(activity))
        soundChoice.setCompoundDrawablesRelativeWithIntrinsicBounds(
            SettingsIcons.icon(activity, palette, R.drawable.ic_volume),
            null, soundSwitch, null
        )
        soundChoice.setOnClickListener {
            val on = !SoundEffects.isEnabled(activity)
            SoundEffects.setEnabled(activity, on)
            soundSwitch.setChecked(on)
            activity.window.decorView.isSoundEffectsEnabled = on
        }
    }

    companion object {
        private val COLUMN_OPTIONS = intArrayOf(4, 5, 6, 7)
    }
}
