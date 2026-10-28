package com.gathin.tvhome.feature.account.coordinator

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.OnAccountsUpdateListener
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import com.gathin.tvhome.R
import com.gathin.tvhome.core.util.activityScope
import com.gathin.tvhome.feature.account.data.GoogleAccountSelection
import com.gathin.tvhome.feature.account.ui.GoogleAccountSheet
import com.gathin.tvhome.feature.settings.data.Config
import com.gathin.tvhome.feature.system.data.TvSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GoogleAccountCoordinator(
    private val activity: Activity,
    private val button: TextView,
    config: Config
) {
    private val manager = AccountManager.get(activity)
    private val preferences = activity.getSharedPreferences("google_account", Activity.MODE_PRIVATE)
    private val sheet = GoogleAccountSheet(activity, config, ::chooseAccount, ::manageAccounts)
    private val listener = OnAccountsUpdateListener { refresh() }
    private var registered = false
    private var active = false
    private var refreshJob: Job? = null
    private var selectedName: String? = null

    init {
        button.setOnClickListener { sheet.show(selectedName) }
    }

    fun start() {
        active = true
        if (!registered) {
            try {
                manager.addOnAccountsUpdatedListener(
                    listener, Handler(Looper.getMainLooper()), false, arrayOf(GOOGLE_TYPE)
                )
                registered = true
            } catch (_: SecurityException) {
                registered = false
            }
        }
        refresh()
    }

    fun stop() {
        active = false
        if (registered) {
            manager.removeOnAccountsUpdatedListener(listener)
            registered = false
        }
        refreshJob?.cancel()
        sheet.close()
    }

    private fun refresh() {
        if (!active) return

        refreshJob?.cancel()
        refreshJob = activity.activityScope.launch {
            val visibleNames = withContext(Dispatchers.IO) {
                try {
                    manager.getAccountsByType(GOOGLE_TYPE).map { it.name }
                } catch (_: SecurityException) {
                    emptyList()
                }
            }
            selectedName = GoogleAccountSelection.select(
                visibleNames, preferences.getString(SELECTED_NAME, null)
            )
            button.text = selectedName ?: ""
            button.contentDescription = selectedName?.let {
                activity.getString(R.string.google_account_selected, it)
            } ?: activity.getString(R.string.google_account)
            sheet.update(selectedName)
        }
    }

    private fun chooseAccount() {
        val selectedAccount = selectedName?.let { Account(it, GOOGLE_TYPE) }
        val intent = AccountManager.newChooseAccountIntent(
            selectedAccount, null, arrayOf(GOOGLE_TYPE),
            activity.getString(R.string.google_account_choose), null, null, null
        )
        try {
            activity.startActivityForResult(intent, REQUEST_ACCOUNT)
        } catch (_: ActivityNotFoundException) {
            manageAccounts()
        } catch (_: SecurityException) {
            manageAccounts()
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_ACCOUNT || resultCode != Activity.RESULT_OK) return
        if (data?.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE) != GOOGLE_TYPE) return
        val name = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (name.isNullOrBlank()) return

        preferences.edit().putString(SELECTED_NAME, name).apply()
        refresh()
    }

    private fun manageAccounts() {
        val targets = listOf(
            Intent(Settings.ACTION_SYNC_SETTINGS)
                .putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf(GOOGLE_TYPE)),
            TvSettings.main(activity)
        )
        for (target in targets) {
            try {
                activity.startActivity(target)
                return
            } catch (_: ActivityNotFoundException) {
                continue
            } catch (_: SecurityException) {
                continue
            }
        }

        Toast.makeText(activity, R.string.not_available, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val GOOGLE_TYPE = "com.google"
        private const val SELECTED_NAME = "selected_name"
        private const val REQUEST_ACCOUNT = 7301
    }
}
