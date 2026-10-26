package com.gathin.tvhome.feature.main.presenter

import com.gathin.tvhome.feature.runningapps.data.AppRepository
import com.gathin.tvhome.feature.notification.data.NotificationRepository
import com.gathin.tvhome.feature.main.adapter.Entry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch


class MainPresenter(
    private val scope: CoroutineScope,
    private val appRepository: AppRepository,
    private val notificationRepository: NotificationRepository
) {
    private val _state = MutableStateFlow(MainState())
    val state: StateFlow<MainState> = _state.asStateFlow()

    private val _effects = Channel<MainEffect>(Channel.BUFFERED)
    val effects: Flow<MainEffect> = _effects.receiveAsFlow()

    fun processIntent(intent: MainIntent) {
        when (intent) {
            is MainIntent.Init -> handleInit()
            is MainIntent.RefreshApps -> handleRefreshApps()
            is MainIntent.TileFocused -> handleTileFocused(intent.entry, intent.position)
            is MainIntent.TileClicked -> handleTileClicked(intent.entry)
            is MainIntent.StartReorder -> handleStartReorder(intent.entry)
            is MainIntent.EndReorder -> handleEndReorder()
            is MainIntent.OpenSettings -> emitEffect(MainEffect.ShowSettings)
            is MainIntent.OpenInputs -> emitEffect(MainEffect.ShowInputs)
            is MainIntent.OpenNotifications -> emitEffect(MainEffect.ShowNotifications)
            is MainIntent.OpenRunningApps -> emitEffect(MainEffect.ShowRunningApps)
            is MainIntent.OpenSmartHome -> emitEffect(MainEffect.ShowSmartHome)
            is MainIntent.OpenAppLibrary -> emitEffect(MainEffect.ShowAppLibrary)
            is MainIntent.UpdatePalette -> {
                _state.value = _state.value.copy(palette = intent.palette)
            }
            is MainIntent.UpdateGreeting -> {
                _state.value = _state.value.copy(greetingText = intent.text)
            }
            is MainIntent.UpdateNotificationCount -> {
                _state.value = _state.value.copy(unreadNotificationCount = intent.count)
            }
            is MainIntent.UpdateNetworkStatus -> {
                _state.value = _state.value.copy(
                    isConnected = intent.isConnected,
                    isWifi = intent.isWifi,
                    isVpn = intent.isVpn
                )
            }
        }
    }

    private fun handleInit() {
        scope.launch {
            val unread = notificationRepository.unreadCount()
            _state.value = _state.value.copy(unreadNotificationCount = unread)
        }
    }

    private fun handleRefreshApps() {
        // AppCoordinator will populate the resolved Entry items into the state
    }

    fun updateEntries(entries: List<Entry>) {
        _state.value = _state.value.copy(entries = entries)
    }

    private fun handleTileFocused(entry: Entry?, position: Int) {
        _state.value = _state.value.copy(
            focusedEntry = entry,
            focusedPosition = position
        )
    }

    private fun handleTileClicked(entry: Entry) {
        emitEffect(MainEffect.LaunchEntry(entry))
    }

    private fun handleStartReorder(entry: Entry) {
        _state.value = _state.value.copy(isReordering = true, focusedEntry = entry)
    }

    private fun handleEndReorder() {
        _state.value = _state.value.copy(isReordering = false)
    }

    private fun emitEffect(effect: MainEffect) {
        scope.launch {
            _effects.send(effect)
        }
    }
}
