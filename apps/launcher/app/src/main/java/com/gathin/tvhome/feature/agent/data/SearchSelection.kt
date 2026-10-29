package com.gathin.tvhome.feature.agent.data

import android.content.Context

class SearchSelection(context: Context) {
    private val prefs = context.getSharedPreferences("search_selection", Context.MODE_PRIVATE)
    fun sources(): List<String> = prefs.getStringSet("sources", emptySet())!!.sorted()
    fun select(sources: Set<String>) { prefs.edit().putStringSet("sources", sources).apply() }
    fun title(): String = prefs.getString("title", "").orEmpty()
    fun title(value: String) { prefs.edit().putString("title", value.take(500)).apply() }
}
