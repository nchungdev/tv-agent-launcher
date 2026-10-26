package com.gathin.tvhome

import com.gathin.tvhome.feature.notification.data.NotificationRepositoryImpl
import com.gathin.tvhome.feature.notification.data.NotificationStore
import com.gathin.tvhome.feature.notification.data.TvNotification
import com.gathin.tvhome.feature.notification.presenter.NotificationPresenter
import com.gathin.tvhome.core.design.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotificationRepositoryTest {

    @Before
    fun setUp() {
        NotificationRepositoryImpl.getInstance().clearAll()
    }

    @Test
    fun addMarksUnreadAndCapsAtMaxCapacity() {
        for (i in 1..60) {
            NotificationRepositoryImpl.getInstance().add(
                TvNotification(
                    id = "n-$i",
                    source = "radarr",
                    title = "Title $i",
                    message = "Message $i"
                )
            )
        }
        val all = NotificationRepositoryImpl.getInstance().getAll()
        assertEquals(NotificationRepositoryImpl.MAX_CAPACITY, all.size)
        assertEquals("n-60", all.first().id)
        assertEquals("n-11", all.last().id)
        assertTrue(NotificationRepositoryImpl.getInstance().hasUnreadNotifications())
        assertEquals(50, NotificationRepositoryImpl.getInstance().unreadCount())
    }

    @Test
    fun markAllAsReadClearsUnreadStateAndNotifiesListeners() {
        var latestUnreadCount = -1
        val listener: (Int) -> Unit = { count -> latestUnreadCount = count }
        NotificationRepositoryImpl.getInstance().addListener(listener)

        NotificationRepositoryImpl.getInstance().add(
            TvNotification(
                id = "plex-1",
                source = "plex",
                title = "Dune: Part Two",
                message = "Phim Dune: Part Two đã tải xong!",
                actionType = "plex.open",
                actionParams = mapOf("deep_link" to "plex://movie/dune2")
            )
        )
        assertTrue(NotificationRepositoryImpl.getInstance().hasUnreadNotifications())
        assertEquals(1, latestUnreadCount)
        assertNotNull(NotificationRepositoryImpl.getInstance().latestUnreadActionable())

        NotificationRepositoryImpl.getInstance().markAllAsRead()
        assertFalse(NotificationRepositoryImpl.getInstance().hasUnreadNotifications())
        assertEquals(0, latestUnreadCount)
        assertNull(NotificationRepositoryImpl.getInstance().latestUnreadActionable())
        assertTrue(NotificationRepositoryImpl.getInstance().getAll().first().isRead)

        NotificationRepositoryImpl.getInstance().removeListener(listener)
    }

    @Test
    fun serializeAndDeserializePreservesFieldsAndAction() {
        val original = listOf(
            TvNotification(
                id = "id-1",
                source = "plex",
                title = "Dune 2",
                message = "Ready to watch",
                severity = "info",
                actionType = "plex.open",
                actionParams = mapOf("deep_link" to "plex://123"),
                timestampEpochMs = 1700000000000L,
                isRead = false
            ),
            TvNotification(
                id = "id-2",
                source = "ha",
                title = "Door",
                message = "Front door opened",
                severity = "warning",
                actionType = null,
                actionParams = null,
                timestampEpochMs = 1700000005000L,
                isRead = true
            )
        )
        val rawJson = NotificationRepositoryImpl.getInstance().serializeList(original)
        val restored = NotificationRepositoryImpl.getInstance().deserializeList(rawJson)

        assertEquals(2, restored.size)
        assertEquals("id-1", restored[0].id)
        assertEquals("plex.open", restored[0].actionType)
        assertEquals("plex://123", restored[0].actionParams?.get("deep_link"))
        assertFalse(restored[0].isRead)
        assertTrue(restored[0].hasAction())

        assertEquals("id-2", restored[1].id)
        assertNull(restored[1].actionType)
        assertTrue(restored[1].isRead)
        assertFalse(restored[1].hasAction())
    }

    @Test
    fun markReadUpdatesSingleActionableNotification() {
        NotificationRepositoryImpl.getInstance().add(
            TvNotification(
                id = "act-1",
                source = "plex",
                title = "Movie 1",
                message = "Ready",
                actionType = "plex.open",
                actionParams = mapOf("uri" to "plex://1")
            )
        )
        NotificationRepositoryImpl.getInstance().add(
            TvNotification(
                id = "act-2",
                source = "plex",
                title = "Movie 2",
                message = "Ready 2",
                actionType = "plex.open",
                actionParams = mapOf("uri" to "plex://2")
            )
        )
        assertEquals("act-2", NotificationRepositoryImpl.getInstance().latestUnreadActionable()?.id)
        NotificationRepositoryImpl.getInstance().markRead("act-2")
        assertEquals("act-1", NotificationRepositoryImpl.getInstance().latestUnreadActionable()?.id)
    }

    @Test
    fun storePersistenceAndMediaFormatWorkCorrectly() {
        val dbRows = ArrayList<TvNotification>()
        val fakeStore = object : NotificationStore {
            override fun loadAll(limit: Int): List<TvNotification> = dbRows.take(limit)
            override fun upsert(item: TvNotification, maxCapacity: Int) {
                dbRows.removeAll { it.id == item.id }
                dbRows.add(0, item)
            }
            override fun markRead(id: String) {
                dbRows.find { it.id == id }?.isRead = true
            }
            override fun markAllRead() {
                dbRows.forEach { it.isRead = true }
            }
            override fun delete(id: String) {
                dbRows.removeAll { it.id == id }
            }
            override fun clearAll() {
                dbRows.clear()
            }
        }
        NotificationRepositoryImpl.getInstance().initWithStore(fakeStore)
        val notif = TvNotification(
            id = "dune-2",
            source = "nas",
            title = "Dune: Part Two (4K HDR)",
            message = "Phim đã tải xong! Bấm OK để mở Plex ngay.",
            actionType = "deeplink",
            actionParams = mapOf(
                "package_name" to "com.plexapp.android",
                "action_label" to "Mở Plex"
            ),
            imageUrl = "https://example.com/poster.jpg"
        )
        NotificationRepositoryImpl.getInstance().add(notif)
        assertEquals(1, dbRows.size)
        val presented = NotificationPresenter.present(notif, Palette.currentOrBaseline())
        assertEquals("NAS", presented.badgeText)
        assertEquals("Dune: Part Two (4K HDR)", presented.headline)
        assertEquals("Phim đã tải xong! Bấm OK để mở Plex ngay.", presented.body)
        assertEquals("Mở Plex", presented.actionLabel)
        assertTrue(presented.hasMediaThumb)
    }
}
