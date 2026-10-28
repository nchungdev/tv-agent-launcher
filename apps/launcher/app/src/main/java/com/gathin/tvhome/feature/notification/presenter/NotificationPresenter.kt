package com.gathin.tvhome.feature.notification.presenter
import com.gathin.tvhome.feature.notification.data.*

import com.gathin.tvhome.R
import com.gathin.tvhome.feature.notification.data.NotificationRepositoryImpl
import com.gathin.tvhome.feature.notification.data.TvNotification
import com.gathin.tvhome.feature.notification.data.NotificationRepository
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.core.util.EffectBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale


sealed interface NotificationIntent {
    object Load : NotificationIntent
    object ClearAll : NotificationIntent
    data class ClickItem(val item: TvNotification) : NotificationIntent
}

data class NotificationState(
    val items: List<TvNotification> = emptyList()
)

sealed interface NotificationEffect {
    data class ExecuteAction(val item: TvNotification) : NotificationEffect
    object Close : NotificationEffect
}

data class NotificationPresentation(
    val badgeText: String,
    val badgeColor: Int,
    val headline: String,
    val body: String,
    val actionLabel: String,
    val iconRes: Int,
    val hasMediaThumb: Boolean
)

class NotificationPresenter(
    private val scope: CoroutineScope,
    private val repository: NotificationRepository = NotificationRepositoryImpl.getInstance()
) {
    private val _state = MutableStateFlow(NotificationState())
    val state: StateFlow<NotificationState> = _state.asStateFlow()

    val effects = EffectBus<NotificationEffect>()

    fun processIntent(intent: NotificationIntent) {
        when (intent) {
            is NotificationIntent.Load -> loadNotifications()
            is NotificationIntent.ClearAll -> clearAllNotifications()
            is NotificationIntent.ClickItem -> handleItemClick(intent.item)
        }
    }

    private fun loadNotifications() {
        scope.launch {
            val list = repository.getAll()
            repository.markAllAsRead()
            _state.value = NotificationState(items = list)
        }
    }

    private fun clearAllNotifications() {
        scope.launch {
            repository.clearAll()
            _state.value = NotificationState(items = emptyList())
        }
    }

    private fun handleItemClick(item: TvNotification) {
        val actionable = item.hasAction() || !item.imageUrl.isNullOrBlank()
        if (actionable) {
            effects.emit(NotificationEffect.ExecuteAction(item))
            effects.emit(NotificationEffect.Close)
        }
    }

    companion object {
        fun present(notification: TvNotification, palette: Palette): NotificationPresentation {
            val sourceClean = notification.source.trim().uppercase(Locale.ROOT)
            val badge = if (sourceClean.isEmpty()) "THÔNG BÁO" else sourceClean
            val headline = notification.title.trim().ifEmpty { "Thông báo mới" }
            val body = notification.message.trim()
            val severity = notification.severity.lowercase(Locale.ROOT)

            val badgeColor = when (severity) {
                "error", "danger" -> 0xFFF43F5E.toInt()
                "warning", "alert" -> 0xFFF59E0B.toInt()
                "success" -> 0xFF10B981.toInt()
                else -> if (palette.dark) 0xFF38BDF8.toInt() else palette.primary
            }

            val explicitAction = notification.actionParams?.get("action_label")
                ?.toString()?.trim()
            val actionLabel = when {
                !explicitAction.isNullOrEmpty() -> explicitAction
                notification.hasAction() -> "▶ Bấm để mở"
                else -> ""
            }

            val sourceLower = sourceClean.lowercase(Locale.ROOT)
            val iconRes = when {
                sourceLower.contains("camera") || sourceLower.contains("tapo") ->
                    R.drawable.ic_videocam
                sourceLower.contains("home") || sourceLower.contains("ha") ->
                    R.drawable.ic_home_assistant
                sourceLower.contains("nas") || sourceLower.contains("media") ||
                    sourceLower.contains("plex") || sourceLower.contains("radarr") ||
                    sourceLower.contains("sonarr") -> R.drawable.ic_av
                sourceLower.contains("mail") || sourceLower.contains("chat") ->
                    R.drawable.ic_mail
                else -> R.drawable.ic_notifications_outline
            }

            return NotificationPresentation(
                badgeText = badge,
                badgeColor = badgeColor,
                headline = headline,
                body = body,
                actionLabel = actionLabel,
                iconRes = iconRes,
                hasMediaThumb = !notification.imageUrl.isNullOrBlank()
            )
        }
    }
}
