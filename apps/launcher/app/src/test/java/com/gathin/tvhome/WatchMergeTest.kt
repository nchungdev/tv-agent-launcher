package com.gathin.tvhome

import com.gathin.tvhome.feature.watching.data.WatchItem
import com.gathin.tvhome.feature.watching.data.WatchMerge
import com.gathin.tvhome.feature.watching.data.WatchSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchMergeTest {

    private val now = 1_700_000_000_000L

    @Test
    fun progressNormalization() {
        assertEquals(0f, WatchMerge.progress(Float.NaN), 0.0001f)
        assertEquals(0f, WatchMerge.progress(Float.POSITIVE_INFINITY), 0.0001f)
        assertEquals(0f, WatchMerge.progress(Float.NEGATIVE_INFINITY), 0.0001f)
        assertEquals(0f, WatchMerge.progress(-0.5f), 0.0001f)
        assertEquals(1f, WatchMerge.progress(1.5f), 0.0001f)
        assertEquals(0.45f, WatchMerge.progress(0.45f), 0.0001f)
    }

    @Test
    fun timestampConversionAndValidation() {
        // Timestamp in seconds
        val sec = 1_700_000_000L
        assertEquals(sec * 1000, WatchMerge.timestamp(sec, now))

        // Timestamp in future
        assertEquals(0L, WatchMerge.timestamp(now + 10_000, now))

        // Negative timestamp
        assertEquals(0L, WatchMerge.timestamp(-100L, now))
    }

    @Test
    fun filtersCompletedItems() {
        val completed = WatchItem(
            title = "Movie A", subtitle = "", progress = 0.96f, posterUri = null,
            appLabel = "App", packageName = "com.test.app", actionType = "open",
            parameters = emptyMap(), source = WatchSource.TV, updatedAt = now - 1000
        )
        val inProgress = completed.copy(progress = 0.5f)

        val result = WatchMerge.merge(listOf(completed, inProgress), now)
        assertEquals(1, result.size)
        assertEquals(0.5f, result[0].progress, 0.0001f)
    }

    @Test
    fun combinesDuplicateEntriesAcrossSources() {
        val tvItem = WatchItem(
            title = "Movie X", subtitle = "Part 1", progress = 0.4f, posterUri = "poster_tv",
            appLabel = "Plex", packageName = "com.plexapp.android", actionType = "open",
            parameters = emptyMap(), source = WatchSource.TV, contentId = "tv_row_123",
            deepLink = "plex://play/123", updatedAt = now - 5000
        )
        val serverItem = WatchItem(
            title = "Movie X", subtitle = "Part 1", progress = 0.6f, posterUri = "poster_server",
            appLabel = "Plex", packageName = "com.plexapp.android", actionType = "open",
            parameters = emptyMap(), source = WatchSource.SERVER, contentId = "server_item_456",
            deepLink = "plex://play/123", updatedAt = now - 1000 // Newer
        )

        val merged = WatchMerge.merge(listOf(tvItem, serverItem), now)
        assertEquals(1, merged.size)
        val item = merged[0]
        assertEquals(0.6f, item.progress, 0.0001f)
        assertEquals(serverItem.updatedAt, item.updatedAt)
        // Deep link from source preserved
        assertEquals("plex://play/123", item.deepLink)
        // Both posters available in candidates
        assertTrue(item.posterCandidates().contains("poster_server"))
        assertTrue(item.posterCandidates().contains("poster_tv"))
    }

    @Test
    fun keepsDistinctEpisodesApart() {
        val ep1 = WatchItem(
            title = "Breaking Bad", subtitle = "S01E01", progress = 0.5f, posterUri = null,
            appLabel = "Netflix", packageName = "com.netflix.ninja", actionType = "open",
            parameters = emptyMap(), source = WatchSource.TV, contentId = "ep_1",
            deepLink = "nflx://1", updatedAt = now - 2000
        )
        val ep2 = WatchItem(
            title = "Breaking Bad", subtitle = "S01E02", progress = 0.2f, posterUri = null,
            appLabel = "Netflix", packageName = "com.netflix.ninja", actionType = "open",
            parameters = emptyMap(), source = WatchSource.TV, contentId = "ep_2",
            deepLink = "nflx://2", updatedAt = now - 1000
        )

        val merged = WatchMerge.merge(listOf(ep1, ep2), now)
        assertEquals(2, merged.size)
        assertEquals("ep_2", merged[0].contentId)
        assertEquals("ep_1", merged[1].contentId)
    }

    @Test
    fun filtersHiddenItemsAndHiddenAppsBeforeLimit() {
        val hiddenApp = "com.hidden.app"
        val normalApp = "com.normal.app"

        val items = (1..12).map { i ->
            val pkg = if (i % 2 == 0) hiddenApp else normalApp
            WatchItem(
                title = "Item $i", subtitle = "", progress = 0.3f, posterUri = null,
                appLabel = "App", packageName = pkg, actionType = "open",
                parameters = emptyMap(), source = WatchSource.TV, contentId = "id_$i",
                updatedAt = now - i * 1000L
            )
        }

        val hiddenKeys = setOf("$normalApp|TV:id_1")
        val hiddenApps = setOf(hiddenApp)

        val merged = WatchMerge.merge(items, now, hidden = hiddenKeys, hiddenApps = hiddenApps)
        // All items with hiddenApp filtered out, id_1 filtered out.
        // Remaining normalApp items: id_3, id_5, id_7, id_9, id_11 (5 items)
        assertEquals(5, merged.size)
        assertTrue(merged.none { it.packageName == hiddenApp })
        assertTrue(merged.none { it.contentId == "id_1" })
    }

    @Test
    fun localItemDurationAndRetentionRules() {
        val oldLocal = WatchItem(
            title = "Old Video", subtitle = "", progress = 0.5f, posterUri = null,
            appLabel = "Player", packageName = "com.video.player", actionType = "open",
            parameters = emptyMap(), source = WatchSource.LOCAL,
            updatedAt = now - WatchMerge.LOCAL_KEEP_MS - 1000L
        )
        val lowProgressLocal = WatchItem(
            title = "Barely Started", subtitle = "", progress = 0.01f, posterUri = null,
            appLabel = "Player", packageName = "com.video.player", actionType = "open",
            parameters = emptyMap(), source = WatchSource.LOCAL, durationMs = 600_000L,
            updatedAt = now - 10_000L
        )
        val validLocal = WatchItem(
            title = "Active Video", subtitle = "", progress = 0.5f, posterUri = null,
            appLabel = "Player", packageName = "com.video.player", actionType = "open",
            parameters = emptyMap(), source = WatchSource.LOCAL, durationMs = 600_000L,
            updatedAt = now - 10_000L
        )
        val noDurationLocal = WatchItem(
            title = "Live Stream", subtitle = "", progress = 0f, posterUri = null,
            appLabel = "Player", packageName = "com.video.player", actionType = "open",
            parameters = emptyMap(), source = WatchSource.LOCAL, durationMs = 0L,
            updatedAt = now - 10_000L
        )

        val merged = WatchMerge.merge(listOf(oldLocal, lowProgressLocal, validLocal, noDurationLocal), now)
        assertEquals(2, merged.size)
        val titles = merged.map { it.title }.toSet()
        assertTrue(titles.contains("Active Video"))
        assertTrue(titles.contains("Live Stream"))
        assertFalse(titles.contains("Old Video"))
        assertFalse(titles.contains("Barely Started"))
    }
}
