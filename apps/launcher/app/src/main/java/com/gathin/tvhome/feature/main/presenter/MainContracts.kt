package com.gathin.tvhome.feature.main.presenter

import com.gathin.tvhome.feature.main.adapter.Entry
import com.gathin.tvhome.core.design.Palette


sealed interface MainIntent {
    object Init : MainIntent
    object RefreshApps : MainIntent
    data class TileFocused(val entry: Entry?, val position: Int) : MainIntent
    data class TileClicked(val entry: Entry, val position: Int) : MainIntent
    data class StartReorder(val entry: Entry) : MainIntent
    object EndReorder : MainIntent
    object OpenSettings : MainIntent
    object OpenInputs : MainIntent
    object OpenNotifications : MainIntent
    object OpenRunningApps : MainIntent
    object OpenSmartHome : MainIntent
    object OpenAppLibrary : MainIntent
    data class UpdatePalette(val palette: Palette) : MainIntent
    data class UpdateGreeting(val text: String) : MainIntent
    data class UpdateNotificationCount(val count: Int) : MainIntent
    data class UpdateNetworkStatus(
        val isConnected: Boolean,
        val isWifi: Boolean,
        val isVpn: Boolean
    ) : MainIntent
}

data class MainState(
    val entries: List<Entry> = emptyList(),
    val focusedEntry: Entry? = null,
    val focusedPosition: Int = -1,
    val isReordering: Boolean = false,
    val palette: Palette = Palette.currentOrBaseline(),
    val greetingText: String = "",
    val unreadNotificationCount: Int = 0,
    val isConnected: Boolean = true,
    val isWifi: Boolean = true,
    val isVpn: Boolean = false
)

sealed interface MainEffect {
    data class LaunchEntry(val entry: Entry) : MainEffect
    object ShowSettings : MainEffect
    object ShowInputs : MainEffect
    object ShowNotifications : MainEffect
    object ShowRunningApps : MainEffect
    object ShowSmartHome : MainEffect
    object ShowAppLibrary : MainEffect
    data class ScrollToPosition(val position: Int) : MainEffect
}
