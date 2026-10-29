package com.gathin.tvhome.feature.main.widget

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.smarthome.data.TvModesClient
import com.gathin.tvhome.feature.smarthome.data.TvModesState
import org.json.JSONObject

class TvModeTiles(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val onChanged: () -> Unit
) {
    data class Tile(val id: String, val icon: String, val title: String, val active: Boolean)

    private val main = Handler(Looper.getMainLooper())
    private val client = TvModesClient(context)
    private var state: TvModesState? = cached()
    private var loading = false

    fun tiles(): List<Tile>? {
        val current = state ?: return null
        if (current.modes.isEmpty()) return null
        val modes = current.modes.take(SLOTS - 1).map {
            Tile(it.id, it.icon, it.name, it.id == current.active)
        }
        val normal = Tile(TvModesState.NORMAL, NORMAL_ICON,
            context.getString(R.string.tv_mode_normal), current.active.isBlank())
        return modes + normal
    }

    fun refresh() {
        if (loading) return
        loading = true
        Thread {
            val loaded = runCatching { client.load() }.getOrNull()
            main.post {
                loading = false
                if (loaded != null) remember(loaded)
            }
        }.start()
    }

    fun activate(index: Int): Boolean {
        val tile = tiles()?.getOrNull(index) ?: return false
        Thread {
            val result = runCatching { client.activate(tile.id) }
            main.post {
                result.onSuccess { remember(it) }.onFailure {
                    Toast.makeText(context, R.string.tv_mode_offline, Toast.LENGTH_SHORT).show()
                    onChanged()
                }
            }
        }.start()
        return true
    }

    private fun remember(next: TvModesState) {
        state = next
        prefs.edit().putString(KEY, next.toJson().toString()).apply()
        onChanged()
    }

    private fun cached(): TvModesState? = prefs.getString(KEY, null)?.let {
        runCatching { TvModesState.fromJson(JSONObject(it)) }.getOrNull()
    }

    private companion object {
        const val KEY = "tv_modes"
        const val SLOTS = 3
        const val NORMAL_ICON = "💡"
    }
}
