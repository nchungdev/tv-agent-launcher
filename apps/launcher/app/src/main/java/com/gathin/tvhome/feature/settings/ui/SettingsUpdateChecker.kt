package com.gathin.tvhome.feature.settings.ui
import com.gathin.tvhome.feature.settings.data.*
import com.gathin.tvhome.feature.settings.widget.*

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.TextView
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.system.data.LauncherRelease
import com.gathin.tvhome.feature.system.data.ReleaseInstaller
import java.io.IOException
import java.util.concurrent.Executor


class SettingsUpdateChecker(
    private val activity: Activity,
    private val worker: Executor,
    private val updateChoice: TextView
) {
    private var updateBusy = false
    private var offeredRelease: LauncherRelease? = null

    fun init() {
        updateChoice.setOnClickListener { onUpdateChosen() }
        checkForUpdate()
    }

    private fun versionName(): String {
        return try {
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    fun checkForUpdate() {
        val installed = versionName()
        updateBusy = true
        showUpdateState(activity.getString(R.string.update_checking, installed))
        worker.execute {
            try {
                val latest = LauncherRelease.latest()
                activity.runOnUiThread { showRelease(latest, installed) }
            } catch (_: IOException) {
                activity.runOnUiThread { showUpdateFailure(R.string.update_check_failed) }
            }
        }
    }

    private fun showRelease(latest: LauncherRelease, installed: String) {
        updateBusy = false
        offeredRelease = if (latest.isNewerThan(installed)) latest else null
        val rel = offeredRelease
        if (rel == null) {
            showUpdateState(activity.getString(R.string.update_current, installed))
            return
        }
        showUpdateState(
            activity.getString(
                R.string.update_available, latest.version, latest.apkSize / 1024
            )
        )
    }

    private fun onUpdateChosen() {
        if (updateBusy) return
        val rel = offeredRelease
        if (rel == null) {
            checkForUpdate()
            return
        }
        if (!activity.packageManager.canRequestPackageInstalls()) {
            askToAllowInstalls()
            return
        }
        downloadAndInstall(rel)
    }

    private fun downloadAndInstall(release: LauncherRelease) {
        updateBusy = true
        showUpdateState(activity.getString(R.string.update_downloading, 0))
        val appContext = activity.applicationContext
        worker.execute {
            try {
                ReleaseInstaller.install(appContext, release) { percent ->
                    activity.runOnUiThread {
                        showUpdateState(activity.getString(R.string.update_downloading, percent))
                    }
                }
                activity.runOnUiThread {
                    updateBusy = false
                    showUpdateState(activity.getString(R.string.update_installing))
                }
            } catch (_: IOException) {
                activity.runOnUiThread { showUpdateFailure(R.string.update_download_failed) }
            }
        }
    }

    private fun askToAllowInstalls() {
        val allow = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.fromParts("package", activity.packageName, null)
        )
        try {
            activity.startActivity(allow)
        } catch (_: Exception) {
            showUpdateState(activity.getString(R.string.update_needs_permission))
        }
    }

    private fun showUpdateFailure(message: Int) {
        updateBusy = false
        showUpdateState(activity.getString(message))
    }

    private fun showUpdateState(status: String) {
        updateChoice.text = SettingsChoiceDialog.item(
            activity, activity.getString(R.string.update_title), status
        )
    }
}
