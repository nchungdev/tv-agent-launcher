package com.gathin.tvhome.feature.notification.data

import android.content.Context
import com.gathin.tvhome.feature.notification.data.NotificationDatabase
import com.gathin.tvhome.feature.notification.data.NotificationStore
import com.gathin.tvhome.feature.notification.data.TvNotification
import com.gathin.tvhome.feature.notification.data.NotificationRepository
import org.json.JSONArray
import java.util.concurrent.CopyOnWriteArrayList


class NotificationRepositoryImpl(
    private var initialStore: NotificationStore? = null
) : NotificationRepository {

    private val notifications = CopyOnWriteArrayList<TvNotification>()
    private val listeners = CopyOnWriteArrayList<(Int) -> Unit>()
    private var store: NotificationStore? = initialStore
    private var initialized = false

    init {
        if (initialStore != null) {
            initWithStore(initialStore!!)
        }
    }

    @Synchronized
    override fun init(context: Context) {
        if (initialized && store != null) return
        val appContext = context.applicationContext
        val database = NotificationDatabase(appContext)
        migrateLegacyPrefsIfNeeded(appContext, database)
        initWithStore(database)
    }

    @Synchronized
    internal fun initWithStore(targetStore: NotificationStore) {
        store = targetStore
        initialized = true
        notifications.clear()
        notifications.addAll(targetStore.loadAll(MAX_CAPACITY))
        notifyListeners()
    }

    private fun migrateLegacyPrefsIfNeeded(context: Context, db: NotificationStore) {
        val prefs = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(LEGACY_KEY, null) ?: return
        val legacyItems = deserializeList(raw)
        for (item in legacyItems.asReversed()) {
            db.upsert(item, MAX_CAPACITY)
        }
        prefs.edit().remove(LEGACY_KEY).apply()
    }

    override fun add(notification: TvNotification) {
        val existingIndex = notifications.indexOfFirst { it.id == notification.id }
        if (existingIndex >= 0) {
            notifications.removeAt(existingIndex)
        }
        notifications.add(0, notification)
        while (notifications.size > MAX_CAPACITY) {
            notifications.removeAt(notifications.size - 1)
        }
        try {
            store?.upsert(notification, MAX_CAPACITY)
        } catch (_: Exception) {}
        notifyListeners()
    }

    override fun getAll(): List<TvNotification> = ArrayList(notifications)

    override fun unreadCount(): Int = notifications.count { !it.isRead }

    override fun hasUnreadNotifications(): Boolean = notifications.any { !it.isRead }

    override fun latestUnreadActionable(): TvNotification? =
        notifications.firstOrNull { !it.isRead && it.hasAction() }

    override fun markRead(id: String) {
        var changed = false
        for (item in notifications) {
            if (item.id == id && !item.isRead) {
                item.isRead = true
                changed = true
            }
        }
        if (changed) {
            try {
                store?.markRead(id)
            } catch (_: Exception) {}
            notifyListeners()
        }
    }

    override fun markAllAsRead() {
        var changed = false
        for (item in notifications) {
            if (!item.isRead) {
                item.isRead = true
                changed = true
            }
        }
        if (changed) {
            try {
                store?.markAllRead()
            } catch (_: Exception) {}
            notifyListeners()
        }
    }

    override fun clearAll() {
        val hadItems = notifications.isNotEmpty()
        notifications.clear()
        try {
            store?.clearAll()
        } catch (_: Exception) {}
        if (hadItems) notifyListeners()
    }

    override fun remove(id: String) {
        val removed = notifications.removeAll { it.id == id }
        if (removed) {
            try {
                store?.delete(id)
            } catch (_: Exception) {}
            notifyListeners()
        }
    }

    override fun addListener(listener: (Int) -> Unit) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
        listener(unreadCount())
    }

    override fun removeListener(listener: (Int) -> Unit) {
        listeners.remove(listener)
    }

    fun serializeList(items: List<TvNotification>): String {
        val array = JSONArray()
        for (item in items.take(MAX_CAPACITY)) {
            array.put(item.toJson())
        }
        return array.toString()
    }

    fun deserializeList(rawJson: String): List<TvNotification> {
        return try {
            val array = JSONArray(rawJson)
            val result = ArrayList<TvNotification>(minOf(array.length(), MAX_CAPACITY))
            val limit = minOf(array.length(), MAX_CAPACITY)
            for (i in 0 until limit) {
                val itemObj = array.optJSONObject(i) ?: continue
                result.add(TvNotification.fromJson(itemObj))
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun notifyListeners() {
        val count = unreadCount()
        for (listener in listeners) {
            listener(count)
        }
    }

    companion object {
        const val MAX_CAPACITY = 50
        private const val LEGACY_PREFS = "tv_notifications_store"
        private const val LEGACY_KEY = "items_json"

        @Volatile
        private var instance: NotificationRepositoryImpl? = null

        fun getInstance(): NotificationRepositoryImpl {
            return instance ?: synchronized(this) {
                instance ?: NotificationRepositoryImpl().also { instance = it }
            }
        }
    }
}
