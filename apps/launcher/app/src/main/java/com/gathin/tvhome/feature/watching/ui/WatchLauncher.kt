package com.gathin.tvhome.feature.watching.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.watching.data.WatchItem
import com.gathin.tvhome.feature.watching.data.WatchMerge

object WatchLauncher {
    fun open(context: Context, item: WatchItem): Boolean {
        if (!WatchMerge.validPackage(item.packageName)) return false
        val resume = resumeIntent(item.packageName, link(item))
        if (resume != null && start(context, resume)) return true
        val manager = context.packageManager
        val fallback = manager.getLeanbackLaunchIntentForPackage(item.packageName)
            ?: manager.getLaunchIntentForPackage(item.packageName)
        if (fallback != null && start(context, fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
            return true
        }
        Toast.makeText(context, R.string.watch_open_failed, Toast.LENGTH_SHORT).show()
        return false
    }

    fun resumeIntent(owner: String, link: String): Intent? {
        if (!WatchMerge.validPackage(owner) || link.isBlank() || link.length > 8192) return null
        return runCatching {
            val intent = if (link.startsWith("intent:")) Intent.parseUri(link,
                Intent.URI_INTENT_SCHEME) else Intent(Intent.ACTION_VIEW, Uri.parse(link))
            val scheme = intent.data?.scheme?.lowercase() ?: return null
            if (scheme in BLOCKED_SCHEMES) return null
            if (scheme == "content" && intent.data?.authority?.let {
                it == owner || it.startsWith("$owner.")
            } != true) return null
            val action = intent.action ?: Intent.ACTION_VIEW
            if (action != Intent.ACTION_VIEW && !action.startsWith("$owner.")) return null
            intent.selector = null
            intent.component = null
            intent.clipData = null
            intent.sourceBounds = null
            intent.categories?.toList()?.forEach { intent.removeCategory(it) }
            intent.action = action
            intent.setPackage(owner)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            intent
        }.getOrNull()
    }

    private fun link(item: WatchItem): String {
        if (item.deepLink.isNotBlank()) return item.deepLink
        for (key in listOf("deep_link", "uri", "url")) {
            item.parameters[key]?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        if (item.packageName == "com.google.android.youtube.tv" ||
            item.packageName == "com.google.android.youtube") {
            item.parameters["video_id"]?.toString()?.takeIf { it.isNotBlank() }?.let {
                return "https://www.youtube.com/watch?v=${Uri.encode(it)}"
            }
            if (item.title.isNotBlank()) {
                return "https://www.youtube.com/results?search_query=${Uri.encode(item.title)}"
            }
        }
        return ""
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }

    private val BLOCKED_SCHEMES = setOf("file", "data", "javascript", "tel", "sms",
        "mailto", "intent", "android-app", "market")
}
