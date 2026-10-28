package com.gathin.tvhome

import com.gathin.tvhome.feature.watching.data.WatchDetails
import com.gathin.tvhome.feature.watching.ui.WatchFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchDetailsTest {

    @Test
    fun readsSeasonAndEpisodeFromText() {
        val details = WatchDetails.guess("The Expanse S2 · E5 · Home", 2017)

        assertEquals(WatchDetails(WatchDetails.SERIES, 2017, 2, 5), details)
    }

    @Test
    fun readsVietnameseEpisodeWords() {
        val details = WatchDetails.guess("2 Ngày 1 Đêm Mùa 5 | Tập 106", 0)

        assertEquals(WatchDetails(WatchDetails.SERIES, 0, 5, 106), details)
    }

    @Test
    fun yearAloneMeansMovie() {
        assertEquals(WatchDetails(WatchDetails.MOVIE, 2023), WatchDetails.guess("Dune", 2023))
    }

    @Test
    fun dropsCoAuthorSuffixFromChannel() {
        val channel = WatchFormat.channel("ĐÔNG TÂY PROMOTION OFFICIAL và 3 người khác")

        assertEquals("ĐÔNG TÂY PROMOTION OFFICIAL", channel)
    }
}
