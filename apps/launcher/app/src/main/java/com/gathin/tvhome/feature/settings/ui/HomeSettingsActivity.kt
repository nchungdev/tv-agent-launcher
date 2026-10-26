package com.gathin.tvhome.feature.settings.ui
import com.gathin.tvhome.feature.settings.data.*
import com.gathin.tvhome.feature.settings.widget.*

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import com.gathin.tvhome.R


class HomeSettingsActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show()
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show()
        } finally {
            finish()
        }
    }
}
