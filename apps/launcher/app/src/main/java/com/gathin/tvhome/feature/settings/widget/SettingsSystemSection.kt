package com.gathin.tvhome.feature.settings.widget
import com.gathin.tvhome.feature.settings.data.*
import com.gathin.tvhome.feature.settings.ui.*

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.settings.data.Config
import com.gathin.tvhome.feature.system.data.TvSettings
import com.gathin.tvhome.core.design.Palette
import java.util.concurrent.Executor


class SettingsSystemSection(
    private val activity: Activity,
    private val config: Config,
    private val choices: SharedPreferences,
    private val palette: Palette,
    private val worker: Executor,
    private val onNeedsRecreate: () -> Unit,
    private val onClose: () -> Unit
) {
    private lateinit var nameChoice: TextView

    fun bind(view: View) {
        initName(view)
        initTvSettings(view)
        initReboot(view)
        initHomeStatus(view)
        initUpdate(view)
        initVersion(view)
    }

    private fun initName(view: View) {
        nameChoice = view.findViewById(R.id.nameChoice)
        nameChoice.setCompoundDrawablesRelativeWithIntrinsicBounds(
            SettingsIcons.icon(activity, palette, R.drawable.ic_person), null, null, null
        )
        showName()
        nameChoice.setOnClickListener {
            SettingsChoiceDialog.editName(
                activity, config, choices, NAME_KEY,
                activity.getString(R.string.greeting_name)
            ) {
                onNeedsRecreate()
                showName()
            }
        }
    }

    private fun showName() {
        val name = choices.getString(
            NAME_KEY, activity.getString(R.string.greeting_name)
        )?.trim() ?: ""
        nameChoice.text = SettingsChoiceDialog.item(
            activity, activity.getString(R.string.your_name),
            if (name.isEmpty()) activity.getString(R.string.your_name_none) else name
        )
    }

    private fun initTvSettings(view: View) {
        val tvSettings = view.findViewById<TextView>(R.id.tvSettingsChoice)
        tvSettings.text = activity.getString(R.string.tv_settings)
        tvSettings.setCompoundDrawablesRelativeWithIntrinsicBounds(
            SettingsIcons.icon(activity, palette, R.drawable.ic_settings), null,
            SettingsIcons.icon(activity, palette, R.drawable.ic_open_in_new), null
        )
        tvSettings.setOnClickListener { open(TvSettings.main(activity)) }
    }

    private fun initReboot(view: View) {
        val reboot = view.findViewById<TextView>(R.id.rebootChoice)
        reboot.text = SettingsChoiceDialog.item(
            activity, activity.getString(R.string.reboot),
            activity.getString(R.string.reboot_desc)
        )
        reboot.setCompoundDrawablesRelativeWithIntrinsicBounds(
            SettingsIcons.icon(activity, palette, R.drawable.ic_power), null, null, null
        )
        reboot.setOnClickListener {
            SettingsChoiceDialog.confirmPower(activity, config, worker, onClose)
        }
    }

    private fun initHomeStatus(view: View) {
        val pm = activity.packageManager
        val home = view.findViewById<TextView>(R.id.defaultInfo)
        val r = pm.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY
        )
        val isDefault = r?.activityInfo != null && activity.packageName == r.activityInfo.packageName
        val defaultText = if (isDefault) R.string.about_default_yes else R.string.about_default_no
        home.text = SettingsChoiceDialog.item(
            activity, activity.getString(R.string.about_default), activity.getString(defaultText)
        )
        home.setCompoundDrawablesRelativeWithIntrinsicBounds(
            SettingsIcons.icon(activity, palette, R.drawable.ic_home), null, null, null
        )
        home.setOnClickListener { open(Intent(Settings.ACTION_HOME_SETTINGS)) }

        val stock = view.findViewById<TextView>(R.id.stockInfo)
        val setting = try {
            pm.getApplicationEnabledSetting(STOCK_LAUNCHER)
        } catch (_: Exception) {
            stock.visibility = View.GONE
            return
        }
        val off = setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
        val stockText = if (off) R.string.about_stock_off else R.string.about_stock_on
        stock.text = SettingsChoiceDialog.item(
            activity, activity.getString(R.string.about_stock), activity.getString(stockText)
        )
        stock.setCompoundDrawablesRelativeWithIntrinsicBounds(
            SettingsIcons.icon(activity, palette, R.drawable.ic_tv), null, null, null
        )
        stock.setOnClickListener {
            open(Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", STOCK_LAUNCHER, null)
            ))
        }
    }

    private fun initUpdate(view: View) {
        val updateChoice = view.findViewById<TextView>(R.id.updateChoice)
        updateChoice.setCompoundDrawablesRelativeWithIntrinsicBounds(
            SettingsIcons.icon(activity, palette, R.drawable.ic_system_update), null, null, null
        )
        SettingsUpdateChecker(activity, worker, updateChoice).init()
    }

    private fun initVersion(view: View) {
        val versionRow = view.findViewById<TextView>(R.id.aboutVersion)
        versionRow.text = SettingsChoiceDialog.item(
            activity, activity.getString(R.string.about_version), versionName()
        )
        versionRow.setCompoundDrawablesRelativeWithIntrinsicBounds(
            SettingsIcons.icon(activity, palette, R.drawable.ic_info), null, null, null
        )
    }

    private fun versionName(): String {
        return try {
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun open(intent: Intent) {
        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(activity, R.string.not_available, Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(activity, R.string.not_available, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val STOCK_LAUNCHER = "com.google.android.apps.tv.launcherx"
        const val NAME_KEY = "greeting_name"
    }
}
