package com.gathin.tvhome

import android.content.ComponentName
import android.content.Context
import com.gathin.tvhome.feature.runningapps.data.RunningApp
import com.gathin.tvhome.feature.notification.data.TvNotification
import com.gathin.tvhome.feature.runningapps.data.AppRepository
import com.gathin.tvhome.feature.notification.data.NotificationRepository
import com.gathin.tvhome.feature.main.adapter.Entry
import com.gathin.tvhome.feature.main.presenter.MainEffect
import com.gathin.tvhome.feature.main.presenter.MainIntent
import com.gathin.tvhome.feature.main.presenter.MainPresenter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainPresenterTest {

    private class FakeAppRepository : AppRepository {
        override suspend fun getRunningApps(): Result<List<RunningApp>> =
            Result.success(emptyList())
        override suspend fun forceStop(pkg: String): Result<Unit> =
            Result.success(Unit)
        override suspend fun forceStopAll(packages: List<String>): Result<Unit> =
            Result.success(Unit)
    }

    private class FakeNotificationRepository(
        private val count: Int = 3
    ) : NotificationRepository {
        override fun init(context: Context) {}
        override fun add(notification: TvNotification) {}
        override fun getAll(): List<TvNotification> = emptyList()
        override fun unreadCount(): Int = count
        override fun hasUnreadNotifications(): Boolean = count > 0
        override fun latestUnreadActionable(): TvNotification? = null
        override fun markRead(id: String) {}
        override fun markAllAsRead() {}
        override fun clearAll() {}
        override fun remove(id: String) {}
        override fun addListener(listener: (Int) -> Unit) {}
        override fun removeListener(listener: (Int) -> Unit) {}
    }

    @Test
    fun testInitLoadsUnreadNotificationCount() = runBlocking {
        val appRepo = FakeAppRepository()
        val notifRepo = FakeNotificationRepository(count = 5)
        val presenter = MainPresenter(this, appRepo, notifRepo)

        presenter.processIntent(MainIntent.Init)
        val state = presenter.state.first { it.unreadNotificationCount == 5 }

        assertEquals(5, state.unreadNotificationCount)
    }

    @Test
    fun testTileFocusedUpdatesState() = runBlocking {
        val appRepo = FakeAppRepository()
        val notifRepo = FakeNotificationRepository()
        val presenter = MainPresenter(this, appRepo, notifRepo)

        val entry = Entry(
            label = "Plex",
            component = ComponentName("com.plexapp.android", ".MainActivity"),
            banner = null,
            icon = null
        )
        presenter.processIntent(MainIntent.TileFocused(entry, 2))

        val state = presenter.state.value
        assertEquals("Plex", state.focusedEntry?.label)
        assertEquals(2, state.focusedPosition)
    }

    @Test
    fun testTileClickedEmitsLaunchEntryEffect() = runBlocking {
        val appRepo = FakeAppRepository()
        val notifRepo = FakeNotificationRepository()
        val presenter = MainPresenter(this, appRepo, notifRepo)

        val entry = Entry(
            label = "YouTube",
            component = ComponentName("com.google.android.youtube.tv", ".MainActivity"),
            banner = null,
            icon = null
        )
        presenter.processIntent(MainIntent.TileClicked(entry, 1))

        val effect = presenter.effects.first()
        assertTrue(effect is MainEffect.LaunchEntry)
        assertEquals("YouTube", (effect as MainEffect.LaunchEntry).entry.label)
    }

    @Test
    fun testStartAndEndReorder() = runBlocking {
        val appRepo = FakeAppRepository()
        val notifRepo = FakeNotificationRepository()
        val presenter = MainPresenter(this, appRepo, notifRepo)

        val entry = Entry(
            label = "Kodi",
            component = ComponentName("org.xbmc.kodi", ".Splash"),
            banner = null,
            icon = null
        )
        presenter.processIntent(MainIntent.StartReorder(entry))
        assertTrue(presenter.state.value.isReordering)

        presenter.processIntent(MainIntent.EndReorder)
        assertTrue(!presenter.state.value.isReordering)
    }
}
