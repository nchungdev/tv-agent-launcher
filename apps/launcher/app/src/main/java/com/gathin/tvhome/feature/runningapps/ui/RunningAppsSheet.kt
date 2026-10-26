package com.gathin.tvhome.feature.runningapps.ui
import com.gathin.tvhome.feature.runningapps.data.*
import com.gathin.tvhome.feature.runningapps.presenter.*

import com.gathin.tvhome.feature.runningapps.presenter.RunningAppsPresenter
import com.gathin.tvhome.feature.runningapps.presenter.RunningAppsIntent
import com.gathin.tvhome.feature.runningapps.presenter.RunningAppsState
import com.gathin.tvhome.feature.runningapps.presenter.RunningAppsEffect
import com.gathin.tvhome.feature.runningapps.data.AppRepositoryImpl
import com.gathin.tvhome.core.view.SideSheet
import android.app.Activity
import android.app.Dialog
import android.graphics.drawable.Drawable
import android.text.format.Formatter
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.settings.data.Config
import com.gathin.tvhome.feature.runningapps.data.AppLocalDataSource
import com.gathin.tvhome.feature.runningapps.data.RunningApp
import com.gathin.tvhome.core.design.M3Skin
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.core.motion.FocusScrollView
import com.gathin.tvhome.feature.main.data.LauncherMonitor
import com.gathin.tvhome.feature.system.data.RemoteKeys
import com.gathin.tvhome.core.util.dialogScope
import kotlinx.coroutines.launch
import java.util.Locale



class RunningAppsSheet(
    private val activity: Activity,
    private val config: Config,
    private val onClosed: (() -> Unit)? = null
) {
    private val dialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
    private val presenter by lazy {
        RunningAppsPresenter(
            AppRepositoryImpl(AppLocalDataSource(activity)),
            dialog.dialogScope
        )
    }

    private lateinit var scrim: View
    private lateinit var panel: LinearLayout
    private lateinit var rows: LinearLayout
    private lateinit var footer: LinearLayout
    private lateinit var summary: TextView
    private lateinit var hint: TextView
    private lateinit var sideSheet: SideSheet
    private var lastFocusIndex = 0

    fun show() {
        Palette.loadDarkMode(activity)
        val view = LayoutInflater.from(activity).inflate(R.layout.activity_running_apps, null)
        dialog.setContentView(view)

        scrim = view.findViewById(R.id.scrim)
        panel = view.findViewById(R.id.panel)
        rows = view.findViewById(R.id.rows)
        footer = view.findViewById(R.id.footer)
        summary = view.findViewById(R.id.summary)
        hint = view.findViewById(R.id.hint)

        sideSheet = SideSheet(activity, scrim, panel, config) {
            dialog.dismiss()
            onClosed?.invoke()
        }

        view.findViewById<FocusScrollView>(R.id.rowsScroll)
            .setMotion(config.animations, config.reducedMotion)
        scrim.setOnClickListener { close() }
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                close()
                true
            } else false
        }

        reskin()
        dialog.show()
        sideSheet.slideIn()

        dialog.dialogScope.launch {
            presenter.state.collect { state -> render(state) }
        }
        dialog.dialogScope.launch {
            presenter.effects.collect { effect ->
                when (effect) {
                    is RunningAppsEffect.ShowToast -> Toast.makeText(
                        activity, effect.messageResId, Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        presenter.processIntent(RunningAppsIntent.Load)
    }

    fun isShowing(): Boolean = dialog.isShowing

    fun close() {
        sideSheet.close()
    }

    private fun render(state: RunningAppsState) {
        if (sideSheet.closing()) return
        when {
            state.isLoading && rows.childCount == 0 -> {
                showMessage(
                    activity.getString(R.string.running_connecting),
                    activity.getString(R.string.running_allow),
                    showRetry = false
                )
            }
            state.errorMessage != null -> {
                rows.removeAllViews()
                val fallback = activity.getString(R.string.running_failed)
                showMessage(state.errorMessage.ifBlank { fallback }, null, showRetry = true)
            }
            else -> {
                displayApps(state)
            }
        }
    }

    private fun showMessage(title: String, subtitle: String?, showRetry: Boolean) {
        if (sideSheet.closing()) return
        summary.text = title
        hint.visibility = if (subtitle != null) View.VISIBLE else View.GONE
        if (subtitle != null) hint.text = subtitle
        footer.removeAllViews()
        if (showRetry) {
            addRow(
                footer, activity.getDrawable(R.drawable.ic_refresh),
                activity.getString(R.string.try_again), null, null,
                { presenter.processIntent(RunningAppsIntent.Load) }, null
            )
        }
        reskin()
        if (showRetry && footer.childCount > 0) footer.getChildAt(0).requestFocus()
    }

    private fun displayApps(state: RunningAppsState) {
        if (sideSheet.closing()) return
        rows.removeAllViews()
        footer.removeAllViews()
        val apps = state.apps
        if (apps.isEmpty()) {
            showMessage(activity.getString(R.string.running_none), null, false)
            return
        }

        for (app in apps) {
            val memoryText = Formatter.formatShortFileSize(activity, app.memoryKb * 1024)
            val cpuText = formatCpu(app.cpuPercent)
            val usageText = activity.getString(R.string.running_app_usage, memoryText, cpuText)
            val statusText = if (app.isLauncher) {
                activity.getString(R.string.running_launcher_status, memoryText, cpuText)
            } else if (app.recency == 0) {
                activity.getString(R.string.running_on_screen, usageText)
            } else usageText

            addRow(rows, app.icon, app.label, statusText, app.pkg, {
                if (app.isLauncher) close() else {
                    RemoteKeys.launch(activity, app.pkg)
                    close()
                }
            }, { clickedView ->
                lastFocusIndex = rows.indexOfChild(clickedView)
                if (app.isLauncher) {
                    LauncherMonitor.trim(activity)
                    presenter.processIntent(RunningAppsIntent.TrimLauncher)
                } else {
                    presenter.processIntent(RunningAppsIntent.ForceStop(app.pkg))
                }
                true
            })
        }

        summary.text = activity.getString(
            R.string.running_summary,
            activity.resources.getQuantityString(R.plurals.running_count, apps.size, apps.size),
            Formatter.formatShortFileSize(activity, state.totalMemoryKb * 1024),
            formatCpu(state.totalCpuPercent)
        )
        hint.setText(R.string.running_hint)
        hint.visibility = View.VISIBLE

        val stoppable = apps.filter { !it.isLauncher }.map { it.pkg }
        if (stoppable.isNotEmpty()) {
            addRow(
                footer, activity.getDrawable(R.drawable.ic_clear_all),
                activity.getString(R.string.running_stop_all), null, null,
                { presenter.processIntent(RunningAppsIntent.ForceStopAll(stoppable)) }, null
            )
        }
        reskin()
        val idx = lastFocusIndex.coerceIn(0, (rows.childCount - 1).coerceAtLeast(0))
        rows.getChildAt(idx)?.requestFocus()
    }

    private fun addRow(
        parent: LinearLayout,
        iconDrawable: Drawable?,
        titleText: CharSequence,
        statusText: String?,
        packageName: String?,
        clickHandler: View.OnClickListener,
        longClickHandler: View.OnLongClickListener?
    ) {
        val row = LayoutInflater.from(activity).inflate(R.layout.item_input, parent, false)
        val imageView = row.findViewById<ImageView>(R.id.icon)
        imageView.setImageDrawable(iconDrawable)
        if (packageName != null) {
            val params = imageView.layoutParams
            val iconSize = (28 * activity.resources.displayMetrics.density).toInt()
            params.width = iconSize
            params.height = iconSize
            imageView.setTag(R.id.running_app_icon, true)
        }
        row.findViewById<TextView>(R.id.title).text = titleText
        row.findViewById<View>(R.id.dot).visibility = View.GONE
        val statusView = row.findViewById<TextView>(R.id.status)
        if (statusText != null) {
            statusView.text = statusText
        } else {
            statusView.visibility = View.GONE
        }
        row.setOnClickListener(clickHandler)
        if (longClickHandler != null) row.setOnLongClickListener(longClickHandler)
        row.setTag(R.id.running_app, packageName)
        parent.addView(row)
    }

    private fun reskin() {
        M3Skin.apply(panel, Palette.currentOrBaseline(), config.animations, config.reducedMotion)
        untint(panel)
    }

    private fun untint(targetView: View) {
        val isIcon = java.lang.Boolean.TRUE == targetView.getTag(R.id.running_app_icon)
        if (targetView is ImageView && isIcon) {
            targetView.imageTintList = null
        }
        if (targetView is ViewGroup) {
            for (index in 0 until targetView.childCount) untint(targetView.getChildAt(index))
        }
    }

    private fun formatCpu(percent: Float): String =
        if (percent < 0.05f) "0%" else String.format(Locale.US, "%.1f%%", percent)
}
