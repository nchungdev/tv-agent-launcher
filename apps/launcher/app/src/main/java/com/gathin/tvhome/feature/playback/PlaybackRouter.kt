package com.gathin.tvhome.feature.playback

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.agent.data.AgentClient
import com.gathin.tvhome.feature.agent.data.CatalogClient
import com.gathin.tvhome.feature.agent.coordinator.CatalogEpisodeSheet
import com.gathin.tvhome.feature.agent.coordinator.MediaDetailSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.WeakHashMap

object PlaybackRouter {
    private val pending = WeakHashMap<Activity, Dialog>()
    private var player = WeakReference<LauncherPlayer>(null)

    fun activity(context: Context): Activity? = when (context) {
        is Activity -> context
        is ContextWrapper -> activity(context.baseContext)
        else -> null
    }

    fun onHome(activity: Activity) {
        pending.remove(activity)?.dismiss()
        PlaybackPrompts.close(activity)
        player.get()?.close()
    }

    fun open(context: Context, identity: PlaybackIdentity) {
        val host = activity(context) ?: return
        if (identity.sourceId.isBlank() || identity.episodeId.isBlank()) return
        val store = PlaybackStore(host)
        store.remember(identity.sourceId)
        val preference = store.preferred(identity.sourceId)
        if (preference.isBlank()) {
            if (ExternalPlayback.hasCompatibleApp(host, identity.sourceId)) {
                PlaybackChoices.choose(host, identity.sourceId) { resolve(host, identity, it) }
            } else {
                resolve(host, identity, "launcher")
            }
        } else resolve(host, identity, preference)
    }

    fun source(context: Context, source: String, url: String) {
        val host = activity(context) ?: return
        val choose: (String) -> Unit = { preference ->
            if (preference == "launcher") {
                PlaybackPrompts.show(host, AlertDialog.Builder(host)
                    .setMessage(R.string.playback_stream_required)
                    .setPositiveButton(R.string.playback_app) { _, _ ->
                        if (!ExternalPlayback.source(host, source, url)) unavailable(host)
                    }.setNegativeButton(android.R.string.cancel, null))
            } else if (!ExternalPlayback.source(host, source, url)) unavailable(host)
        }
        val store = PlaybackStore(host)
        val preferred = store.preferred(source)
        if (preferred.isEmpty()) {
            if (ExternalPlayback.hasCompatibleApp(host, source)) {
                PlaybackChoices.choose(host, source, choose)
            } else {
                choose("launcher")
            }
        } else choose(preferred)
    }

    private fun resolve(host: Activity, identity: PlaybackIdentity, preference: String) {
        if (host.isFinishing || host.isDestroyed) return
        pending.remove(host)?.dismiss()
        player.get()?.close()
        val request = AgentClient.Request()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val loading = AlertDialog.Builder(host).setMessage(R.string.playback_loading)
            .setNegativeButton(android.R.string.cancel, null).create()
        val lifecycle = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStopped(activity: Activity) {
                if (activity === host) loading.dismiss()
            }
            override fun onActivityDestroyed(activity: Activity) {
                if (activity === host) loading.dismiss()
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
        }
        host.application.registerActivityLifecycleCallbacks(lifecycle)
        loading.setOnDismissListener {
            request.cancel()
            scope.cancel()
            if (pending[host] === loading) pending.remove(host)
            host.application.unregisterActivityLifecycleCallbacks(lifecycle)
        }
        pending[host] = loading
        loading.show()
        scope.launch {
            val response = withContext(Dispatchers.IO) {
                runCatching { CatalogClient.resolvePlayback(host, identity.episodeId, request) }
            }
            if (!loading.isShowing || pending[host] !== loading) return@launch
            val descriptor = response.getOrNull()?.playback
            if (descriptor == null) {
                loading.dismiss()
                PlaybackPrompts.show(host, AlertDialog.Builder(host)
                    .setMessage(R.string.playback_error)
                    .setPositiveButton(R.string.playback_retry) { _, _ ->
                        resolve(host, identity, preference)
                    }.setNegativeButton(android.R.string.cancel, null))
                return@launch
            }
            loading.dismiss()
            if (!PlaybackUrls.supported(descriptor.mimeType) && preference == "launcher") {
                PlaybackPrompts.show(host, AlertDialog.Builder(host)
                    .setMessage(R.string.playback_error)
                    .setPositiveButton(R.string.playback_app) { _, _ ->
                        if (!ExternalPlayback.stream(host, descriptor)) unavailable(host)
                    }.setNegativeButton(android.R.string.cancel, null))
            } else if (preference == "app") {
                if (!ExternalPlayback.stream(host, descriptor)) {
                    PlaybackPrompts.show(host, AlertDialog.Builder(host)
                    .setMessage(R.string.playback_no_app)
                        .setPositiveButton(R.string.playback_launcher) { _, _ ->
                            resolve(host, identity, "launcher")
                        }.setNegativeButton(android.R.string.cancel, null))
                }
            } else {
                val selected = identity.copy(titleId = identity.titleId.ifBlank {
                    response.getOrNull()?.episode?.titleId.orEmpty()
                })
                val episodes: (() -> Unit)? = if (identity.kind == "movie") null else {
                    {
                        val titleId = response.getOrNull()?.episode?.titleId.orEmpty()
                        val detail = MediaDetailSheet
                        if (titleId.isNotBlank() && !detail.contains(host, titleId)) {
                            CatalogEpisodeSheet.show(host, titleId,
                                identity.sourceId, identity.poster)
                        }
                    }
                }
                val playback = LauncherPlayer(host, selected, descriptor,
                    { resolve(host, identity, "launcher") }, episodes)
                player = WeakReference(playback)
                playback.show()
            }
        }
    }

    private fun unavailable(activity: Activity) {
        PlaybackPrompts.show(activity, AlertDialog.Builder(activity)
            .setMessage(R.string.playback_no_app)
            .setPositiveButton(android.R.string.ok, null))
    }
}
