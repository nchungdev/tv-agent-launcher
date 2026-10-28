package com.gathin.tvhome.feature.runningapps.presenter
import com.gathin.tvhome.feature.runningapps.data.*

import com.gathin.tvhome.core.util.EffectBus
import com.gathin.tvhome.feature.runningapps.data.RunningApp
import com.gathin.tvhome.feature.runningapps.data.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


sealed interface RunningAppsIntent {
    object Load : RunningAppsIntent
    data class ForceStop(val pkg: String) : RunningAppsIntent
    data class ForceStopAll(val packages: List<String>) : RunningAppsIntent
    object TrimLauncher : RunningAppsIntent
}

data class RunningAppsState(
    val isLoading: Boolean = false,
    val apps: List<RunningApp> = emptyList(),
    val totalMemoryKb: Long = 0L,
    val totalCpuPercent: Float = 0f,
    val errorMessage: String? = null
)

sealed interface RunningAppsEffect {
    data class ShowToast(val messageResId: Int) : RunningAppsEffect
}

class RunningAppsPresenter(
    private val appRepository: AppRepository,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(RunningAppsState(isLoading = true))
    val state: StateFlow<RunningAppsState> = _state.asStateFlow()

    val effects = EffectBus<RunningAppsEffect>()

    fun processIntent(intent: RunningAppsIntent) {
        when (intent) {
            is RunningAppsIntent.Load -> loadApps()
            is RunningAppsIntent.ForceStop -> forceStopApp(intent.pkg)
            is RunningAppsIntent.ForceStopAll -> forceStopAllApps(intent.packages)
            is RunningAppsIntent.TrimLauncher -> trimLauncher()
        }
    }

    private fun loadApps() {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            val result = appRepository.getRunningApps()
            result.onSuccess { apps ->
                val totalMem = apps.sumOf { it.memoryKb }
                val totalCpu = apps.fold(0f) { acc, a -> acc + a.cpuPercent }
                _state.value = RunningAppsState(
                    isLoading = false,
                    apps = apps,
                    totalMemoryKb = totalMem,
                    totalCpuPercent = totalCpu,
                    errorMessage = null
                )
            }.onFailure { err ->
                _state.value = RunningAppsState(
                    isLoading = false,
                    apps = emptyList(),
                    errorMessage = err.message
                )
            }
        }
    }

    private fun forceStopApp(pkg: String) {
        scope.launch {
            appRepository.forceStop(pkg)
            loadApps()
        }
    }

    private fun forceStopAllApps(packages: List<String>) {
        scope.launch {
            appRepository.forceStopAll(packages)
            loadApps()
        }
    }

    private fun trimLauncher() {
        scope.launch {
            val res = com.gathin.tvhome.R.string.running_launcher_trimmed
            effects.emit(RunningAppsEffect.ShowToast(res))
            loadApps()
        }
    }
}
