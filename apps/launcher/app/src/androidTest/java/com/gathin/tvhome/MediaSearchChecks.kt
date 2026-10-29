package com.gathin.tvhome

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.media3.exoplayer.ExoPlayer
import com.gathin.tvhome.feature.agent.data.FlowCards
import com.gathin.tvhome.feature.agent.data.SearchTab
import com.gathin.tvhome.core.view.AgentCardData
import com.gathin.tvhome.feature.agent.coordinator.CatalogEpisodeSheet
import com.gathin.tvhome.feature.agent.ui.AgentRequestRunner
import com.gathin.tvhome.feature.agent.widget.AgentAnswerView
import com.gathin.tvhome.feature.agent.widget.SearchFiltersView
import com.gathin.tvhome.feature.agent.widget.SearchPosterCard
import com.gathin.tvhome.feature.agent.widget.SearchResultsView
import com.gathin.tvhome.feature.playback.*
import com.gathin.tvhome.feature.settings.data.AgentHomeConfig
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MediaSearchChecks(private val runner: AgentRenderInstrumentation,
    private val activity: Activity) {
    private val context = runner.targetContext
    private var answerDialog: Dialog? = null
    private var requestRunner: AgentRequestRunner? = null

    fun run() {
        val done = CountDownLatch(1)
        var failure: String? = null
        var searchCards = emptyList<AgentCardData>()
        var searchTabs = emptyList<SearchTab>()
        lateinit var answerView: AgentAnswerView
        main {
            answerView = AgentAnswerView(activity)
            answerDialog = Dialog(activity, android.R.style.Theme_Material_NoActionBar_Fullscreen)
                .apply { setContentView(answerView); show(); window?.setLayout(-1, -1) }
            requestRunner = AgentRequestRunner(activity, object : AgentRequestRunner.Callbacks {
                override fun onPartialAnswer(soFar: String) { answerView.setText(soFar) }
                override fun onCards(cards: List<AgentCardData>) {
                    searchCards = cards
                    answerView.setCards(cards)
                }
                override fun onSearchResults(tabs: List<SearchTab>) {
                    searchTabs = tabs
                    searchCards = tabs.flatMap { it.cards }
                    answerView.setSearch(tabs)
                }
                override fun onFinished(error: String?, answer: String) {
                    failure = error
                    done.countDown()
                }
            })
            requestRunner!!.send("fixture media search")
        }
        check(done.await(12, TimeUnit.SECONDS)) { "Search did not complete" }
        check(failure == null) { "Search failed: $failure" }
        main {
            check(searchCards.size == 2 && searchCards.map { it.sourceId }.toSet().size == 2)
            check(searchCards.first { it.sourceId == "phimapi" }.year == "2026")
            val view = field(answerView, "searchResults") as SearchResultsView
            val filters = field(view, "searchFilters") as SearchFiltersView
            val filterButtons = descendants(filters).filterIsInstance<TextView>()
                .filter { it.tag?.toString()?.startsWith("search-filter:") == true }
            check(filterButtons.any { it.text.toString().startsWith("All (2)") })
            check(descendants(view).filterIsInstance<TextView>().any { it.text == "PhimAPI" })
            val row = descendants(view).first { it.tag == "phimapi|film" }
            check(row is SearchPosterCard) { "Search results should use horizontal poster cards" }
            row.requestFocus()
            answerView.setSearch(searchTabs.map { tab ->
                if (tab.source == "jellyfin") tab.copy(cards = tab.cards +
                    tab.cards.first().copy(id = "another", title = "Another")) else tab
            })
            check(row.hasFocus()) { "Source update stole focus" }
            val rowType = FlowCards.contentType(row.contentDescription.toString())
            val otherType = listOf("Movie", "Series", "Other").firstOrNull { type ->
                type != rowType && filterButtons.any { it.tag == "search-filter:$type" }
            }
            if (otherType != null) {
                filterButtons.first { it.tag == "search-filter:$otherType" }.apply {
                    requestFocus()
                    performClick()
                }
                check(descendants(view).none { it.tag == "phimapi|film" })
            }
            filterButtons.first { it.tag == "search-filter:All" }.performClick()
            check(descendants(view).any { it.tag == "phimapi|film" })
        }
        screenshot("search.png")
        main {
            val view = field(answerView, "searchResults") as SearchResultsView
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
            val view = field(answerView, "searchResults") as SearchResultsView
            check(descendants(view).first { it.tag == "phimapi|film" }.hasFocus())
            answerDialog?.dismiss()
        }
    }

    fun close() {
        requestRunner?.destroy()
        answerDialog?.dismiss()
    }

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

}
