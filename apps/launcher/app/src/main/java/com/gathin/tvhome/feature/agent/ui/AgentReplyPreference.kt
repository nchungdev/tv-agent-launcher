package com.gathin.tvhome.feature.agent.ui

import android.content.Context

class AgentReplyPreference(context: Context) {

    private val preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    init {
        if (!preferences.contains(MUTED_DEFAULT)) {
            preferences.edit()
                .putBoolean(SPEAK, false)
                .putBoolean(MUTED_DEFAULT, true)
                .apply()
        }
    }

    var speakReplies: Boolean
        get() = preferences.getBoolean(SPEAK, false)
        set(value) = preferences.edit().putBoolean(SPEAK, value).apply()

    companion object {
        private const val NAME = "agent"
        private const val SPEAK = "speak"
        private const val MUTED_DEFAULT = "speak_default_muted"
    }
}
