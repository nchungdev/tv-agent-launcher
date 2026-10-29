package com.gathin.tvhome

import com.gathin.tvhome.feature.watching.data.WatchAvailability
import com.gathin.tvhome.feature.watching.data.WatchFeed
import com.gathin.tvhome.feature.watching.data.WatchFeedState
import com.gathin.tvhome.feature.watching.data.WatchItem
import com.gathin.tvhome.feature.watching.data.WatchResult
import com.gathin.tvhome.feature.watching.data.WatchSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors

class WatchFeedTest {

    private val now = 1_700_000_000_000L

    @Test
    fun feedStatePreservesSnapshotOnError() {
        val state = WatchFeedState()
        val item1 = WatchItem(
            title = "Movie 1", subtitle = "", progress = 0.5f, posterUri = null,
            appLabel = "App", packageName = "com.pkg.one", actionType = "open",
            parameters = emptyMap(), source = WatchSource.TV, contentId = "c1",
            updatedAt = now - 1000
        )

        // Initial success
        state.update(WatchSource.TV, WatchResult.loaded(listOf(item1)))
        assertEquals(WatchAvailability.READY, state.state(WatchSource.TV))
        assertEquals(1, state.count(WatchSource.TV))

        // Subsequent temporary error retains previous snapshot
        state.update(WatchSource.TV, WatchResult(WatchAvailability.ERROR))
        assertEquals(WatchAvailability.ERROR, state.state(WatchSource.TV))
        assertEquals(1, state.count(WatchSource.TV))
        assertEquals(1, state.items(now, emptySet(), emptySet()).size)
    }

    @Test
    fun feedStateClearsSnapshotOnEmptyOrPermissionRevoked() {
        val state = WatchFeedState()
        val item = WatchItem(
            title = "Video", subtitle = "", progress = 0.5f, posterUri = null,
            appLabel = "App", packageName = "com.pkg.one", actionType = "open",
            parameters = emptyMap(), source = WatchSource.TV, contentId = "v1",
            updatedAt = now - 1000
        )

        // 1. Loaded
        state.update(WatchSource.TV, WatchResult.loaded(listOf(item)))
        assertEquals(1, state.count(WatchSource.TV))

        // 2. Returns empty -> clears snapshot
        state.update(WatchSource.TV, WatchResult.loaded(emptyList()))
        assertEquals(WatchAvailability.EMPTY, state.state(WatchSource.TV))
        assertEquals(0, state.count(WatchSource.TV))
        assertTrue(state.items(now, emptySet(), emptySet()).isEmpty())

        // 3. Loaded again
        state.update(WatchSource.TV, WatchResult.loaded(listOf(item)))
        assertEquals(1, state.count(WatchSource.TV))

        // 4. Permission revoked -> immediately removes cached snapshot
        state.update(WatchSource.TV, WatchResult(WatchAvailability.PERMISSION_REQUIRED))
        assertEquals(WatchAvailability.PERMISSION_REQUIRED, state.state(WatchSource.TV))
        assertEquals(0, state.count(WatchSource.TV))
        assertTrue(state.items(now, emptySet(), emptySet()).isEmpty())
    }

    @Test
    fun independentSourcesDoNotBlockOrHideEachOther() = runBlocking {
        val executor = Executors.newSingleThreadExecutor()
        val dispatcher = executor.asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val changes = Channel<Unit>(Channel.UNLIMITED)

        val tvItem = WatchItem(
            title = "TV Show", subtitle = "", progress = 0.4f, posterUri = null,
            appLabel = "TV App", packageName = "com.tv.app", actionType = "open",
            parameters = emptyMap(), source = WatchSource.TV, contentId = "t1",
            updatedAt = now - 2000
        )

        // Server fails with ERROR, TV succeeds with data, Local is empty
        val sources = mapOf(
            WatchSource.TV to {
                object : WatchFeed.Load {
                    override fun fetch(): WatchResult = WatchResult.loaded(listOf(tvItem))
                    override fun cancel() {}
                }
            },
            WatchSource.SERVER to {
                object : WatchFeed.Load {
                    override fun fetch(): WatchResult = WatchResult(WatchAvailability.ERROR)
                    override fun cancel() {}
                }
            },
            WatchSource.LOCAL to {
                object : WatchFeed.Load {
                    override fun fetch(): WatchResult = WatchResult(WatchAvailability.EMPTY)
                    override fun cancel() {}
                }
            }
        )

        val feed = WatchFeed(
            scope = scope,
            sources = sources,
            filters = { Pair(emptySet(), emptySet()) },
            changed = { changes.trySend(Unit) },
            now = { now }
        )

        try {
            feed.start()
            withTimeout(3000) {
                while (feed.state(WatchSource.TV) != WatchAvailability.READY ||
                    feed.state(WatchSource.SERVER) != WatchAvailability.ERROR ||
                    feed.state(WatchSource.LOCAL) != WatchAvailability.EMPTY ||
                    feed.items.none { it.title == "TV Show" }) {
                    changes.receive()
                }
            }

            // TV item is successfully published despite server error
            assertEquals(1, feed.items.size)
            assertEquals("TV Show", feed.items[0].title)
            assertEquals(WatchAvailability.READY, feed.state(WatchSource.TV))
            assertEquals(WatchAvailability.ERROR, feed.state(WatchSource.SERVER))
        } finally {
            feed.stop()
            executor.shutdown()
        }
    }
}
