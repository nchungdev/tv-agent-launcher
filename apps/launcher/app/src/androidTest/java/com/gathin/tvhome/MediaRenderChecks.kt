package com.gathin.tvhome

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.VideoView
import com.gathin.tvhome.core.view.AgentCardData
import com.gathin.tvhome.feature.agent.coordinator.CatalogEpisodeSheet
import com.gathin.tvhome.feature.agent.ui.AgentRequestRunner
import com.gathin.tvhome.feature.agent.widget.AgentAnswerView
import com.gathin.tvhome.feature.agent.widget.SearchResultsView
import com.gathin.tvhome.feature.playback.*
import com.gathin.tvhome.feature.settings.data.AgentHomeConfig
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MediaRenderChecks(private val runner: AgentRenderInstrumentation) {
    private val context = runner.targetContext
    private lateinit var activity: Activity
    private var answerDialog: Dialog? = null
    private var requestRunner: AgentRequestRunner? = null

    fun run(): String {
        val config = AgentHomeConfig(context)
        val originalUrl = config.agentUrl
        val originalToken = config.agentToken
        val prefs = context.getSharedPreferences("launcher_playback", 0)
        val original = prefs.all.toMap()
        val selection = context.getSharedPreferences("search_selection", 0)
        val originalSelection = selection.all.toMap()
        try {
            context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setClassName(context, "com.gathin.tvhome.feature.main.ui.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            await("Home") { runner.activeHome?.let { activity = it; true } == true }
            main {
                prefs.edit().clear().commit()
                selection.edit().clear().commit()
                config.agentUrl = "http://10.0.2.2:8765"
                config.agentToken = "fixture-only"
                PlaybackStore(context).prefer("phimapi", "launcher")
                PlaybackStore(context).prefer("jellyfin", "app")
            }
            search()
            report("search tabs, source sections, metadata and focus passed")
            MediaMovieChecks(runner, activity).run()
            report("Movie single-version and multi-version detail differ from Series")
            flowFailures()
            catalog()
            report("500 episodes, pagination and identity passed")
            cancellation()
            playback("episode-1", "mp4")
            playback("episode-2", "hls")
            return "search/detail, 500 episodes, MP4/HLS, seek, resume and Home release passed"
        } finally {
            main {
                requestRunner?.destroy()
                answerDialog?.dismiss()
                player()?.close()
                config.agentUrl = originalUrl
                config.agentToken = originalToken
                MediaTestPreferences.restore(prefs, original)
                MediaTestPreferences.restore(selection, originalSelection)
            }
        }
    }

    private fun search() {
        val done = CountDownLatch(1)
        var failure: String? = null
        var cards = emptyList<AgentCardData>()
        lateinit var answer: AgentAnswerView
        main {
            answer = AgentAnswerView(activity)
            answerDialog = Dialog(activity, android.R.style.Theme_Material_NoActionBar_Fullscreen)
                .apply { setContentView(answer); show(); window?.setLayout(-1, -1) }
            requestRunner = AgentRequestRunner(activity, object : AgentRequestRunner.Callbacks {
                override fun onPartialAnswer(soFar: String) { answer.setText(soFar) }
                override fun onCards(items: List<AgentCardData>) {
                    cards = items
                    answer.setCards(items)
                }
                override fun onFinished(error: String?, text: String) {
                    failure = error
                    done.countDown()
                }
            })
            requestRunner!!.send("fixture media search")
        }
        check(done.await(12, TimeUnit.SECONDS)) { "Search did not complete" }
        check(failure == null) { "Search failed: $failure" }
        main {
            check(cards.size == 2 && cards.map { it.sourceId }.toSet().size == 2)
            check(cards.first().year == "2026")
            val view = field(answer, "searchResults") as SearchResultsView
            val tabs = field(view, "tabs") as ViewGroup
            check(tabs.childCount == 4)
            val row = descendants(view).first { it.tag == "phimapi|film" }
            row.requestFocus()
            answer.setCards(cards + cards.last().copy(id = "another", title = "Another"))
            check(row.hasFocus()) { "Source update stole focus" }
            tabs.getChildAt(1).performClick()
            check(row.visibility == View.GONE)
            tabs.getChildAt(0).performClick()
        }
        screenshot("search.png")
        main {
            val view = field(answer, "searchResults") as SearchResultsView
            descendants(view).first { it.tag == "phimapi|film" }.apply {
                requestFocus()
                performClick()
            }
        }
        await("Media detail") {
            val map = field(com.gathin.tvhome.feature.agent.coordinator.MediaDetailSheet,
                "open") as Map<*, *>
            val detail = map[activity] as? Dialog
            detail?.window?.decorView?.let { root ->
                descendants(root).filterIsInstance<TextView>().any { it.text.contains("Episode 1") }
            } == true
        }
        screenshot("detail.png")
        main {
            com.gathin.tvhome.feature.agent.coordinator.MediaDetailSheet.close(activity)
            val view = field(answer, "searchResults") as SearchResultsView
            check(descendants(view).first { it.tag == "phimapi|film" }.hasFocus())
            answerDialog?.dismiss()
        }
    }

    private fun flowFailures() {
        var completed = false
        var failure = ""
        com.gathin.tvhome.feature.agent.data.FlowClient.execute(context,
            com.gathin.tvhome.feature.agent.data.FlowInput("truncated"),
            object : com.gathin.tvhome.feature.agent.data.FlowClient.FlowListener {
                override fun onCompleted(status: String) { completed = true }
                override fun onError(code: String, message: String) { failure = code }
            })
        check(!completed && failure == "network_error") { "Truncated flow reported success" }
        report("truncated flow terminates with an error")
    }

    private fun catalog() {
        main { CatalogEpisodeSheet.show(activity, "fixture-title", "phimapi") }
        var dialog: Dialog? = null
        await("Catalog") {
            val map = field(com.gathin.tvhome.feature.agent.coordinator.MediaDetailSheet,
                "open") as Map<*, *>
            dialog = map[activity] as? Dialog
            dialog?.window?.decorView?.let { root ->
                descendants(root).filterIsInstance<TextView>().any { it.text.contains("Episode 1") }
            } == true
        }
        MediaEpisodeGridChecks(runner).run(dialog!!.window!!.decorView)
        main {
            val root = dialog!!.window!!.decorView
            descendants(root).filterIsInstance<TextView>().first { it.text == "Season 2" }
                .performClick()
            check(descendants(root).filterIsInstance<TextView>()
                .any { it.text.contains("Episode 26") })
            check(descendants(root).filterIsInstance<TextView>()
                .none { it.text.contains("Episode 1") })
            descendants(root).filterIsInstance<TextView>()
                .first { it.text == activity.getString(R.string.media_all_episodes) }.performClick()
            repeat(19) {
                descendants(dialog!!.window!!.decorView)
                    .filterIsInstance<TextView>()
                    .first { it.text == "›" }.performClick()
            }
            val text = descendants(dialog!!.window!!.decorView)
                    .filterIsInstance<TextView>()
            check(text.any { it.text.contains("Episode 500") })
            check(text.count { it.text.contains("Episode ") } == 25)
        }
        screenshot("episodes.png")
        main { dialog!!.dismiss() }
    }

    private fun cancellation() {
        main { PlaybackRouter.open(activity, PlaybackIdentity("phimapi", "slow", "Slow")) }
        await("Pending resolve") {
            (field(PlaybackRouter, "pending") as Map<*, *>).containsKey(activity)
        }
        main {
            PlaybackRouter.open(activity, PlaybackIdentity("phimapi", "episode-1", "Current"))
        }
        await("Replacement playback") {
            val active = player() ?: return@await false
            (field(active, "identity") as PlaybackIdentity).episodeId == "episode-1" &&
                (field(active, "video") as VideoView).isPlaying
        }
        Thread.sleep(3200)
        main {
            check((field(player()!!, "identity") as PlaybackIdentity).episodeId == "episode-1")
            player()!!.close()
        }
        report("late cancelled resolve cannot open the old episode")
    }

    private fun playback(episode: String, suffix: String) {
        val identity = PlaybackIdentity("phimapi", episode, "Fixture $suffix")
        main { PlaybackRouter.open(activity, identity) }
        await("$suffix playback", 20_000) {
            val player = player() ?: return@await false
            val video = field(player, "video") as VideoView
            video.isPlaying && video.currentPosition > 500
        }
        main {
            val player = player()!!
            val video = field(player, "video") as VideoView
            val controls = field(player, "controls") as PlaybackControls
            controls.buttons.getChildAt(2).performClick()
            check(video.duration > 30_000)
        }
        await("$suffix seek") { (field(player()!!, "video") as VideoView).currentPosition > 9000 }
        await("$suffix advancing after seek", 15_000) {
            (field(player()!!, "video") as VideoView).currentPosition > 11_000
        }
        screenshot("player-$suffix.png")
        main { player()!!.close() }
        check(PlaybackStore(context).position(identity) > 9000) { "Progress was not saved" }
        check(PlaybackStore(context).items().any { it.parameters["episode_id"] == episode })
        main {
            val item = PlaybackStore(context).items().first {
                it.parameters["episode_id"] == episode
            }
            check(com.gathin.tvhome.feature.watching.ui.WatchLauncher.open(activity, item))
        }
        await("$suffix resume") {
            val active = player() ?: return@await false
            val video = field(active, "video") as VideoView
            video.isPlaying && video.currentPosition > 9000
        }
        runner.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_HOME)
        await("$suffix Home release") { player()?.let { field(it, "closed") == true } != false }
        report("$suffix decode, seek, history, fresh resolve, resume, Home release passed")
    }

    private fun player(): LauncherPlayer? =
        (field(PlaybackRouter, "player") as WeakReference<*>).get() as? LauncherPlayer

    private fun await(label: String, timeout: Long = 10_000, condition: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeout
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            var passed = false
            main { passed = condition() }
            if (passed) return
            Thread.sleep(50)
        }
        error("Timed out: $label")
    }

    private fun main(action: () -> Unit) {
        var failure: Throwable? = null
        runner.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }

    private fun field(target: Any, name: String): Any? = target.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(target)

    private fun descendants(root: View): List<View> = listOf(root) +
        if (root is ViewGroup) (0 until root.childCount).flatMap {
            descendants(root.getChildAt(it))
        }
        else emptyList()

    private fun screenshot(name: String) {
        val frames = CountDownLatch(1)
        main {
            activity.window.decorView.postOnAnimation {
                activity.window.decorView.postOnAnimation { frames.countDown() }
            }
        }
        check(frames.await(2, TimeUnit.SECONDS))
        val directory = File(context.getExternalFilesDir(null), "player-qa").apply { mkdirs() }
        runner.uiAutomation.takeScreenshot().let { bitmap ->
            File(directory, name).outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }

    private fun report(message: String) {
        runner.sendStatus(0, Bundle().apply { putString("step", message) })
    }


}
