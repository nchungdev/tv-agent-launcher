package com.gathin.tvhome

import com.gathin.tvhome.feature.playback.PlaybackUrls
import org.junit.Assert.*
import org.junit.Test

class PlaybackUrlsTest {
    @Test fun resolvesProxyAndExplicitMediaUrl() {
        assertEquals("http://nas:8788/media/stream/session/file.mp4",
            PlaybackUrls.resolve("http://nas:8788/base/", "/media/stream/session/file.mp4"))
        assertEquals("https://cdn.example/video.m3u8?signature=fixture",
            PlaybackUrls.resolve("http://nas:8788",
                "https://cdn.example/video.m3u8?signature=fixture"))
    }

    @Test fun rejectsCredentialsSchemesFragmentsAndNetworkPathReferences() {
        for (value in listOf("file:///etc/passwd", "javascript:alert(1)",
            "https://admin:secret@example.com/video", "//other.example/video.mp4",
            "https://cdn.example/video#fragment", "", "https:///video")) {
            assertTrue(value, runCatching {
                PlaybackUrls.resolve("http://nas:8788", value)
            }.isFailure)
        }
    }

    @Test fun acceptsOnlyDeclaredMediaFormats() {
        assertTrue(PlaybackUrls.supported("application/vnd.apple.mpegurl"))
        assertTrue(PlaybackUrls.supported("video/mp4"))
        assertFalse(PlaybackUrls.supported("text/html"))
        assertFalse(PlaybackUrls.supported("text/javascript"))
    }
}
