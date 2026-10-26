package com.gathin.tvhome

import android.content.Context
import com.gathin.tvhome.feature.notification.data.TvNotification
import com.gathin.tvhome.feature.notification.data.NotificationRepository
import com.gathin.tvhome.feature.notification.presenter.NotificationEffect
import com.gathin.tvhome.feature.notification.presenter.NotificationIntent
import com.gathin.tvhome.feature.notification.presenter.NotificationPresenter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPresenterTest {

    private class FakeNotificationRepository(
        private val initialItems: List<TvNotification> = emptyList()
    ) : NotificationRepository {
        val items = ArrayList<TvNotification>(initialItems)
        var allReadCalled = false

        override fun init(context: Context) {}
        override fun add(notification: TvNotification) { items.add(0, notification) }
        override fun getAll(): List<TvNotification> = ArrayList(items)
        override fun unreadCount(): Int = items.count { !it.isRead }
        override fun hasUnreadNotifications(): Boolean = items.any { !it.isRead }
        override fun latestUnreadActionable(): TvNotification? =
            items.firstOrNull { !it.isRead && it.hasAction() }

        override fun markRead(id: String) {
            items.find { it.id == id }?.isRead = true
        }

        override fun markAllAsRead() {
            allReadCalled = true
            items.forEach { it.isRead = true }
        }

        override fun clearAll() { items.clear() }
        override fun remove(id: String) { items.removeAll { it.id == id } }
        override fun addListener(listener: (Int) -> Unit) {}
        override fun removeListener(listener: (Int) -> Unit) {}
    }

    @Test
    fun testLoadNotificationsUpdatesStateAndMarksRead() = runBlocking {
        val notif = TvNotification(
            id = "notif-1",
            source = "ha",
            title = "Phát hiện chuyển động",
            message = "Camera sân trước phát hiện chuyển động",
            isRead = false
        )
        val repository = FakeNotificationRepository(listOf(notif))
        val presenter = NotificationPresenter(this, repository)

        presenter.processIntent(NotificationIntent.Load)

        val state = presenter.state.first { it.items.isNotEmpty() }
        assertEquals(1, state.items.size)
        assertEquals("notif-1", state.items.first().id)
        assertTrue(repository.allReadCalled)
    }

    @Test
    fun testClearAllNotificationsEmptiesState() = runBlocking {
        val notif = TvNotification(
            id = "notif-2",
            source = "nas",
            title = "Tải phim hoàn tất",
            message = "Oppenheimer (2023) đã sẵn sàng",
            isRead = false
        )
        val repository = FakeNotificationRepository(listOf(notif))
        val presenter = NotificationPresenter(this, repository)

        presenter.processIntent(NotificationIntent.ClearAll)
        kotlinx.coroutines.delay(50)

        val state = presenter.state.value
        assertTrue(state.items.isEmpty())
        assertTrue(repository.items.isEmpty())
    }

    @Test
    fun testClickActionableItemEmitsEffects() = runBlocking {
        val actionable = TvNotification(
            id = "notif-3",
            source = "agent",
            title = "Cập nhật launcher",
            message = "Phiên bản mới v2.0 đã sẵn sàng",
            actionType = "deeplink",
            actionParams = mapOf("action" to "open_settings")
        )
        val repository = FakeNotificationRepository(listOf(actionable))
        val presenter = NotificationPresenter(this, repository)

        presenter.processIntent(NotificationIntent.ClickItem(actionable))

        val firstEffect = presenter.effects.first()
        assertTrue(firstEffect is NotificationEffect.ExecuteAction)
        assertEquals(actionable.id, (firstEffect as NotificationEffect.ExecuteAction).item.id)
    }
}
