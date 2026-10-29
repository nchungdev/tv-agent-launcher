package com.gathin.tvhome

import android.app.Activity
import android.app.Dialog
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.gathin.tvhome.core.view.AgentCardData
import com.gathin.tvhome.core.view.CardAction
import com.gathin.tvhome.feature.agent.coordinator.MediaDetailSheet
import com.gathin.tvhome.feature.agent.coordinator.MediaEpisodeRow
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MediaMovieChecks(private val runner: AgentRenderInstrumentation, private val host: Activity) {
    fun run() {
        try {
            for (versions in listOf(false, true)) {
                val id = if (versions) "fixture-versions" else "fixture-movie"
                main {
                    MediaDetailSheet.show(host, AgentCardData("information", "Quiet Orbit", "movie",
                        "2026", "", "http://10.0.2.2:8765/media/stream/fixture/" +
                            "poster-movie.jpg", id,
                        listOf(CardAction("detail", "", CardAction.KIND_CLIENT,
                            "catalog_detail",
                            mapOf("title_id" to id))), "phimapi", false,
                        "An astronaut returns to a station that has fallen silent."))
                }
                lateinit var root: View
                val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
                var ready = false
                while (android.os.SystemClock.elapsedRealtime() < deadline && !ready) {
                    main {
                        root = dialog().window!!.decorView
                        ready = descendants(root).filterIsInstance<TextView>()
                            .any { it.text == host.getString(R.string.media_play) }
                    }
                    if (!ready) Thread.sleep(50)
                }
                check(ready)
                main {
                    val views = descendants(root)
                    check(views.filterIsInstance<MediaEpisodeRow>().size ==
                        if (versions) 2 else 0)
                    check(views.filterIsInstance<TextView>().none {
                        it.text == "Season 1" ||
                            it.text == host.getString(R.string.playback_episodes)
                    })
                    if (versions) check(views.filterIsInstance<TextView>().any {
                        it.text == host.getString(R.string.media_versions)
                    })
                }
                val frames = CountDownLatch(1)
                main { root.postOnAnimation { root.postOnAnimation { frames.countDown() } } }
                check(frames.await(2, TimeUnit.SECONDS))
                val name = if (versions) "movie-versions.png" else "movie.png"
                val directory = File(host.getExternalFilesDir(null), "player-qa").apply { mkdirs() }
                runner.uiAutomation.takeScreenshot().let { bitmap ->
                    File(directory, name).outputStream().use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
            }
        } finally {
            main { MediaDetailSheet.close(host) }
        }
    }

    private fun dialog(): Dialog {
        val field = MediaDetailSheet.javaClass.getDeclaredField("open")
            .apply { isAccessible = true }
        return (field.get(MediaDetailSheet) as Map<*, *>)[host] as Dialog
    }

    private fun main(action: () -> Unit) {
        var failure: Throwable? = null
        runner.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap {
            descendants(view.getChildAt(it))
        }
        else emptyList()
}
