package com.gathin.tvhome

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import com.gathin.tvhome.feature.agent.coordinator.CatalogEpisodesView
import com.gathin.tvhome.feature.agent.coordinator.MediaEpisodeRow
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MediaEpisodeGridChecks(private val runner: AgentRenderInstrumentation) {
    fun run(root: View) {
        frames(root)
        lateinit var grid: CatalogEpisodesView
        lateinit var cards: List<MediaEpisodeRow>
        main {
            grid = descendants(root).filterIsInstance<CatalogEpisodesView>().single()
            cards = descendants(grid).filterIsInstance<MediaEpisodeRow>()
            check(cards.size == 25)
            val first = location(cards[0])
            val second = location(cards[1])
            val third = location(cards[2])
            val density = root.resources.displayMetrics.density
            check(cards[0].width < grid.width * 0.55f) { "Episode still fills screen width" }
            check(first[1] == second[1] && third[1] > first[1]) { "Expected two-column grid" }
            check(second[0] - first[0] - cards[0].width >= 15 * density)
            val seasons = descendants(grid).filterIsInstance<HorizontalScrollView>().single()
            val gap = first[1] - location(seasons)[1] - seasons.height
            check(gap >= 19 * density) { "Season tabs touch episode cards: $gap" }
            cards[0].requestFocus()
        }
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        main { check(cards[1].hasFocus()) { "Right should select episode 2" } }
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        main { check(cards[3].hasFocus()) { "Down should keep the same column" } }
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        main { check(cards[2].hasFocus()) { "Left should select episode 3" } }
        key(KeyEvent.KEYCODE_DPAD_UP)
        main { check(cards[0].hasFocus()) { "Up should return to episode 1" } }
        var originalWidth = 0
        main {
            originalWidth = grid.layoutParams.width
            grid.layoutParams = grid.layoutParams.apply {
                width = (600 * root.resources.displayMetrics.density).toInt()
            }
        }
        frames(root)
        main {
            val narrow = descendants(grid).filterIsInstance<MediaEpisodeRow>()
            check(location(narrow[1])[1] > location(narrow[0])[1])
            check(narrow[0].width > grid.width * 0.9f)
            check(narrow.first().hasFocus()) { "Resize lost focused episode identity" }
            grid.layoutParams = grid.layoutParams.apply { width = originalWidth }
        }
        frames(root)
        main {
            val wide = descendants(grid).filterIsInstance<MediaEpisodeRow>()
            check(location(wide[0])[1] == location(wide[1])[1])
        }
    }

    private fun key(code: Int) {
        runner.sendKeyDownUpSync(code)
        runner.waitForIdleSync()
    }

    private fun frames(root: View) {
        val ready = CountDownLatch(1)
        main { root.postOnAnimation { root.postOnAnimation { ready.countDown() } } }
        check(ready.await(2, TimeUnit.SECONDS))
    }

    private fun main(action: () -> Unit) {
        var failure: Throwable? = null
        runner.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }

    private fun location(view: View) = IntArray(2).also { view.getLocationInWindow(it) }
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap {
            descendants(view.getChildAt(it))
        }
        else emptyList()
}
