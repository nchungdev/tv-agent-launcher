package com.gathin.tvhome.feature.main.coordinator

import android.content.Context
import android.os.SystemClock
import com.gathin.tvhome.feature.main.data.WeatherRefreshPolicy
import com.gathin.tvhome.feature.main.data.WeatherSceneState
import com.gathin.tvhome.feature.system.data.Weather
import com.gathin.tvhome.feature.system.data.WeatherCache
import com.gathin.tvhome.feature.system.data.WeatherHttp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeWeatherCoordinator(
    private val scope: CoroutineScope,
    private val source: Source,
    private val city: () -> String,
    private val onChanged: (Weather.Report?, WeatherSceneState) -> Unit,
    private val wall: () -> Long = System::currentTimeMillis,
    private val elapsed: () -> Long = SystemClock::elapsedRealtime
) {
    interface Request {
        fun load(): Weather.Report
        fun cancel()
    }

    interface Source {
        fun revision(): Long = 0
        fun cached(city: String): Weather.Report?
        fun request(city: String): Request
        fun save(city: String, report: Weather.Report)
    }

    var offline = false
        private set

    private var active = false
    private var selected: String? = null
    private var pendingForce = false
    private var cacheReady = false
    private var revision = Long.MIN_VALUE
    private var generation = 0
    private var report: Weather.Report? = null
    private var job: Job? = null
    private var request: Request? = null
    private val policy = WeatherRefreshPolicy()

    fun start() {
        if (active) return
        active = true
        tick()
    }

    fun stop() {
        if (!active) return
        active = false
        cancelRequest()
    }

    fun reloadLocation() {
        cancelRequest()
        selected = null
        cacheReady = false
        tick(true)
    }

    fun tick(force: Boolean = false) {
        if (!active) return
        val current = city()
        val changed = current != selected || revision != source.revision()
        if (changed || !cacheReady) {
            if (!changed && job?.isActive == true) {
                pendingForce = pendingForce || force
                return
            }
            cancelRequest()
            selected = current
            revision = source.revision()
            report = null
            if (changed) { policy.reset(); offline = false }
            cacheReady = false
            pendingForce = force
            val ticket = ++generation
            job = scope.launch {
                val cached = try {
                    withContext(Dispatchers.IO) { source.cached(current) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                if (!active || ticket != generation || city() != current) return@launch
                report = cached
                cacheReady = true
                job = null
                val refresh = pendingForce
                pendingForce = false
                tick(refresh)
            }
            publish()
            return
        }
        publish()
        if (job?.isActive == true) {
            pendingForce = pendingForce || force
            return
        }
        if (!force && !policy.due(report, wall(), elapsed())) return
        val key = current
        val version = revision
        val ticket = ++generation
        val pending = source.request(key)
        request = pending
        job = scope.launch {
            try {
                val fresh = withContext(Dispatchers.IO) { pending.load() }
                if (!active || ticket != generation || city() != key ||
                    source.revision() != version) return@launch
                source.save(key, fresh)
                report = fresh
                offline = false
                policy.reset()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (ticket == generation) {
                    offline = true
                    policy.failed(elapsed())
                }
            } finally {
                pending.cancel()
                if (ticket == generation) {
                    request = null
                    job = null
                    pendingForce = false
                    if (active) tick()
                }
            }
        }
    }

    private fun publish() {
        val scene = WeatherSceneState.from(report, wall())
        val shown = if (scene.freshness == WeatherSceneState.Freshness.EXPIRED) null else report
        onChanged(shown, scene)
    }

    private fun cancelRequest() {
        generation++
        request?.cancel()
        request = null
        job?.cancel()
        job = null
    }

    companion object {
        fun source(context: Context): Source {
            val app = context.applicationContext
            val cache = WeatherCache(app)
            return object : Source {
                override fun revision() =
                    com.gathin.tvhome.feature.system.data.WeatherLocation.version(app)
                override fun cached(city: String) = cache.read(city)
                override fun save(city: String, report: Weather.Report) = cache.write(city, report)
                override fun request(city: String): Request = object : Request {
                    private val http = WeatherHttp()
                    override fun load() = Weather.fetch(app, city, http)
                    override fun cancel() = http.close()
                }
            }
        }
    }
}
