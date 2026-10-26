package com.gathin.tvhome.feature.notification.data

import android.content.Context
import com.gathin.tvhome.feature.notification.data.TvNotification


interface NotificationRepository {
    fun init(context: Context)
    fun add(notification: TvNotification)
    fun getAll(): List<TvNotification>
    fun unreadCount(): Int
    fun hasUnreadNotifications(): Boolean
    fun latestUnreadActionable(): TvNotification?
    fun markRead(id: String)
    fun markAllAsRead()
    fun clearAll()
    fun remove(id: String)
    fun addListener(listener: (Int) -> Unit)
    fun removeListener(listener: (Int) -> Unit)
}
