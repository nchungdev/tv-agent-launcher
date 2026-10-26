package com.gathin.tvhome.feature.main.coordinator

import android.content.Context
import android.widget.TextView
import com.gathin.tvhome.R
import com.gathin.tvhome.core.util.Shamsi
import java.util.Calendar


class HomeHeaderController(
    private val context: Context,
    private val greeting: TextView,
    private val shamsiDate: TextView
) {

    fun updateGreeting(name: String, festivalGreeting: String?) {
        if (!festivalGreeting.isNullOrEmpty()) {
            greeting.text = festivalGreeting
            return
        }
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val textRes = when {
            hour in 5..11 -> R.string.good_morning
            hour in 12..17 -> R.string.good_afternoon
            else -> R.string.good_evening
        }
        val base = context.getString(textRes)
        greeting.text = if (name.isNotEmpty()) "$base, $name" else base
    }

    fun updateShamsi(enabled: Boolean) {
        if (enabled) {
            shamsiDate.visibility = android.view.View.VISIBLE
            shamsiDate.text = Shamsi.today()
        } else {
            shamsiDate.visibility = android.view.View.GONE
        }
    }
}
