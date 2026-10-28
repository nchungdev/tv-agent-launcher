package com.gathin.tvhome.feature.settings.widget

import android.app.Activity
import android.content.Context
import android.widget.TextView
import com.gathin.tvhome.R
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.feature.settings.data.Config
import com.gathin.tvhome.feature.settings.ui.SettingsChoiceDialog
import com.gathin.tvhome.feature.system.data.Weather
import com.gathin.tvhome.feature.system.data.WeatherLocation

object WeatherLocationPicker {
    private val queries = arrayOf("", "Hanoi|VN", "Ho Chi Minh City|VN", "Da Nang|VN",
        "Haiphong|VN", "Can Tho|VN", "Hue|VN", "Nha Trang|VN", "Da Lat|VN")
    private val names = arrayOf("", "Hà Nội", "TP. Hồ Chí Minh", "Đà Nẵng", "Hải Phòng",
        "Cần Thơ", "Huế", "Nha Trang", "Đà Lạt")

    fun bind(activity: Activity, config: Config, row: TextView, onChanged: () -> Unit) {
        fun update() {
            val city = Weather.selectedCity(activity)
            val index = queries.indexOf(city)
            val name = if (index > 0) names[index] else if (city.isNotEmpty()) city else
                activity.getSharedPreferences("weather", Context.MODE_PRIVATE)
                    .getString("place_name", null) ?: activity.getString(R.string.widget_city_auto)
            row.text = SettingsChoiceDialog.item(activity,
                activity.getString(R.string.weather_location), name)
        }
        row.setCompoundDrawablesRelativeWithIntrinsicBounds(
            SettingsIcons.icon(activity, Palette.currentOrBaseline(), R.drawable.ic_location),
            null, null, null
        )
        update()
        row.setOnClickListener { choose(activity, config) { update(); onChanged() } }
    }

    fun choose(activity: Activity, config: Config, onChanged: () -> Unit) {
        val labels = Array<CharSequence>(queries.size) {
            if (it == 0) activity.getString(R.string.weather_location_update) else names[it]
        }
        val captions = Array<CharSequence>(queries.size) {
            if (it == 0) activity.getString(R.string.weather_location_once) else ""
        }
        SettingsChoiceDialog.chooseOne(activity, R.string.weather_location, labels, captions,
            queries.indexOf(Weather.selectedCity(activity)), { index ->
                WeatherLocation.reset(activity)
                activity.getSharedPreferences("launcher_settings", Context.MODE_PRIVATE).edit()
                    .putString("weather_city", queries[index]).apply()
                onChanged()
            }, config)
    }
}
