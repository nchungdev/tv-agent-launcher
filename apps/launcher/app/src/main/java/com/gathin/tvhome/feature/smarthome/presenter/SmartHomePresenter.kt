package com.gathin.tvhome.feature.smarthome.presenter
import com.gathin.tvhome.feature.smarthome.data.*

import com.gathin.tvhome.core.util.EffectBus
import com.gathin.tvhome.feature.smarthome.data.SmartHomeAction
import com.gathin.tvhome.feature.smarthome.data.SmartHomeDevice
import com.gathin.tvhome.feature.smarthome.data.SmartHomeRepository
import com.gathin.tvhome.feature.settings.data.AgentHomeConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject


sealed interface SmartHomeIntent {
    object Load : SmartHomeIntent
    data class ExecuteAction(
        val device: SmartHomeDevice,
        val action: SmartHomeAction,
        val values: JSONObject
    ) : SmartHomeIntent
    object ToggleEditMode : SmartHomeIntent
    data class ToggleDeviceHidden(val entityId: String) : SmartHomeIntent
}

data class SmartHomeState(
    val isLoading: Boolean = false,
    val devices: List<SmartHomeDevice> = emptyList(),
    val hiddenEntities: Set<String> = emptySet(),
    val pendingHiddenEntities: Set<String> = emptySet(),
    val isEditMode: Boolean = false,
    val errorMessage: String? = null
)

sealed interface SmartHomeEffect {
    data class ShowToast(val message: String) : SmartHomeEffect
}

class SmartHomePresenter(
    private val repository: SmartHomeRepository,
    private val settings: AgentHomeConfig,
    private val scope: CoroutineScope
) {
    private val initialHidden = settings.hiddenEntities
    private val _state = MutableStateFlow(
        SmartHomeState(
            isLoading = true,
            hiddenEntities = initialHidden,
            pendingHiddenEntities = initialHidden
        )
    )
    val state: StateFlow<SmartHomeState> = _state.asStateFlow()

    val effects = EffectBus<SmartHomeEffect>()

    fun processIntent(intent: SmartHomeIntent) {
        when (intent) {
            is SmartHomeIntent.Load -> loadDevices()
            is SmartHomeIntent.ExecuteAction -> executeAction(
                intent.device, intent.action, intent.values
            )
            is SmartHomeIntent.ToggleEditMode -> toggleEditMode()
            is SmartHomeIntent.ToggleDeviceHidden -> toggleHidden(intent.entityId)
        }
    }

    private fun loadDevices() {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            val result = repository.getDevices()
            result.onSuccess { devices ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    devices = devices,
                    errorMessage = null
                )
            }.onFailure { err ->
                val msg = err.message ?: "Lỗi kết nối Smart Home"
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = msg
                )
                effects.emit(SmartHomeEffect.ShowToast(msg))
            }
        }
    }

    private fun executeAction(
        device: SmartHomeDevice,
        action: SmartHomeAction,
        values: JSONObject
    ) {
        scope.launch {
            val result = repository.executeAction(device, action, values)
            result.onFailure { err ->
                val msg = err.message ?: "Lỗi thực thi lệnh"
                effects.emit(SmartHomeEffect.ShowToast(msg))
            }
            loadDevices()
        }
    }

    private fun toggleEditMode() {
        val current = _state.value
        val newMode = !current.isEditMode
        if (newMode) {
            _state.value = current.copy(
                isEditMode = true,
                pendingHiddenEntities = current.hiddenEntities
            )
        } else {
            val savedHidden = current.pendingHiddenEntities
            settings.hiddenEntities = savedHidden
            _state.value = current.copy(
                isEditMode = false,
                hiddenEntities = savedHidden
            )
        }
    }

    private fun toggleHidden(entityId: String) {
        val current = _state.value
        val pending = current.pendingHiddenEntities.toMutableSet()
        if (pending.contains(entityId)) {
            pending.remove(entityId)
        } else {
            pending.add(entityId)
        }
        _state.value = current.copy(pendingHiddenEntities = pending)
    }
}
