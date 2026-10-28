package com.gathin.tvhome.feature.watching.data

import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.media.tv.TvContract
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.LruCache
import com.gathin.tvhome.feature.main.data.LibraryApps
import com.gathin.tvhome.feature.settings.data.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContinueWatching(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val main = Handler(Looper.getMainLooper())
    private val hidden = WatchHiddenStore(app)
    private val tv = TvWatchNextSource(app)
    private val posterLoader = WatchPosterLoader(app)
    private val posters = object : LruCache<String, Bitmap>(16 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = maxOf(1, value.byteCount / 1024)
    }
    private var active = false
    private var changed: (() -> Unit)? = null
    private val listeners = LinkedHashSet<() -> Unit>()
    private var posterJob: Job? = null
    private var posterRequest: WatchRequest? = null
    private var postersPending = false
    private var generation = 0
    private var refreshedAt = 0L
    private var observing = false
    private val debounce = Runnable { refresh() }
    private val observer = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) {
            main.removeCallbacks(debounce)
            main.postDelayed(debounce, 500)
        }
    }
    private val feed: WatchFeed = WatchFeed(scope, sources(), {
        Pair(hidden.load(), (Config.load(app).hiddenApps + LibraryApps.loadRemoved(app)).toSet())
    }, {
        items = feedItems()
        observe()
        notifyChanged()
        loadPosters()
    })

    var items: List<WatchItem> = emptyList()
        private set

    private fun feedItems(): List<WatchItem> = feed.items
    fun isRunning(): Boolean = active
    fun poster(uri: String?): Bitmap? {
        if (uri.isNullOrBlank()) return null
        val cached = posters.get(uri)
        if (cached != null) return cached
        if (active && posterJob == null) loadPosters()
        return null
    }
    fun state(source: WatchSource) = feed.state(source)
    fun count(source: WatchSource) = feed.count(source)
    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }

    fun start(onChanged: () -> Unit) {
        changed = onChanged
        if (active) return
        active = true
        generation++
        observe()
        refreshedAt = SystemClock.elapsedRealtime()
        feed.start()
    }

    fun stop() {
        active = false
        generation++
        changed = null
        main.removeCallbacks(debounce)
        if (observing) runCatching { app.contentResolver.unregisterContentObserver(observer) }
        observing = false
        feed.stop()
        posterRequest?.close()
        posterJob?.cancel()
        posterJob = null
        posterRequest = null
    }

    fun refresh(onLoaded: (() -> Unit)? = null) {
        if (onLoaded != null) changed = onLoaded
        if (!active) return
        observe()
        refreshedAt = SystemClock.elapsedRealtime()
        feed.refresh()
    }

    fun tick() {
        if (active && SystemClock.elapsedRealtime() - refreshedAt >= 5 * 60_000) refresh()
    }

    fun hide(item: WatchItem) {
        hidden.hide(item)
        feed.hide(item.keys())
    }

    private fun observe() {
        if (!active) return
        if (tv.availability() != WatchAvailability.READY) {
            if (observing) runCatching { app.contentResolver.unregisterContentObserver(observer) }
            observing = false
            return
        }
        if (observing) return
        observing = runCatching {
            app.contentResolver.registerContentObserver(TvContract.WatchNextPrograms.CONTENT_URI,
                true, observer)
            true
        }.getOrDefault(false)
    }

    private fun sources(): Map<WatchSource, () -> WatchFeed.Load> = WatchSource.entries
        .associateWith { source ->
            {
                val request = WatchRequest()
                object : WatchFeed.Load {
                    override fun fetch(): WatchResult = when (source) {
                        WatchSource.TV -> tv.load(request)
                        WatchSource.SERVER -> ServerContinueSource(app).load(request)
                        WatchSource.LOCAL -> if (WatchAccess.mediaSessions(app)) {
                            WatchResult.loaded(LocalWatchHistory(app).continueItems())
                        } else WatchResult(WatchAvailability.PERMISSION_REQUIRED)
                    }
                    override fun cancel() = request.close()
                }
            }
        }

    private fun notifyChanged() {
        changed?.invoke()
        listeners.toList().forEach { it() }
    }

    private fun loadPosters() {
        if (!active) return
        if (posterJob != null) {
            postersPending = true
            return
        }
        val missing = items.filter { item ->
            val candidates = item.posterCandidates()
            candidates.isNotEmpty() && candidates.none { posters.get(it) != null }
        }
        if (missing.isEmpty()) return
        val ticket = generation
        val request = WatchRequest()
        posterRequest = request
        posterJob = scope.launch {
            try {
                for (item in missing) {
                    val bitmap = withContext(Dispatchers.IO) {
                        item.posterCandidates().firstNotNullOfOrNull { uri ->
                            runCatching { posterLoader.load(uri, request) }.getOrNull()
                        }
                    } ?: continue
                    if (!active || ticket != generation) break
                    for (candidateUri in item.posterCandidates()) {
                        posters.put(candidateUri, bitmap)
                    }
                    notifyChanged()
                }
            } finally {
                request.close()
                if (ticket == generation) {
                    posterJob = null
                    posterRequest = null
                    if (postersPending) {
                        postersPending = false
                        loadPosters()
                    }
                }
            }
        }
    }
}
