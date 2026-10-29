package com.gathin.tvhome

import com.gathin.tvhome.feature.agent.coordinator.MediaDetailsPolicy
import com.gathin.tvhome.feature.agent.data.CatalogEpisode
import com.gathin.tvhome.feature.agent.data.CatalogTitle
import org.junit.Assert.*
import org.junit.Test

class MediaDetailsPolicyTest {
    private val full = CatalogEpisode(id = "full", label = "Full", group = "Original")

    @Test fun movieHasNoEpisodesSectionForOneVersion() {
        val movie = CatalogTitle(name = "Film", kind = "movie", episodes = listOf(full))
        assertFalse(MediaDetailsPolicy.isSeries(movie.kind))
        assertFalse(MediaDetailsPolicy.showEntries(movie))
        assertEquals("Film", MediaDetailsPolicy.playbackTitle(movie, full))
    }

    @Test fun movieVersionsKeepOriginalIdentityAndNeverBecomeSeasons() {
        val movie = CatalogTitle(kind = "movie", episodes = listOf(full,
            full.copy(id = "dubbed", group = "Dubbed")))
        assertTrue(MediaDetailsPolicy.showEntries(movie))
        assertFalse(MediaDetailsPolicy.isSeries(movie.kind))
        assertEquals("dubbed", movie.episodes[1].id)
    }

    @Test fun seriesKeepsEpisodeLabelsAndAnEmptyEpisodeState() {
        val series = CatalogTitle(name = "Series", kind = "series")
        assertTrue(MediaDetailsPolicy.showEntries(series))
        assertEquals("Series · Special", MediaDetailsPolicy.playbackTitle(series,
            full.copy(label = "Special")))
        assertFalse(MediaDetailsPolicy.isSeries("media_title"))
    }
}
