package com.gathin.tvhome.feature.main.adapter

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.settings.data.Config
import com.gathin.tvhome.feature.main.data.Cards
import com.gathin.tvhome.feature.main.data.LibraryApps
import com.gathin.tvhome.feature.main.data.Order
import com.gathin.tvhome.feature.main.data.DockSlots
import com.gathin.tvhome.feature.settings.ui.HomeSettingsActivity
import com.gathin.tvhome.feature.main.widget.HomeGrid
import com.gathin.tvhome.core.design.M3Skin
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.core.design.SettingsBanner
import com.gathin.tvhome.core.design.VidoraBanner


open class AppAdapter(
    private val ctx: Context,
    private val listener: TileListener?,
    var dockSize: Int,
    cfg: Config
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface TileListener {
        fun onTileFocused(position: Int, card: View)
        fun onTileLongPressed(position: Int, card: View)
        fun onTileVerticalKey(position: Int, keyCode: Int): Boolean
        fun onLibraryRequested()
        fun onLibraryFocused()
        fun onLibraryUp()
    }

    private val favourites: List<String> = cfg.dockApps
    private val fillDock: Boolean = cfg.fillDock
    private val hidden: Set<String> = cfg.hiddenApps.toSet()
    private val animations: Boolean = cfg.animations
    private val advancedEffects: Boolean = cfg.animations && cfg.advancedEffects
    private val showHomeSettings: Boolean = cfg.showHomeSettingsShortcut
    private val showLibrary: Boolean = cfg.appLibrary
    private val reducedMotion: Boolean = cfg.reducedMotion
    private val rowsBelowDock: Int = cfg.rowsBelowDock
    private val dockTitle: Boolean = cfg.dockTitle
    private val dockRow: Boolean = cfg.dockRow

    private var savedRanks: Map<String, Int> = emptyMap()
    private var reordering = false
    private var docked = 0
    private var emptySlots = 0
    private var leanbackPackages: Set<String> = emptySet()
    private val items = ArrayList<Entry>()
    private val art = HashMap<String, Array<Drawable?>>()
    private var nameColor = 0
    private var tileColor = 0
    private var palette: Palette? = null
    private var recyclerView: RecyclerView? = null

    init {
        setHasStableIds(true)
    }

    fun reload(cards: List<Cards.Card>): Boolean {
        val pm = ctx.packageManager
        val probe = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val found = ArrayList(pm.queryIntentActivities(probe, 0))
        val tvPackages = HashSet<String>()
        for (ri in found) ri.activityInfo?.let { tvPackages.add(it.packageName) }
        leanbackPackages = tvPackages

        val added = LibraryApps.load(ctx)
        val removed = LibraryApps.loadRemoved(ctx)
        if (added.isNotEmpty()) {
            val seen = HashSet(tvPackages)
            for (ri in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
                val ai = ri.activityInfo ?: continue
                if (added.contains(ai.packageName) && seen.add(ai.packageName)) found.add(ri)
            }
        }

        val cardByPkg = cards.associateBy { it.pkg }
        val fresh = ArrayList<Entry>()
        val seen = HashSet<String>()

        for (ri in found) {
            val ai = ri.activityInfo ?: continue
            val pkg = ai.packageName
            if (pkg == ctx.packageName || !ai.exported ||
                !LibraryApps.visible(tvPackages.contains(pkg), hidden.contains(pkg), added.contains(pkg), removed.contains(pkg))) continue

            val component = ComponentName(pkg, ai.name)
            val card = cardByPkg[pkg]
            if (card != null && card.component != component) continue

            val label = card?.label ?: Cards.cleanLabel(ri.loadLabel(pm)?.toString(), pkg)
            if (!seen.add("$pkg\n$label")) continue

            val artKey = "${component.flattenToShortString()}\n${ai.applicationInfo.sourceDir}"
            var cached = art[artKey]
            if (cached == null) {
                val b = loadBanner(pm, ri)
                val ic = try { ri.loadIcon(pm) } catch (_: Exception) { null }
                cached = arrayOf(b, ic)
                art[artKey] = cached
            }
            val banner = cached[0]
            val icon = cached[1]
            if (card != null) {
                fresh.add(Entry(Entry.CARD, label, component, banner, icon, card.caption, card.accent))
            } else {
                fresh.add(Entry(label, component, banner, icon))
            }
        }

        fresh.sortWith { a, b ->
            val saved = Order.compare(savedRanks, a.component.flattenToShortString(), b.component.flattenToShortString())
            if (saved != 0) return@sortWith saved
            val fa = favourites.indexOf(a.pkg)
            val fb = favourites.indexOf(b.pkg)
            if (fa >= 0 || fb >= 0) {
                if (fa < 0) return@sortWith 1
                if (fb < 0) return@sortWith -1
                return@sortWith fa.compareTo(fb)
            }
            val ca = a.kind == Entry.CARD
            val cb = b.kind == Entry.CARD
            if (ca != cb) return@sortWith if (ca) -1 else 1
            a.label.compareTo(b.label, ignoreCase = true)
        }

        if (showHomeSettings && pm.resolveActivity(Intent(Settings.ACTION_HOME_SETTINGS), 0) != null) {
            fresh.add(Entry(Entry.HOME_SETTINGS, ctx.getString(R.string.home_settings),
                ComponentName(ctx, HomeSettingsActivity::class.java), null, ctx.getDrawable(R.drawable.ic_home_settings), null, 0))
        }

        docked = 0
        while (docked < fresh.size && favourites.contains(fresh[docked].pkg)) docked++
        emptySlots = if (dockRow && !reordering) DockSlots.empty(docked, dockSize, fresh.size, fillDock) else 0
        for (i in 0 until emptySlots) {
            fresh.add(docked + i, Entry(Entry.EMPTY, "", ComponentName(ctx, "empty-dock-slot-$i"), null, null, null, 0))
        }

        if (sameTiles(fresh)) return false
        items.clear()
        items.addAll(fresh)
        notifyDataSetChanged()
        return true
    }

    private fun sameTiles(fresh: List<Entry>): Boolean {
        if (fresh.size != items.size) return false
        for (i in fresh.indices) {
            val a = items[i]
            val b = fresh[i]
            if (a.kind != b.kind || a.component != b.component || a.label != b.label ||
                a.name != b.name || a.banner !== b.banner || a.icon !== b.icon) return false
        }
        return true
    }

    private fun loadBanner(pm: PackageManager, ri: ResolveInfo): Drawable? {
        if (SettingsBanner.PACKAGE == ri.activityInfo.packageName) return SettingsBanner(ctx)
        if (VidoraBanner.PACKAGE == ri.activityInfo.packageName) {
            val icon = try { ri.activityInfo.loadIcon(pm) } catch (_: Exception) { null }
            return VidoraBanner(ctx, icon)
        }
        var id = ri.activityInfo.banner
        if (id == 0) id = ri.activityInfo.applicationInfo.banner
        var banner: Drawable? = null
        if (id != 0) {
            try {
                val res = pm.getResourcesForApplication(ri.activityInfo.applicationInfo)
                val density = maxOf(DisplayMetrics.DENSITY_XHIGH, ctx.resources.displayMetrics.densityDpi)
                banner = res.getDrawableForDensity(id, density, null)
            } catch (_: Exception) {}
        }
        if (banner == null) banner = ri.activityInfo.loadBanner(pm)
        return if (banner != null && isBannerShaped(banner.intrinsicWidth, banner.intrinsicHeight)) banner else null
    }

    override fun getItemViewType(position: Int): Int = if (isLibraryAt(position)) LIBRARY_VIEW else TILE_VIEW

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == LIBRARY_VIEW) {
            val row = LayoutInflater.from(ctx).inflate(R.layout.row_app_library, parent, false)
            val button = row.findViewById<View>(R.id.btnLibrary)
            M3Skin.apply(row, Palette.currentOrBaseline(), animations, reducedMotion)
            button.setOnClickListener { listener?.onLibraryRequested() }
            button.setOnFocusChangeListener { _, focused -> if (focused) listener?.onLibraryFocused() }
            button.setOnKeyListener { _, key, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                if (key == KeyEvent.KEYCODE_DPAD_UP) listener?.onLibraryUp()
                key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN ||
                        key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT
            }
            return object : RecyclerView.ViewHolder(row) {}
        }

        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        val density = parent.resources.displayMetrics.density
        val dm = parent.resources.displayMetrics
        val width = if (parent.width > 0) parent.width else parent.measuredWidth
        val fallbackInner = dm.widthPixels - Math.round(112 * density)
        val inner = if (width > 0) width - parent.paddingLeft - parent.paddingRight else fallbackInner
        val height = if (parent is HomeGrid) parent.roomBelowHeader() else parent.height
        val safeHeight = if (height > 0) height else dm.heightPixels
        val effectiveDock = if (dockSize > 0) dockSize else 4
        val w = tileWidthPx(inner, safeHeight, effectiveDock, rowsBelowDock, dockTitle, dockRow, density)
        val h = tileHeightPx(w)

        val card = v.findViewById<View>(R.id.card)
        val cardLp = card.layoutParams
        cardLp.width = w
        cardLp.height = h
        card.layoutParams = cardLp

        val name = v.findViewById<View>(R.id.name)
        val nameLp = name.layoutParams as ViewGroup.MarginLayoutParams
        nameLp.width = w
        nameLp.topMargin = h + Math.round((if (dockRow) 7 else 4) * density)
        if (!dockRow) {
            nameLp.height = Math.round(20 * density)
            val pad = Math.round(4 * density)
            v.setPadding(v.paddingLeft, pad, v.paddingRight, pad)
        }
        name.layoutParams = nameLp

        return AppTileViewHolder(v, listener, animations, advancedEffects, reducedMotion)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (isLibraryAt(position)) return
        val h = holder as AppTileViewHolder
        val e = items[position]
        if (e.kind == Entry.EMPTY) {
            h.card.visibility = View.INVISIBLE
            h.card.isFocusable = false
            h.name.visibility = View.INVISIBLE
            h.banner.setImageDrawable(null)
            h.icon.setImageDrawable(null)
            h.card.setOnClickListener(null)
            h.card.setOnLongClickListener(null)
            return
        }
        h.card.visibility = View.VISIBLE
        h.card.isFocusable = true
        h.name.text = e.name
        h.name.visibility = if (dockRow) View.GONE else View.VISIBLE
        h.snap(h.card.isFocused)
        paint(h)
        if (e.banner != null) {
            h.banner.setImageDrawable(e.banner)
            h.banner.visibility = View.VISIBLE
            h.icon.visibility = View.GONE
            h.label.visibility = View.GONE
        } else {
            h.banner.setImageDrawable(null)
            h.banner.visibility = View.GONE
            h.icon.setImageDrawable(e.icon)
            h.icon.visibility = View.VISIBLE
            h.label.text = e.label
            h.label.visibility = View.VISIBLE
        }
        h.card.setOnClickListener { launch(e) }
        h.card.setOnLongClickListener { v ->
            val at = h.bindingAdapterPosition
            if (listener != null && at != RecyclerView.NO_POSITION) listener.onTileLongPressed(at, v)
            true
        }
    }

    private fun launch(e: Entry) {
        if (e.kind == Entry.HOME_SETTINGS) {
            ctx.startActivity(Intent(ctx, HomeSettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val i = Intent(Intent.ACTION_MAIN)
            .setComponent(e.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        if (leanbackPackages.contains(e.pkg)) i.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        try {
            if (animations) {
                ctx.startActivity(i, ActivityOptions.makeCustomAnimation(ctx, R.anim.app_open_enter, R.anim.app_open_exit).toBundle())
            } else {
                ctx.startActivity(i)
            }
        } catch (_: Exception) {
            Toast.makeText(ctx, ctx.getString(R.string.cant_open, e.label), Toast.LENGTH_SHORT).show()
        }
    }

    override fun getItemCount(): Int = items.size + (if (showLibrary) 1 else 0)

    fun appCount(): Int = items.size

    fun isLibraryAt(position: Int): Boolean = showLibrary && position == items.size

    fun containsPackage(pkg: String): Boolean = items.any { it.pkg == pkg }

    override fun getItemId(position: Int): Long =
        if (isLibraryAt(position)) Long.MIN_VALUE else items[position].component.flattenToShortString().hashCode().toLong()

    fun setSavedOrder(ranks: Map<String, Int>) { savedRanks = ranks }

    fun setReordering(on: Boolean) { reordering = on }

    fun hasEmptySlots(): Boolean = emptySlots > 0

    fun movableCount(): Int {
        val n = items.size
        return if (n > 0 && items[n - 1].kind == Entry.HOME_SETTINGS) n - 1 else n
    }

    fun movableKeys(): List<String> {
        val keys = ArrayList<String>()
        for (i in 0 until movableCount()) {
            val e = items[i]
            if (e.kind != Entry.EMPTY) keys.add(e.component.flattenToShortString())
        }
        return keys
    }

    fun move(from: Int, to: Int) {
        if (from == to || from < 0 || to < 0 || from >= movableCount() || to >= movableCount()) return
        items.add(to, items.removeAt(from))
        notifyItemMoved(from, to)
        if (from < dockSize || to < dockSize) {
            val lo = minOf(from, to)
            val hi = maxOf(from, to)
            notifyItemRangeChanged(lo, hi - lo + 1)
        }
    }

    fun keyAt(position: Int): String? =
        if (position in 0 until items.size) items[position].component.flattenToShortString() else null

    fun positionOf(key: String?): Int {
        if (key == null) return -1
        return items.indexOfFirst { it.component.flattenToShortString() == key }
    }

    fun dockFilled(): Int = if (emptySlots > 0) docked else dockSize

    fun focusablePosition(position: Int): Int = DockSlots.focusable(position, docked, emptySlots)

    fun labelAt(position: Int): String = if (position in 0 until items.size) items[position].label else ""

    fun iconAt(position: Int): Drawable? {
        if (position !in 0 until items.size) return null
        val entry = items[position]
        if (entry.icon != null) return entry.icon
        if (entry.component != null) {
            return try {
                ctx.packageManager.getActivityIcon(entry.component)
            } catch (_: Exception) {
                entry.banner
            }
        }
        return entry.banner
    }

    fun launchAt(position: Int) {
        if (position in 0 until items.size) launch(items[position])
    }

    fun setPalette(p: Palette) {
        palette = p
        nameColor = p.onSurface
        tileColor = p.surfaceContainerHigh
        val rv = recyclerView ?: return
        for (i in 0 until rv.childCount) {
            val vh = rv.getChildViewHolder(rv.getChildAt(i))
            if (vh is AppTileViewHolder) paint(vh)
        }
    }

    private fun paint(h: AppTileViewHolder) {
        if (nameColor == 0) return
        val d = h.banner.drawable
        if (d is SettingsBanner) palette?.let { d.setPalette(it) }
        h.banner.invalidate()
        h.name.setTextColor(nameColor)
        h.label.setTextColor(nameColor)
        h.card.backgroundTintList = ColorStateList.valueOf(tileColor)
    }

    override fun onAttachedToRecyclerView(rv: RecyclerView) { recyclerView = rv }

    override fun onDetachedFromRecyclerView(rv: RecyclerView) { recyclerView = null }

    fun trim() {
        art.clear()
    }

    fun cleanup() {
        art.clear()
        items.clear()
        recyclerView = null
    }

    fun focusScale(): Float = 1f

    fun ambientColorAt(position: Int): Int {
        if (position !in 0 until items.size) return -0xbbbbbc
        val e = items[position]
        if (e.ambient == 0) e.ambient = vividColor(e.banner ?: e.icon)
        return e.ambient
    }

    companion object {
        const val TILE_VIEW = 0
        const val LIBRARY_VIEW = 1
        const val FOCUS_SCALE = 1.10f
        const val NAME_GAP_DP = 10f
        const val DESIGN_W_DP = 180f
        const val DESIGN_H_DP = 101f
        const val COMPACT_ITEM_EXTRA_DP = 32f
        const val COMPACT_BOTTOM_ROOM_DP = 16f
        const val ITEM_EXTRA_DP = 32f
        const val DOCK_EXTRA_DP = 12f
        const val BOTTOM_ROOM_DP = 30f
        const val TITLE_ROOM_DP = 104f
        const val TOP_ROOM_DP = 12f
        const val MIN_HEIGHT_FIT = 0.85f

        @JvmStatic
        fun tileWidthPx(innerWidthPx: Int, columns: Int, density: Float): Int {
            val safeCols = if (columns > 0) columns else 4
            val design = Math.round(DESIGN_W_DP * density)
            if (innerWidthPx <= 0) return design
            val cell = innerWidthPx / safeCols
            val fit = ((cell - 12 * density) / FOCUS_SCALE).toInt()
            return Math.max(Math.round(80 * density), Math.min(design, fit))
        }

        @JvmStatic
        fun tileHeightPx(tileWidthPx: Int): Int = Math.round(tileWidthPx * DESIGN_H_DP / DESIGN_W_DP)

        @JvmStatic
        fun itemHeightPx(tileHeightPx: Int, density: Float): Int = itemHeightPx(tileHeightPx, true, density)

        @JvmStatic
        fun itemHeightPx(tileHeightPx: Int, dockRow: Boolean, density: Float): Int =
            tileHeightPx + Math.round((if (dockRow) ITEM_EXTRA_DP else COMPACT_ITEM_EXTRA_DP) * density)

        @JvmStatic
        fun peekPx(tileHeightPx: Int, density: Float): Int = Math.round(16 * density) + tileHeightPx / 5

        @JvmStatic
        fun tileWidthPx(
            innerWidthPx: Int, gridHeightPx: Int, columns: Int, rowsBelowDock: Int,
            dockTitle: Boolean, dockRow: Boolean, density: Float
        ): Int {
            val w = tileWidthPx(innerWidthPx, columns, density)
            if (gridHeightPx <= 0 || rowsBelowDock <= 0) return w
            val reserved = (if (dockRow) DOCK_EXTRA_DP + BOTTOM_ROOM_DP + (if (dockTitle) TITLE_ROOM_DP else 0f)
            else TOP_ROOM_DP + COMPACT_BOTTOM_ROOM_DP) * density
            val perItem = (gridHeightPx - reserved) / (rowsBelowDock + 1)
            val tileH = perItem - (if (dockRow) ITEM_EXTRA_DP else COMPACT_ITEM_EXTRA_DP) * density
            val byHeight = Math.round(tileH * DESIGN_W_DP / DESIGN_H_DP)
            return Math.max(Math.max(Math.round(80 * density), Math.round(w * MIN_HEIGHT_FIT)), Math.min(w, byHeight))
        }

        @JvmStatic
        fun isBannerShaped(width: Int, height: Int): Boolean =
            width <= 0 || height <= 0 || width >= height * 1.4f

        @JvmStatic
        fun cornerRadius(tileWidthPx: Int, density: Float, cornerDp: Float): Float =
            cornerDp * density * minOf(1f, tileWidthPx / (DESIGN_W_DP * density))

        @JvmStatic
        private fun vividColor(live: Drawable?): Int {
            if (live == null) return -0xbbbbbc
            val d = if (live.constantState != null) live.constantState!!.newDrawable().mutate() else live
            val saved = Rect(d.bounds)
            val n = 12
            val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            d.setBounds(0, 0, n, n)
            d.draw(c)
            d.bounds = saved
            val px = IntArray(n * n)
            bmp.getPixels(px, 0, n, 0, 0, n, n)
            bmp.recycle()
            val hsv = FloatArray(3)
            var best = -0xbbbbbc
            var bestScore = -1f
            var r = 0L
            var g = 0L
            var b = 0L
            for (p in px) {
                val pr = (p ushr 16) and 0xFF
                val pg = (p ushr 8) and 0xFF
                val pb = p and 0xFF
                r += pr
                g += pg
                b += pb
                val max = maxOf(pr, maxOf(pg, pb))
                val min = minOf(pr, minOf(pg, pb))
                val sat = if (max == 0) 0f else (max - min) / max.toFloat()
                val valF = max / 255f
                val score = sat * minOf(1f, valF * 1.4f)
                if (score > bestScore) {
                    bestScore = score
                    best = p
                }
            }
            if (bestScore < 0.25f) {
                val avg = Color.rgb((r / (n * n)).toInt(), (g / (n * n)).toInt(), (b / (n * n)).toInt())
                Color.colorToHSV(avg, hsv)
                hsv[2] = maxOf(hsv[2], 0.55f)
                return Color.HSVToColor(hsv)
            }
            Color.colorToHSV(best, hsv)
            hsv[1] = minOf(1f, hsv[1] * 1.1f)
            hsv[2] = maxOf(0.5f, hsv[2])
            return Color.HSVToColor(hsv)
        }
    }
}
