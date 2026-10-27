package com.gathin.tvhome.feature.main.coordinator

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.SoundEffectConstants
import android.view.View
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.settings.data.Config
import com.gathin.tvhome.feature.main.data.LibraryApps
import com.gathin.tvhome.feature.main.data.Order
import com.gathin.tvhome.feature.main.adapter.AppAdapter
import com.gathin.tvhome.feature.main.widget.BottomDockView
import com.gathin.tvhome.feature.main.widget.HomeGrid
import com.gathin.tvhome.core.design.DockDecoration
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.core.util.SoundEffects
import com.gathin.tvhome.feature.main.widget.WidgetShelf


class DockModeController(
    private val activity: Activity,
    private val dockView: BottomDockView,
    private val grid: HomeGrid,
    private val heroName: View,
    private val adapter: AppAdapter,
    private val widgets: WidgetShelf? = null,
    private val host: Host
) : BottomDockView.Listener {

    interface Host {
        fun openLibrary()
        fun showContextMenu(anchor: View, items: List<WidgetShelf.MenuItem>)
        fun focusTopHeader()
        fun onAppsChanged()
        fun hideGridChrome()
    }

    private val density = activity.resources.displayMetrics.density
    private var currentMode = Config.LAYOUT_GRID
    private var configuredDockApps: List<String> = emptyList()
    private var currentColumns = 4
    private var runningPackages: Set<String> = emptySet()

    fun setMotion(animations: Boolean, reducedMotion: Boolean) {
        dockView.setMotion(animations, reducedMotion)
    }

    fun setPalette(palette: Palette) {
        dockView.setPalette(palette)
    }

    fun updateRunningApps(running: Set<String>) {
        runningPackages = running
        dockView.updateRunningApps(running)
    }

    fun isDockMode(): Boolean = currentMode == Config.LAYOUT_DOCK

    fun hasFocus(): Boolean = dockView.hasFocus()

    fun applyMode(mode: String, dockApps: List<String>, columns: Int, dockTitleEnabled: Boolean) {
        currentMode = Config.normalizeLayoutMode(mode)
        configuredDockApps = dockApps
        currentColumns = maxOf(1, columns)

        if (isDockMode()) {
            grid.visibility = View.GONE
            heroName.visibility = View.GONE
            host.hideGridChrome()
            dockView.visibility = View.VISIBLE
            reloadDockItems()
            alignShelf(currentColumns)
        } else {
            dockView.visibility = View.GONE
            grid.visibility = View.VISIBLE
            heroName.visibility = if (dockTitleEnabled) View.VISIBLE else View.GONE
            widgets?.setEnabled(false)
        }
    }

    fun alignShelf(columns: Int) {
        currentColumns = maxOf(1, columns)
        val dockTop = if (isDockMode()) {
            val parentHeight = (grid.parent as? View)?.height?.takeIf { it > 0 }
                ?: activity.resources.displayMetrics.heightPixels
            dockView.top.takeIf { it > 0 }
                ?: (parentHeight - Math.round(DOCK_CLEARANCE_DP * density))
        } else {
            val gridTop = if (grid.top > 0) grid.top else Math.round(92 * density)
            gridTop + grid.paddingTop - Math.round(DockDecoration.PADDING_DP * density)
        }
        widgets?.align(grid, currentColumns, dockTop)
    }

    fun reloadDockItems() {
        if (!isDockMode()) return
        val keys = adapter.movableKeys()
        val limit = minOf(keys.size, MAX_DOCK_APPS)
        val items = ArrayList<BottomDockView.DockItem>(limit)
        for (index in 0 until limit) {
            val key = keys[index]
            val pos = adapter.positionOf(key)
            if (pos < 0) continue
            val component = ComponentName.unflattenFromString(key) ?: continue
            items.add(
                BottomDockView.DockItem(
                    key = key,
                    packageName = component.packageName,
                    label = adapter.labelAt(pos),
                    icon = adapter.iconAt(pos)
                )
            )
        }
        dockView.bindItems(items, this)
    }

    fun focusDefault(): Boolean {
        if (!isDockMode()) return false
        val preferred = if (dockView.lastIndex() > 0) dockView.lastIndex()
        else if (dockView.slotCount() > 1) 1 else 0
        return dockView.focusSlot(preferred)
    }

    fun focusFromColumn(column: Int, totalColumns: Int): Boolean {
        if (!isDockMode()) return false
        val slots = dockView.slotCount()
        if (slots <= 0) return false
        val safeColumns = maxOf(1, totalColumns)
        val mapped = Math.round(column.toFloat() * (slots - 1) / maxOf(1, safeColumns - 1))
        return dockView.focusSlot(mapped.coerceIn(0, slots - 1))
    }

    override fun onAllAppsClicked() {
        host.openLibrary()
    }

    override fun onAppClicked(item: BottomDockView.DockItem) {
        runningPackages = runningPackages + item.packageName
        dockView.updateRunningApps(runningPackages)
        val pos = adapter.positionOf(item.key)
        if (pos >= 0) adapter.launchAt(pos)
    }

    override fun onAppLongClicked(item: BottomDockView.DockItem, anchor: View) {
        val keys = adapter.movableKeys()
        val index = keys.indexOf(item.key)
        val visibleCount = minOf(keys.size, MAX_DOCK_APPS)
        val menuItems = ArrayList<WidgetShelf.MenuItem>()

        if (index > 0) {
            menuItems.add(
                WidgetShelf.MenuItem(
                    R.drawable.ic_move,
                    activity.getString(R.string.tile_move_left),
                    Runnable { moveDockApp(item.key, -1) }
                )
            )
        }
        if (index in 0 until (visibleCount - 1)) {
            menuItems.add(
                WidgetShelf.MenuItem(
                    R.drawable.ic_move,
                    activity.getString(R.string.tile_move_right),
                    Runnable { moveDockApp(item.key, 1) }
                )
            )
        }
        menuItems.add(
            WidgetShelf.MenuItem(
                R.drawable.ic_remove,
                activity.getString(R.string.library_remove_shortcut),
                Runnable {
                    if (LibraryApps.save(activity, item.packageName, false)) {
                        host.onAppsChanged()
                    }
                }
            )
        )
        menuItems.add(
            WidgetShelf.MenuItem(
                R.drawable.ic_info,
                activity.getString(R.string.app_info),
                Runnable {
                    val uri = Uri.parse("package:${item.packageName}")
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        activity.startActivity(intent)
                    } catch (_: Exception) {
                    }
                }
            )
        )
        host.showContextMenu(anchor, menuItems)
    }

    override fun onItemFocused(index: Int, item: BottomDockView.DockItem?) {
        host.hideGridChrome()
    }

    override fun onNavigateUp(index: Int): Boolean {
        val slots = maxOf(1, dockView.slotCount())
        val column = (index * currentColumns) / slots
        if (widgets?.enabled() == true && widgets.focusFromDock(column)) {
            if (SoundEffects.isEnabled(activity)) {
                dockView.playSoundEffect(SoundEffectConstants.NAVIGATION_UP)
            }
            return true
        }
        host.focusTopHeader()
        return true
    }

    private fun moveDockApp(key: String, delta: Int) {
        val from = adapter.positionOf(key)
        val count = minOf(adapter.movableCount(), MAX_DOCK_APPS)
        val to = from + delta
        if (from < 0 || to < 0 || to >= count) return

        adapter.move(from, to)
        val order = Order(Order.dockKey(configuredDockApps), adapter.movableKeys())
        if (Order.save(Order.file(activity), order)) {
            adapter.setSavedOrder(Order.ranks(order, configuredDockApps))
        }
        reloadDockItems()
        dockView.post { dockView.focusSlot(to + 1) }
    }

    companion object {
        private const val MAX_DOCK_APPS = 10
        private const val DOCK_CLEARANCE_DP = 136f
    }
}
