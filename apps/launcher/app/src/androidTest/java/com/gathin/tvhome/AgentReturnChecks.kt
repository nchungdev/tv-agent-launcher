package com.gathin.tvhome

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.gathin.tvhome.core.view.AgentCardData
import com.gathin.tvhome.feature.agent.data.SearchTab
import com.gathin.tvhome.feature.agent.ui.AgentOverlay
import com.gathin.tvhome.feature.agent.ui.AgentPanel

class AgentReturnChecks(private val runner: AgentRenderInstrumentation) {
    fun run(): String {
        runner.targetContext.startActivity(Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setClassName(runner.targetContext, HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await("Home did not resume") { runner.activeHome?.hasWindowFocus() == true }
        val activity = runner.activeHome!!
        main { check(AgentOverlay.openIfHomeVisible()) }
        runner.waitForIdleSync()
        var original: AgentPanel? = null
        main {
            original = children(activity.window.decorView).filterIsInstance<AgentPanel>().single()
            original!!.onSearchResults(listOf(SearchTab("plex", SearchTab.State.READY,
                listOf(AgentCardData("available", TITLE, "movie", "2026", "")))))
            original!!.onFinished(null, "Retained answer")
        }
        runner.waitForIdleSync()
        var focused: View? = null
        main { focused = original!!.findFocus() }
        for (intent in listOf(Intent(Settings.ACTION_SETTINGS), Intent().setClassName(
            runner.targetContext, "com.gathin.tvhome.feature.system.ui.ScreensaverPreviewActivity"))) {
            runner.targetContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            await("Other activity did not cover Home") { !activity.hasWindowFocus() }
            runner.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            await("Home did not return") { activity.hasWindowFocus() }
            runner.waitForIdleSync()
            main {
                val retained = children(activity.window.decorView).filterIsInstance<AgentPanel>().single()
                check(retained === original) { "Search panel was replaced on return" }
                check(children(retained).filterIsInstance<TextView>().any { it.text == TITLE }) {
                    "Search result disappeared"
                }
                check(retained.findFocus() === focused) { "Result focus was not restored" }
            }
        }
        runner.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        runner.waitForIdleSync()
        main {
            check(children(activity.window.decorView).none { it is AgentPanel }) {
                "Explicit Back did not release panel"
            }
        }
        return "Settings and internal activity: same search panel/results/focus restored; Back closes panel"
    }

    private fun children(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            addAll(children(view.getChildAt(index)))
        }
    }

    private fun main(action: () -> Unit) {
        var failure: Throwable? = null
        runner.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw IllegalStateException("Agent return regression", it) }
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            main { ready = condition() }
            if (ready) return
            SystemClock.sleep(30)
        }
        error(message)
    }

    private companion object {
        const val HOME = "com.gathin.tvhome.feature.main.ui.MainActivity"
        const val TITLE = "Agent return regression fixture"
    }
}
