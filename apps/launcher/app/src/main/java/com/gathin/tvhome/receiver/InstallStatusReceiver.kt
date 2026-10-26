package com.gathin.tvhome.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import com.gathin.tvhome.R

class InstallStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            askToConfirm(context, intent)
            return
        }

        val declined = status == PackageInstaller.STATUS_FAILURE_ABORTED
        if (status != PackageInstaller.STATUS_SUCCESS && !declined) {
            val reason = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
            val message = context.getString(
                R.string.update_install_failed,
                if (!reason.isNullOrEmpty()) reason else status.toString()
            )
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun askToConfirm(context: Context, intent: Intent) {
        val confirm: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }
        if (confirm == null) return
        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(confirm)
    }
}
