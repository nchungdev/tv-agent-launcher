package com.gathin.tvhome.feature.settings.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.util.Log
import com.gathin.tvhome.Contract
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.main.data.Festival
import com.gathin.tvhome.feature.main.coordinator.WallpaperRotation
import com.gathin.tvhome.feature.main.data.HomeLook
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets


class Config private constructor(
    @JvmField val dockApps: List<String>,
    @JvmField val fillDock: Boolean,
    @JvmField val hiddenApps: List<String>,
    @JvmField val liveTvPackage: String,
    @JvmField val liveTvAction: String,
    @JvmField val sourcePanelService: String,
    @JvmField val sourcePanelAction: String,
    @JvmField val inputAction: String,
    @JvmField val columns: Int,
    @JvmField val rowsBelowDock: Int,
    @JvmField val animations: Boolean,
    @JvmField val advancedEffects: Boolean,
    @JvmField val shamsiDate: Boolean,
    @JvmField val greetingName: String,
    @JvmField val moduleCards: Boolean,
    @JvmField val reducedMotion: Boolean,
    @JvmField val dockTitle: Boolean,
    @JvmField val dockRow: Boolean,
    @JvmField val layoutMode: String,
    @JvmField val reorderButton: Boolean,
    @JvmField val vpnApp: String,
    @JvmField val showHomeSettingsShortcut: Boolean,
    @JvmField val showStats: Boolean,
    @JvmField val appLibrary: Boolean,
    @JvmField val backgroundColor: Int,
    @JvmField val skyIntensity: Int,
    @JvmField val settingsKeycodes: IntArray,
    @JvmField val settingsKeyPanel: Boolean,
    @JvmField val wallpaperEnabled: Boolean,
    @JvmField val wallpaper: File?,
    @JvmField val wallpapers: List<File>,
    @JvmField val wallpaperRotationMinutes: Int,
    @JvmField val wallpaperDim: Int,
    @JvmField val overlayApplied: Boolean,
    @JvmField val festival: Festival,
    @JvmField val inputKeycodes: IntArray,
    appKeys: List<String>
) {
    @JvmField
    val appKeys: List<String> = appKeys.toList()

    companion object {
        private const val TAG = "TvHome.Config"
        @JvmField val FILE_NAME: String = Contract.Theme.CONFIG_FILE
        @JvmField val WALLPAPER_NAME: String = Contract.Theme.WALLPAPER_FILE
        const val SETTINGS_NAME = "launcher_settings"
        const val LAYOUT_GRID = "grid"
        const val LAYOUT_DOCK = "dock"
        private val THEME_REVISION_NAME: String = Contract.Theme.REVISION_FILE
        private const val THEME_SIGNATURE_KEY = "theme_signature"

        @JvmStatic
        fun normalizeLayoutMode(value: String?): String =
            if (LAYOUT_DOCK.equals(value?.trim(), ignoreCase = true)) LAYOUT_DOCK else LAYOUT_GRID

        @JvmStatic
        fun dir(ctx: Context): File? = ctx.getExternalFilesDir(null)

        @JvmStatic
        fun load(ctx: Context): Config {
            val res = ctx.resources
            var dock: List<String> = ArrayList(listOf(*res.getStringArray(R.array.dock_apps)))
            var fillDock = res.getBoolean(R.bool.fill_dock)
            var hidden: List<String> = ArrayList(listOf(*res.getStringArray(R.array.hidden_apps)))
            var liveTv = res.getString(R.string.live_tv_package).trim()
            var liveTvAction = res.getString(R.string.live_tv_action).trim()
            var panelService = res.getString(R.string.source_panel_service).trim()
            var panelAction = res.getString(R.string.source_panel_action).trim()
            var inputAction = res.getString(R.string.input_action).trim()
            var columns = res.getInteger(R.integer.columns)
            var rowsBelow = res.getInteger(R.integer.rows_below_dock)
            var animations = res.getBoolean(R.bool.animations)
            var advancedEffects = res.getBoolean(R.bool.advanced_effects)
            var shamsi = res.getBoolean(R.bool.shamsi_date)
            var greetingName = res.getString(R.string.greeting_name).trim()
            var moduleCards = res.getBoolean(R.bool.module_cards)
            var reducedMotion = res.getBoolean(R.bool.reduced_motion)
            var dockTitle = res.getBoolean(R.bool.dock_title)
            var dockRow = res.getBoolean(R.bool.dock_row)
            var layoutMode = normalizeLayoutMode(res.getString(R.string.layout_mode))
            var reorderButton = res.getBoolean(R.bool.reorder_button)
            var vpn = res.getString(R.string.vpn_app).trim()
            var homeSettings = res.getBoolean(R.bool.show_home_settings_shortcut)
            var showStats = res.getBoolean(R.bool.show_stats)
            var library = res.getBoolean(R.bool.app_library)
            var background = -0x1000000
            var skyIntensity = res.getInteger(R.integer.sky_intensity)
            var settingsKeycodes = res.getIntArray(R.array.settings_key_keycodes)
            var settingsKeyPanel = res.getBoolean(R.bool.settings_key_panel)
            var inputKeycodes = res.getIntArray(R.array.input_key_keycodes)
            var appKeys: List<String> = ArrayList(listOf(*res.getStringArray(R.array.app_keys)))
            var applied = false

            val dir = dir(ctx)
            val file = if (dir != null) File(dir, FILE_NAME) else null
            val json = read(file)
            if (json != null) {
                applied = true
                dock = stringList(json, Contract.Key.DOCK_APPS, dock)
                fillDock = bool(json, Contract.Key.FILL_DOCK, fillDock)
                hidden = stringList(json, Contract.Key.HIDDEN_APPS, hidden)
                liveTv = string(json, Contract.Key.LIVE_TV_PACKAGE, liveTv)
                liveTvAction = string(json, Contract.Key.LIVE_TV_ACTION, liveTvAction)
                panelService = string(json, Contract.Key.SOURCE_PANEL_SERVICE, panelService)
                panelAction = string(json, Contract.Key.SOURCE_PANEL_ACTION, panelAction)
                inputAction = string(json, Contract.Key.INPUT_ACTION, inputAction)
                columns = integer(json, Contract.Key.COLUMNS, columns, Contract.Range.COLUMNS_MIN, Contract.Range.COLUMNS_MAX)
                rowsBelow = integer(json, Contract.Key.ROWS_BELOW_DOCK, rowsBelow, Contract.Range.ROWS_BELOW_DOCK_MIN, Contract.Range.ROWS_BELOW_DOCK_MAX)
                animations = bool(json, Contract.Key.ANIMATIONS, animations)
                advancedEffects = bool(json, Contract.Key.ADVANCED_EFFECTS, advancedEffects)
                shamsi = bool(json, Contract.Key.SHAMSI_DATE, shamsi)
                greetingName = string(json, Contract.Key.GREETING_NAME, greetingName).trim()
                moduleCards = bool(json, Contract.Key.MODULE_CARDS, moduleCards)
                reducedMotion = bool(json, Contract.Key.REDUCED_MOTION, reducedMotion)
                dockTitle = bool(json, Contract.Key.DOCK_TITLE, dockTitle)
                dockRow = bool(json, Contract.Key.DOCK_ROW, dockRow)
                layoutMode = normalizeLayoutMode(string(json, Contract.Key.LAYOUT_MODE, layoutMode))
                reorderButton = bool(json, Contract.Key.REORDER_BUTTON, reorderButton)
                vpn = string(json, Contract.Key.VPN_APP, vpn)
                homeSettings = bool(json, Contract.Key.SHOW_HOME_SETTINGS_SHORTCUT, homeSettings)
                showStats = bool(json, Contract.Key.SHOW_STATS, showStats)
                library = bool(json, Contract.Key.APP_LIBRARY, library)
                background = color(json, Contract.Key.BACKGROUND_COLOR, background)
                skyIntensity = integer(json, Contract.Key.SKY_INTENSITY, skyIntensity, Contract.Range.SKY_INTENSITY_MIN, Contract.Range.SKY_INTENSITY_MAX)
                settingsKeycodes = intArray(json, Contract.Key.SETTINGS_KEY_KEYCODES, settingsKeycodes)
                settingsKeyPanel = bool(json, Contract.Key.SETTINGS_KEY_PANEL, settingsKeyPanel)
                inputKeycodes = intArray(json, Contract.Key.INPUT_KEY_KEYCODES, inputKeycodes)
                appKeys = stringList(json, Contract.Key.APP_KEYS, appKeys)
            }

            val choices = ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
            syncThemeChoices(choices, dir, file)
            val chosenColumns = choices.getInt(Contract.Key.COLUMNS, columns)
            if (chosenColumns in 4..7) columns = chosenColumns
            layoutMode = normalizeLayoutMode(choices.getString(Contract.Key.LAYOUT_MODE, layoutMode))
            animations = choices.getBoolean(Contract.Key.ANIMATIONS, animations)
            advancedEffects = choices.getBoolean(Contract.Key.ADVANCED_EFFECTS, advancedEffects)
            background = choices.getInt(Contract.Key.BACKGROUND_COLOR, background)
            var wallpaperFile: File? = if (dir != null) File(dir, WALLPAPER_NAME) else null
            if (wallpaperFile != null && !(wallpaperFile.isFile && wallpaperFile.length() > 0)) wallpaperFile = null
            val wallpaperEnabled = json == null || bool(json, Contract.Key.WALLPAPER, true)
            if (!wallpaperEnabled) wallpaperFile = null
            val rotationMinutes = if (json == null) 0 else integer(json, Contract.Key.WALLPAPER_ROTATION_MINUTES, 0, Contract.Range.WALLPAPER_ROTATION_MINUTES_MIN, Contract.Range.WALLPAPER_ROTATION_MINUTES_MAX)
            val wallpaperDim = if (json == null) 0 else integer(json, Contract.Key.WALLPAPER_DIM, 0, Contract.Range.WALLPAPER_DIM_MIN, Contract.Range.WALLPAPER_DIM_MAX)
            val wallpapers = ArrayList<File>()
            if (rotationMinutes > 0 && dir != null && wallpaperEnabled) {
                wallpapers.addAll(WallpaperRotation.images(File(dir, Contract.Theme.WALLPAPERS_DIRECTORY)))
            }
            if (wallpapers.isEmpty() && wallpaperFile != null) wallpapers.add(wallpaperFile)
            val festival = if (json == null) Festival.NONE else Festival.parse(
                string(json, Contract.Key.FESTIVAL, ""),
                string(json, Contract.Key.FESTIVAL_UNTIL, ""),
                string(json, Contract.Key.GREETING, ""),
                string(json, Contract.Key.ACCENT_COLOR, "")
            )

            return Config(
                dock, fillDock, hidden, liveTv, liveTvAction, panelService, panelAction, inputAction,
                maxOf(1, columns), maxOf(0, rowsBelow), animations, advancedEffects,
                shamsi, greetingName, moduleCards, reducedMotion, dockTitle, dockRow,
                layoutMode, reorderButton, vpn, homeSettings, showStats, library, background,
                skyIntensity.coerceIn(0, 100), settingsKeycodes, settingsKeyPanel, wallpaperEnabled, wallpaperFile,
                wallpapers, rotationMinutes, wallpaperDim, applied,
                festival, inputKeycodes, appKeys
            )
        }

        const val KEY_GREETING_NAME = "greeting_name"
        private fun syncThemeChoices(choices: SharedPreferences, dir: File?, config: File?) {
            val revision = if (dir == null) null else File(dir, THEME_REVISION_NAME)
            val token = revisionToken(revision)
            val signature = "${fileStamp(config)}:$token"
            val seen = choices.getString(THEME_SIGNATURE_KEY, null)
            if (seen == null) {
                val editor = choices.edit()
                if ("absent" != token) {
                    editor.remove(Contract.Key.COLUMNS).remove(Contract.Key.ANIMATIONS)
                        .remove(Contract.Key.ADVANCED_EFFECTS).remove(Contract.Key.BACKGROUND_COLOR)
                        .remove(HomeLook.BACKDROP_KEY)
                }
                editor.putString(THEME_SIGNATURE_KEY, signature).apply()
            } else if (seen != signature) {
                choices.edit().remove(Contract.Key.COLUMNS).remove(Contract.Key.ANIMATIONS).remove(Contract.Key.ADVANCED_EFFECTS)
                    .remove(Contract.Key.BACKGROUND_COLOR).remove(HomeLook.BACKDROP_KEY)
                    .putString(THEME_SIGNATURE_KEY, signature).apply()
            }
        }

        private fun fileStamp(file: File?): String =
            if (file != null && file.isFile) "${file.length()}@${file.lastModified()}" else "absent"

        private fun revisionToken(file: File?): String {
            if (file == null || !file.isFile || file.length() > 64) return "absent"
            return try {
                FileInputStream(file).use { `in` ->
                    val bytes = ByteArray(file.length().toInt())
                    val n = `in`.read(bytes)
                    if (n > 0) String(bytes, 0, n, StandardCharsets.US_ASCII) else "absent"
                }
            } catch (_: Exception) {
                "absent"
            }
        }

        private fun read(file: File?): JSONObject? {
            if (file == null || !file.isFile) return null
            if (file.length() > Contract.Theme.MAX_CONFIG_BYTES) {
                Log.w(TAG, "ignoring $file: larger than 64 KB")
                return null
            }
            return try {
                FileInputStream(file).use { `in` ->
                    val buf = ByteArray(file.length().toInt())
                    var n = 0
                    var r = 0
                    while (n < buf.size && `in`.read(buf, n, buf.size - n).also { r = it } > 0) n += r
                    var text = String(buf, 0, n, StandardCharsets.UTF_8)
                    if (text.startsWith("\uFEFF")) text = text.substring(1)
                    JSONObject(text)
                }
            } catch (e: Exception) {
                Log.w(TAG, "ignoring $file: ${e.message}")
                null
            }
        }

        private fun string(o: JSONObject, key: String, fallback: String): String {
            val v = o.opt(key)
            return if (v is String) v.trim() else fallback
        }

        private fun bool(o: JSONObject, key: String, fallback: Boolean): Boolean {
            val v = o.opt(key)
            return if (v is Boolean) v else fallback
        }

        private fun integer(o: JSONObject, key: String, fallback: Int, min: Int, max: Int): Int {
            val v = o.opt(key)
            if (v !is Number) return fallback
            val i = v.toInt()
            return if (i in min..max) i else fallback
        }

        private fun color(o: JSONObject, key: String, fallback: Int): Int {
            val v = o.opt(key)
            if (v !is String) return fallback
            return try {
                Color.parseColor(v.trim()) or -0x1000000
            } catch (_: IllegalArgumentException) {
                fallback
            }
        }

        private fun intArray(o: JSONObject, key: String, fallback: IntArray): IntArray {
            val v = o.opt(key)
            if (v !is JSONArray) return fallback
            val out = IntArray(v.length())
            for (i in 0 until v.length()) {
                val item = v.opt(i)
                if (item !is Number) return fallback
                out[i] = item.toInt()
                if (out[i] < 1) return fallback
            }
            return out
        }

        private fun stringList(o: JSONObject, key: String, fallback: List<String>): List<String> {
            val v = o.opt(key)
            if (v !is JSONArray) return fallback
            val out = ArrayList<String>()
            for (i in 0 until v.length()) {
                val item = v.opt(i)
                if (item is String && item.trim().isNotEmpty()) out.add(item.trim())
            }
            return out
        }
    }
}
