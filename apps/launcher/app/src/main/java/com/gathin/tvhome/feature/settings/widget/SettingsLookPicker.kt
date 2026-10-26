package com.gathin.tvhome.feature.settings.widget
import com.gathin.tvhome.feature.settings.data.*
import com.gathin.tvhome.feature.settings.ui.*

import android.app.Activity
import android.content.SharedPreferences
import android.widget.TextView
import android.widget.Toast
import com.gathin.tvhome.Contract
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.settings.data.Config
import com.gathin.tvhome.core.design.M3Switch
import com.gathin.tvhome.feature.main.data.HomeLook
import com.gathin.tvhome.feature.system.ui.Screensaver


object SettingsLookPicker {

    val BACKDROP_NAMES = intArrayOf(
        R.string.look_backdrop_shapes,
        R.string.look_backdrop_sky,
        R.string.look_backdrop_plain
    )

    val SCREENSAVER_STYLE_NAMES = intArrayOf(
        R.string.screensaver_style_shapes,
        R.string.screensaver_style_glow,
        R.string.screensaver_style_minimal,
        R.string.screensaver_style_large
    )

    fun updateLook(
        activity: Activity,
        choices: SharedPreferences,
        colorChoice: TextView,
        backdropChoice: TextView
    ) {
        val seedNames = activity.resources.getStringArray(R.array.look_seed_names)
        val savedSeed = choices.getInt(Contract.Key.BACKGROUND_COLOR, 0)
        var name = activity.getString(R.string.look_color_theme)
        if (savedSeed != 0) {
            for (i in HomeLook.SEEDS.indices) {
                if (HomeLook.SEEDS[i] == savedSeed) {
                    name = seedNames[i]
                    break
                }
            }
        }
        colorChoice.text = SettingsChoiceDialog.item(
            activity, activity.getString(R.string.look_color), name
        )

        val backdrop = HomeLook.backdrop(choices)
        var index = 0
        for (i in HomeLook.BACKDROPS.indices) {
            if (HomeLook.BACKDROPS[i] == backdrop) {
                index = i
                break
            }
        }
        val backdropName = activity.getString(BACKDROP_NAMES[index])
        backdropChoice.text = SettingsChoiceDialog.item(
            activity, activity.getString(R.string.look_backdrop), backdropName
        )
    }

    fun chooseColor(
        activity: Activity,
        config: Config,
        choices: SharedPreferences,
        onChanged: () -> Unit
    ) {
        val names = activity.resources.getStringArray(R.array.look_seed_names)
        val labels = Array<CharSequence>(names.size + 1) { "" }
        labels[0] = activity.getString(R.string.look_color_theme)
        System.arraycopy(names, 0, labels, 1, names.size)
        val savedSeed = choices.getInt(Contract.Key.BACKGROUND_COLOR, 0)
        var selected = 0
        if (savedSeed != 0) {
            for (i in HomeLook.SEEDS.indices) {
                if (HomeLook.SEEDS[i] == savedSeed) {
                    selected = i + 1
                    break
                }
            }
        }
        SettingsChoiceDialog.chooseOne(
            activity, R.string.look_color, labels, null, selected, { index ->
                val seed = if (index == 0) 0 else HomeLook.SEEDS[index - 1]
                val editor = choices.edit()
                if (seed == 0) editor.remove(Contract.Key.BACKGROUND_COLOR)
                else editor.putInt(Contract.Key.BACKGROUND_COLOR, seed)
                editor.apply()
                onChanged()
            }, config
        )
    }

    fun chooseBackdrop(
        activity: Activity,
        config: Config,
        choices: SharedPreferences,
        onChanged: () -> Unit
    ) {
        val labels = Array<CharSequence>(BACKDROP_NAMES.size) {
            activity.getString(BACKDROP_NAMES[it])
        }
        val current = HomeLook.backdrop(choices)
        var selected = 0
        for (i in HomeLook.BACKDROPS.indices) {
            if (HomeLook.BACKDROPS[i] == current) {
                selected = i
                break
            }
        }
        SettingsChoiceDialog.chooseOne(
            activity, R.string.look_backdrop, labels, null, selected, { index ->
                choices.edit().putString(
                    HomeLook.BACKDROP_KEY, HomeLook.BACKDROPS[index]
                ).apply()
                onChanged()
            }, config
        )
    }

    fun updateScreensaver(
        activity: Activity,
        choices: SharedPreferences,
        screensaverSwitch: M3Switch?,
        toggleChoice: TextView,
        timeChoice: TextView,
        styleChoice: TextView
    ) {
        val enabled = Screensaver.isEnabled(activity)
        screensaverSwitch?.setChecked(enabled)
        toggleChoice.text = SettingsChoiceDialog.item(
            activity,
            activity.getString(R.string.screensaver_enable),
            activity.getString(R.string.dream_name)
        )

        val minutes = Screensaver.minutes(activity)
        val timeLabel = if (minutes == 0) activity.getString(R.string.screensaver_never)
        else activity.resources.getQuantityString(
            R.plurals.screensaver_minutes, minutes, minutes
        )
        timeChoice.text = SettingsChoiceDialog.item(
            activity, activity.getString(R.string.screensaver_after), timeLabel
        )

        val current = Screensaver.style(choices)
        var index = 0
        for (i in Screensaver.STYLES.indices) {
            if (Screensaver.STYLES[i] == current) {
                index = i
                break
            }
        }
        val styleName = activity.getString(SCREENSAVER_STYLE_NAMES[index])
        styleChoice.text = SettingsChoiceDialog.item(
            activity, activity.getString(R.string.screensaver_style), styleName
        )
    }

    fun chooseScreensaverTime(
        activity: Activity,
        config: Config,
        onChanged: () -> Unit
    ) {
        val options = Screensaver.MINUTES
        val labels = Array<CharSequence>(options.size) { "" }
        for (i in options.indices) {
            labels[i] = if (options[i] == 0) activity.getString(R.string.screensaver_never)
            else activity.resources.getQuantityString(
                R.plurals.screensaver_minutes, options[i], options[i]
            )
        }
        val minutes = Screensaver.minutes(activity)
        var selected = 0
        for (i in options.indices) {
            if (options[i] == minutes) {
                selected = i
                break
            }
        }
        SettingsChoiceDialog.chooseOne(
            activity, R.string.screensaver_after, labels, null, selected, { index ->
                Screensaver.setMinutes(activity, options[index])
                onChanged()
            }, config
        )
    }

    fun chooseScreensaverStyle(
        activity: Activity,
        config: Config,
        choices: SharedPreferences,
        onChanged: () -> Unit
    ) {
        val labels = Array<CharSequence>(SCREENSAVER_STYLE_NAMES.size) {
            activity.getString(SCREENSAVER_STYLE_NAMES[it])
        }
        val current = Screensaver.style(choices)
        var selected = 0
        for (i in Screensaver.STYLES.indices) {
            if (Screensaver.STYLES[i] == current) {
                selected = i
                break
            }
        }
        SettingsChoiceDialog.chooseOne(
            activity, R.string.screensaver_style, labels, null, selected, { index ->
                choices.edit().putString(
                    Screensaver.STYLE_KEY, Screensaver.STYLES[index]
                ).apply()
                onChanged()
            }, config
        )
    }
}
