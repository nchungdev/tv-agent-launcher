package com.gathin.tvhome.feature.main.widget

import android.util.TypedValue
import com.gathin.tvhome.feature.agent.data.AgentEventHub
import com.gathin.tvhome.feature.smarthome.data.HassClient
import com.gathin.tvhome.feature.smarthome.widget.HomeTile
import com.gathin.tvhome.feature.smarthome.widget.HomeTiles
import com.gathin.tvhome.feature.settings.data.AgentHomeConfig
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextClock
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gathin.tvhome.Contract
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.settings.data.Config
import com.gathin.tvhome.feature.watching.data.ContinueWatching
import com.gathin.tvhome.feature.watching.data.WatchItem
import com.gathin.tvhome.feature.watching.ui.PosterColumn
import com.gathin.tvhome.feature.watching.ui.WatchFormat
import com.gathin.tvhome.feature.agent.coordinator.AgentActionExecutor
import com.gathin.tvhome.feature.system.data.AirQuality
import com.gathin.tvhome.feature.system.data.Weather
import com.gathin.tvhome.feature.main.widget.HomeGrid
import com.gathin.tvhome.core.view.ShapeBadge
import com.gathin.tvhome.feature.main.widget.WidgetCard
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.core.design.ShelfHighlight
import com.gathin.tvhome.core.util.LunarCalendar
import com.gathin.tvhome.core.motion.Motion
import com.google.android.material.color.utilities.Blend
import com.google.android.material.color.utilities.Hct
import java.time.LocalDate
import java.util.ArrayList
import java.util.Collections
import java.util.function.IntConsumer


@SuppressLint("RestrictedApi")
class WidgetShelf(
    private val activity: Activity,
    private val shelf: View,
    animations: Boolean,
    reducedMotion: Boolean,
    advancedEffects: Boolean,
    private val host: Host
) {

    interface Host {
        fun showMenu(anchor: View, items: List<MenuItem>)
        fun slotsChanged()
        fun carryChanged()
        fun startEditing(): Boolean
        fun openSmartHome()
        fun openHomeAssistant()
    }

    class MenuItem @JvmOverloads constructor(
        @JvmField val icon: Int,
        @JvmField val title: String,
        @JvmField val action: Runnable,
        @JvmField val value: String? = null
    )

    private val density: Float = activity.resources.displayMetrics.density
    private val prefs: SharedPreferences = activity.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
    private var homeTiles: List<HomeTile> = HomeTiles.load(prefs)
    private val tvModes = TvModeTiles(activity, prefs) { paintHaConnection() }
    private var modesShown = false
    private val configCity: String = activity.getString(R.string.weather_city).trim()
    private val main = Handler(Looper.getMainLooper())
    private val motion: Boolean = animations && !reducedMotion
    private var enabled: Boolean = true

    private val cards: Array<WidgetCard>
    private val addCard: WidgetCard
    private val addLabel: TextView
    private val addIcon: ImageView
    private val highlight: ShelfHighlight

    private val order = ArrayList<String>()
    private val slots = ArrayList<WidgetCard>()
    private var lastGrid: RecyclerView? = null
    private var lastColumns = MAX_CARDS
    private var lastDockTop = 0

    private var editing = false
    private var carried: String? = null
    private var onDown: IntConsumer? = null

    private var cellOrigin = 0
    private var cellPitch = 0
    private var cellWidth = 0
    private var tilePitch = 0
    private var dockLeft = 0
    private var dockTiles = 1
    private var headerUnderlap = 0

    private val weatherPlace: TextView
    private val weatherTemp: TextView
    private val weatherDetail: TextView
    private val weatherIcon: ShapeBadge

    private val tiles: List<View>
    private val tileIcons: List<TextView>
    private val tileValues: List<TextView>
    private val tileCaptions: List<TextView>

    private val airLabel: TextView
    private val airValue: TextView
    private val airDetail: TextView
    private val airBadge: ShapeBadge

    private val clockDay: TextView
    private val clockTime: TextClock
    private val clockAmPm: TextClock
    private val clockDate: TextView
    private val clockLunar: TextView
    private var clockSeconds = false

    private val contLabel: TextView
    private val contCount: TextView
    private val contTitle: TextView
    private val contSubtitle: TextView
    private val contPoster: ImageView
    private val contProgress: ProgressBar
    private val contRemaining: TextView
    private val contAppIcon: ImageView
    private val watching = ContinueWatching(activity)
    private var continueDock: ((WatchItem?, Bitmap?) -> Unit)? = null
    private var watchIndex = 0
    private var continueShown = false
    private var expandedHub = false
    private var paintedWatchItem: WatchItem? = null
    private var watchRotatedAt = 0L

    private val lunarLabel: TextView
    private val lunarDate: TextView
    private val holiday: TextView

    private var palette: Palette = Palette.currentOrBaseline()
    private var started = false

    private var report: Weather.Report? = null
    private var weatherOffline = false

    private var air: AirQuality.Report? = null
    private var airFetching = false
    private var airTomorrow = false
    private var airFailure = 0L

    private var holidayStep = 0
    private var weatherPage = 0

    init {
        cards = arrayOf(
            shelf.findViewById(R.id.widgetClock),
            shelf.findViewById(R.id.widgetWeather),
            shelf.findViewById(R.id.widgetContinue),
            shelf.findViewById(R.id.widgetAir),
            shelf.findViewById(R.id.widgetCalendar)
        )
        for (c in cards) {
            c.setMotion(motion)
            c.setFlat(true)
        }
        cards[0].isFocusable = false
        cards[0].isClickable = false
        cards[2].setGlass(true)
        cards[2].setFlat(false)

        addCard = shelf.findViewById(R.id.widgetAdd)
        addCard.setMotion(motion)
        addCard.setGhost(true)
        addLabel = addCard.findViewById(R.id.widgetAddLabel)
        addIcon = addCard.findViewById(R.id.widgetAddIcon)
        addCard.setOnClickListener { showAddMenu() }

        highlight = ShelfHighlight.attach(shelf as ViewGroup, animations, reducedMotion, advancedEffects)

        clockDay = cards[0].findViewById(R.id.clockDay)
        clockTime = cards[0].findViewById(R.id.clockTime)
        clockAmPm = cards[0].findViewById(R.id.clockAmPm)
        clockDate = cards[0].findViewById(R.id.clockDate)
        clockLunar = cards[0].findViewById(R.id.clockLunar)

        weatherPlace = cards[1].findViewById(R.id.weatherPlace)
        weatherTemp = cards[1].findViewById(R.id.weatherTemp)
        weatherDetail = cards[1].findViewById(R.id.weatherDetail)
        weatherIcon = cards[1].findViewById(R.id.weatherIcon)
        weatherIcon.setFlatBadge(true)

        tiles = listOf(R.id.tileTemp, R.id.tileLight, R.id.tileSwitch)
            .map { cards[0].findViewById<View>(it) }
        tileIcons = listOf(R.id.tileTempIcon, R.id.tileLightIcon, R.id.tileSwitchIcon)
            .map { cards[0].findViewById<TextView>(it) }
        tileValues = listOf(R.id.tileTempValue, R.id.tileLightValue, R.id.tileSwitchValue)
            .map { cards[0].findViewById<TextView>(it) }
        tileCaptions = listOf(R.id.tileTempCaption, R.id.tileLightCaption, R.id.tileSwitchCaption)
            .map { cards[0].findViewById<TextView>(it) }

        contLabel = cards[2].findViewById(R.id.contLabel)
        contCount = cards[2].findViewById(R.id.contCount)
        contTitle = cards[2].findViewById(R.id.contTitle)
        contSubtitle = cards[2].findViewById(R.id.contSubtitle)
        contPoster = cards[2].findViewById(R.id.contPoster)
        contProgress = cards[2].findViewById(R.id.contProgress)
        contRemaining = cards[2].findViewById(R.id.contRemaining)
        contAppIcon = cards[2].findViewById(R.id.contAppIcon)
        contAppIcon.clipToOutline = true
        shelf.findViewById<View>(R.id.smartHomeTiles)?.addOnLayoutChangeListener {
                _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (top != oldTop || bottom != oldBottom) realignContinue()
        }
        cards[2].findViewById<View>(R.id.contPosterFrame).clipToOutline = true

        airLabel = cards[3].findViewById(R.id.airLabel)
        airValue = cards[3].findViewById(R.id.airValue)
        airDetail = cards[3].findViewById(R.id.airDetail)
        airBadge = cards[3].findViewById(R.id.airBadge)
        airBadge.setFlatBadge(true)

        lunarLabel = cards[4].findViewById(R.id.lunarLabel)
        lunarDate = cards[4].findViewById(R.id.lunarDate)
        holiday = cards[4].findViewById(R.id.holiday)

        setupCardInteractions()
        loadOrder()
        shelf.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    fun enabled(): Boolean = enabled

    fun setEnabled(on: Boolean) {
        enabled = on
        shelf.visibility = if (on) View.VISIBLE else View.GONE
        if (!on) stop()
    }

    fun setOnDown(listener: IntConsumer) {
        onDown = listener
    }

    fun focusFromDock(position: Int): Boolean {
        if (!enabled || slots.isEmpty()) return false
        val column = dockColumnUnderCard(position)
        val target = column.coerceIn(0, slots.size - 1)
        val card = slots[target]
        card.requestFocus()
        return true
    }

    fun cardUnderHeaderButtons(): Int {
        if (!enabled || slots.isEmpty()) return R.id.grid
        val card = slots[1.coerceAtMost(slots.size - 1)]
        return card.id
    }

    fun cardUnderHeaderBot(): Int {
        if (!enabled || slots.isEmpty()) return R.id.grid
        return slots[0].id
    }

    fun focusFromHeader(left: Boolean = true): Boolean {
        if (!enabled || slots.isEmpty()) return false
        if (left) {
            val first = tiles.firstOrNull { it.isShown && it.isFocusable }
            if (first != null && first.requestFocus()) return true
            return slots[0].requestFocus()
        } else {
            val target = if (slots.size > 1) slots[1] else slots[0]
            return target.requestFocus()
        }
    }

    private fun focusHeaderOrTopbar(fromView: View): Boolean {
        val bot = activity.findViewById<View>(R.id.duinchBot)
        val topButtons = activity.findViewById<ViewGroup>(R.id.topButtons)

        val loc = IntArray(2)
        fromView.getLocationOnScreen(loc)
        val viewCenterX = loc[0] + fromView.width / 2

        val botLoc = IntArray(2)
        bot?.getLocationOnScreen(botLoc)
        val botCenterX = if (bot != null && bot.isShown) botLoc[0] + bot.width / 2 else -1

        var bestButton: View? = null
        var minButtonDist = Int.MAX_VALUE

        if (topButtons != null && topButtons.isShown) {
            for (i in 0 until topButtons.childCount) {
                val child = topButtons.getChildAt(i)
                if (child.isShown && child.isFocusable) {
                    val childLoc = IntArray(2)
                    child.getLocationOnScreen(childLoc)
                    val childCenterX = childLoc[0] + child.width / 2
                    val dist = Math.abs(childCenterX - viewCenterX)
                    if (dist < minButtonDist) {
                        minButtonDist = dist
                        bestButton = child
                    }
                }
            }
        }

        val botDist = if (botCenterX >= 0) Math.abs(botCenterX - viewCenterX) else Int.MAX_VALUE

        if (bot != null && bot.isShown && botDist <= minButtonDist) {
            if (bot.requestFocus()) return true
        }

        if (bestButton != null && bestButton.requestFocus()) return true
        return bot?.requestFocus() ?: false
    }

    private fun setupCardInteractions() {
        tiles.forEachIndexed { index, tile ->
            tile.setOnClickListener { runTile(index) }
            tile.setOnLongClickListener {
                showEditMenu(CLOCK)
                true
            }
        }
        cards[1].setOnClickListener {
            cards[1].turn {
                weatherPage = (weatherPage + 1) % WEATHER_PAGES
                prefs.edit().putInt(WEATHER_PAGE_KEY, weatherPage).apply()
                paintWeather()
            }
        }
        cards[2].setOnClickListener { launchWatching() }
        cards[2].setOnFocusChangeListener { _, focused ->
            if (!focused) paintContinue()
        }
        cards[2].setOnKeyListener { _, code, event ->
            val step = when (code) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> 1
                KeyEvent.KEYCODE_DPAD_LEFT -> if (watchIndex > 0) -1 else 0
                else -> 0
            }
            val count = watching.items.size
            if (step == 0 || count < 2 || !expandedHub) return@setOnKeyListener false
            if (event.action == KeyEvent.ACTION_DOWN) stepWatching(step, count)
            true
        }
        cards[3].setOnClickListener {
            cards[3].turn {
                airTomorrow = !airTomorrow
                prefs.edit().putInt(AIR_PAGE_KEY, if (airTomorrow) 1 else 0).apply()
                paintAir()
            }
        }
        cards[4].setOnClickListener {
            cards[4].turn {
                holidayStep++
                paintCalendar()
            }
        }
        for (id in ALL) {
            val card = card(id) ?: continue
            card.setOnLongClickListener {
                if (id == CONTINUE) {
                    com.gathin.tvhome.feature.watching.ui.WatchNextMenu.show(activity,
                        Config.load(activity), watching, paintedWatchItem) {
                        cards[0].requestFocus()
                    }
                } else showEditMenu(id)
                true
            }
        }
        val keyListener = View.OnKeyListener { v, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@OnKeyListener false
            if (editing && carried != null) return@OnKeyListener false
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (focusHeaderOrTopbar(v)) return@OnKeyListener true
            }
            if (expandedHub && keyCode == KeyEvent.KEYCODE_DPAD_DOWN && v === cards[0]) {
                val firstTile = tiles.firstOrNull { it.isShown && it.isFocusable }
                if (firstTile != null && firstTile.requestFocus()) return@OnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && onDown != null) {
                val idx = slots.indexOf(ownerOf(v)).coerceAtLeast(0)
                onDown?.accept(dockColumnUnderCard(idx))
                return@OnKeyListener true
            }
            false
        }
        for (c in allCards()) c.setOnKeyListener(keyListener)
        for (tile in tiles) tile.setOnKeyListener(keyListener)
    }

    private fun openSmartHomeCard() {
        val connection = com.gathin.tvhome.feature.settings.data.AgentHomeConfig(activity)
        val configured = connection.homeAssistantConfigured
        if (configured) host.openSmartHome() else host.openHomeAssistant()
    }

    private fun runTile(index: Int) {
        if (tvModes.activate(index)) {
            tileValues[index].text = "…"
            return
        }
        val tile = homeTiles[index]
        val connection = AgentHomeConfig(activity)
        val configured = connection.homeAssistantConfigured
        if (!configured || tile.action.isEmpty()) {
            openSmartHomeCard()
            return
        }
        tileValues[index].text = "…"
        Thread {
            runCatching {
                HassClient(connection.homeAssistantUrl, connection.homeAssistantToken).use {
                    it.callService(tile.domain, tile.service, tile.entities)
                }
            }
            main.postDelayed({ refreshHaData(true) }, TILE_SETTLE_MS)
        }.start()
    }

    private fun launchWatching() {
        val items = watching.items
        val item = if (expandedHub) paintedWatchItem
        else items.getOrNull(watchIndex % maxOf(1, items.size))
        if (item == null) {
            refreshWatching(true)
            return
        }
        com.gathin.tvhome.feature.watching.ui.WatchLauncher.open(activity, item)
    }

    fun continueFeed(): ContinueWatching = watching

    private fun refreshWatching(force: Boolean) {
        if (force) watching.refresh() else watching.tick()
    }

    private fun stepWatching(step: Int, count: Int) {
        watchIndex = (watchIndex + step + count) % count
        watchRotatedAt = System.currentTimeMillis()
        paintedWatchItem = null
        paintContinue()
    }

    private fun rotateWatching() {
        if (continueDock != null) return

        val count = watching.items.size
        val now = System.currentTimeMillis()
        if (count < 2 || now - watchRotatedAt < WATCH_ROTATE_MS) return
        watchRotatedAt = now
        watchIndex = (watchIndex + 1) % count
        paintContinue()
    }

    private fun paintContinue() {
        val items = watching.items
        val dockItem = items.firstOrNull()
        val dockVisible = if (expandedHub) null else dockItem
        continueDock?.invoke(dockVisible, watching.poster(dockVisible?.posterUri))
        if (expandedHub && cards[2].hasFocus() && paintedWatchItem != null) return
        val item = items.getOrNull(watchIndex)
        paintedWatchItem = item
        val darkP = Palette.dark(palette)
        contTitle.setTextColor(0xFFFFFFFF.toInt())
        contLabel.setTextColor(darkP.onSurfaceVariant)
        contSubtitle.setTextColor(darkP.onSurfaceVariant)
        contCount.setTextColor(0xFFFFFFFF.toInt())
        contRemaining.setTextColor(0xFFFFFFFF.toInt())
        cards[2].findViewById<View>(R.id.contPosterFrame)?.let { frame ->
            (frame.background as? GradientDrawable)?.setColor(
                Palette.alpha(darkP.surfaceContainerHighest, 0x66)
            )
        }
        contProgress.progressTintList = ColorStateList.valueOf(darkP.primary)
        contProgress.progressBackgroundTintList =
            ColorStateList.valueOf(PROGRESS_TRACK)
        if ((item != null) != continueShown) {
            continueShown = item != null
            lastGrid?.let { align(it, lastColumns, lastDockTop) }
            host.slotsChanged()
        }
        if (item == null) return
        contLabel.text = continueLabel(item)
        val appIcon = WatchFormat.appIcon(activity, item.packageName)
        contAppIcon.setImageDrawable(appIcon)
        contAppIcon.visibility = if (appIcon == null) View.GONE else View.VISIBLE
        contCount.text = WatchFormat.pageDots(watchIndex, items.size)
        contTitle.text = item.title
        val subtitle = WatchFormat.subtitle(activity, item)
        val cleanSubtitle = if (subtitle.contains("<")) {
            android.text.Html.fromHtml(subtitle, android.text.Html.FROM_HTML_MODE_LEGACY).trim()
        } else subtitle.trim()
        contSubtitle.text = cleanSubtitle
        contSubtitle.visibility = if (cleanSubtitle.isBlank()) View.GONE else View.VISIBLE
        paintPoster(item)
        val remaining = WatchFormat.remaining(activity, item.remainingMs)
        contRemaining.text = remaining
        contRemaining.visibility = if (remaining == null) View.GONE else View.VISIBLE
        val known = item.progress > 0f || item.durationMs > 0 || item.remainingMs > 0
        contProgress.visibility = if (known) View.VISIBLE else View.INVISIBLE
        contProgress.progress = Math.round(item.progress * 1000)
    }

    private fun paintPoster(item: WatchItem) {
        val poster = watching.poster(item.posterUri)
        if (poster != null) {
            contPoster.scaleType = ImageView.ScaleType.CENTER_CROP
            contPoster.setImageBitmap(poster)
            return
        }
        contPoster.scaleType = ImageView.ScaleType.FIT_CENTER
        contPoster.setImageDrawable(WatchFormat.appArt(activity, item.packageName))
    }

    private fun continueLabel(item: WatchItem): String {
        val label = activity.getString(R.string.widget_continue)
        val app = item.appLabel
        if (app.isBlank() || app == item.packageName) return label
        return "$label · $app"
    }

    private fun allCards(): Array<WidgetCard> = arrayOf(
        cards[0], cards[1], cards[2], cards[3], cards[4], addCard
    )

    private fun card(id: String): WidgetCard? {
        for (i in ALL.indices) if (ALL[i] == id) return cards[i]
        return null
    }

    private fun span(card: WidgetCard): Int {
        if (expandedHub && (card === cards[0] || card === cards[2])) return 2
        if (card === cards[0]) {
            return minOf(if (continueShown && continueDock == null) 3 else 4, slotsLimit())
        }
        return 1
    }

    fun setContinueDock(listener: (WatchItem?, Bitmap?) -> Unit) {
        continueDock = listener
        paintContinue()
        lastGrid?.let { align(it, lastColumns, lastDockTop) }
    }

    fun continueItems(): List<WatchItem> = watching.items

    private fun has(id: String): Boolean = order.contains(id)

    private fun loadOrder() {
        order.clear()
        val defaultOrder = "$CLOCK,$CONTINUE"
        val saved = prefs.getString(ORDER_KEY, defaultOrder) ?: defaultOrder
        for (raw in saved.split(",")) {
            var id = raw.trim()
            if (id == "now_playing") id = AIR
            if (id == "home_assistant") id = CONTINUE
            if (id == "smarthome" || id == "system") continue
            if (card(id) != null && !order.contains(id)) order.add(id)
        }
        order.remove(CLOCK)
        order.remove(CONTINUE)
        order.add(0, CLOCK)
        order.add(1, CONTINUE)
        order.retainAll(listOf(CLOCK, CONTINUE))
    }

    private fun setOrder(next: List<String>, focusId: String?, fallbackColumn: Int, removing: String?) {
        val leaving = if (removing == null) null else card(removing)
        if (!motion || leaving == null) {
            applyOrder(next, focusId, fallbackColumn)
            return
        }
        leaving.animate().cancel()
        leaving.animate().alpha(0f).scaleX(ENTER_SCALE).scaleY(ENTER_SCALE)
            .setInterpolator(Motion.emphasizedAccelerate()).setDuration(Motion.EMPHASIZED_ACCELERATE_MS)
            .withEndAction {
                leaving.alpha = 1f
                leaving.scaleX = 1f
                leaving.scaleY = 1f
                applyOrder(next, focusId, fallbackColumn)
            }.start()
    }

    private fun applyOrder(next: List<String>, focusId: String?, fallbackColumn: Int) {
        val before = HashMap<WidgetCard, Int>()
        for (c in slots) before[c] = c.left
        order.clear()
        order.addAll(next)
        prefs.edit().putString(ORDER_KEY, order.joinToString(",")).apply()
        if (started) {
            stop()
            start()
        }
        val grid = lastGrid
        if (grid != null) align(grid, lastColumns, lastDockTop)
        host.slotsChanged()
        if (motion) {
            shelf.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    shelf.viewTreeObserver.removeOnPreDrawListener(this)
                    for (c in slots) {
                        val from = before[c]
                        if (from == null) {
                            c.alpha = 0f
                            c.scaleX = ENTER_SCALE
                            c.scaleY = ENTER_SCALE
                            Motion.DEFAULT_SPATIAL.drive(c.animate().alpha(1f).scaleX(1f).scaleY(1f)).start()
                        } else if (from != c.left) {
                            c.translationX = (from - c.left).toFloat()
                            Motion.DEFAULT_SPATIAL.drive(c.animate().translationX(0f)).start()
                        }
                    }
                    return true
                }
            })
        }
        shelf.post {
            var target = if (focusId != null) card(focusId) else null
            if (target == null || !slots.contains(target)) {
                target = if (slots.isEmpty()) null else slots[fallbackColumn.coerceIn(0, slots.size - 1)]
            }
            if (target != null) {
                target.requestFocus()
                highlight.follow(target)
            }
        }
    }

    private fun showAddMenu() {
        val items = ArrayList<MenuItem>()
        for (id in ALL) {
            if (has(id)) continue
            items.add(MenuItem(icon(id), activity.getString(name(id)), Runnable {
                val next = ArrayList(order)
                next.add(id)
                setOrder(next, id, next.size - 1, null)
            }))
        }
        if (items.isNotEmpty()) host.showMenu(addCard, items)
    }

    private fun showEditMenu(id: String) {
        val at = order.indexOf(id)
        if (at < 0) return
        val items = ArrayList<MenuItem>()
        if (minOf(order.size, slotsLimit()) > 1) {
            items.add(MenuItem(R.drawable.ic_move, activity.getString(R.string.tile_move), Runnable {
                if (!host.startEditing()) return@Runnable
                val card = card(id)
                card?.requestFocus()
                carried = id
                highlight.setLifted(true)
                host.carryChanged()
            }))
        }
        items.add(MenuItem(R.drawable.ic_settings_outline, activity.getString(R.string.widget_settings), Runnable { showSettingsMenu(id) }))
        val c = card(id)
        if (c != null) host.showMenu(c, items)
    }

    private fun move(id: String, by: Int) {
        val next = ArrayList(order)
        val at = next.indexOf(id)
        if (at < 0 || at + by < 0 || at + by >= minOf(next.size, slotsLimit())) return
        Collections.swap(next, at, at + by)
        setOrder(next, id, at + by, null)
    }

    fun setEditing(on: Boolean) {
        editing = on
        if (!on) drop()
        highlight.setColor(if (on) palette.tertiary else palette.primary)
    }

    fun carriedLabel(): String? = carried?.let { activity.getString(name(it)) }

    fun handleEditKey(event: KeyEvent, focused: View?): Boolean {
        if (!editing || focused !is WidgetCard || focused.parent !== shelf) return false
        val down = event.action == KeyEvent.ACTION_DOWN
        val code = event.keyCode
        val ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER
        val back = code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE
        val curCarried = carried
        if (curCarried != null) {
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (down) move(curCarried, if (code == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
                return true
            }
            if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) return true
            if (ok || back) {
                if (!down) drop()
                return true
            }
            return false
        }
        val id = idOf(focused)
        if (ok && id != null) {
            if (!down && event.repeatCount == 0) {
                carried = id
                highlight.setLifted(true)
                host.carryChanged()
            }
            return true
        }
        return false
    }

    private fun drop() {
        if (carried != null) {
            carried = null
            highlight.setLifted(false)
            host.carryChanged()
        }
    }

    private fun idOf(view: View): String? {
        val card = ownerOf(view) ?: return null
        for (i in ALL.indices) if (cards[i] === card) return ALL[i]
        return null
    }

    private fun ownerOf(view: View): WidgetCard? {
        var current: View? = view
        while (current != null) {
            if (current is WidgetCard) return current
            current = current.parent as? View
        }
        return null
    }

    private fun paintTiles() {
        HomeTileAppearance.style(tiles, Palette.dark(palette))
        tvModes.tiles()?.let { paintModes(it) }
    }

    private fun city(): String = prefs.getString(CITY_KEY, configCity) ?: configCity

    private fun fahrenheit(): Boolean = "f" == prefs.getString(UNIT_KEY, "c")

    private fun tetOnly(): Boolean = prefs.getBoolean(TET_ONLY_KEY, false)

    fun agentEnabled(): Boolean {
        val defaultOn = activity.resources.getBoolean(R.bool.agent_widget_enabled)
        return prefs.getBoolean(AGENT_WIDGET_KEY, defaultOn)
    }

    private fun cityName(query: String): String {
        for (i in CITY_QUERIES.indices) {
            if (CITY_QUERIES[i] == query) return CITY_NAMES[i] ?: activity.getString(R.string.widget_city_auto)
        }
        return if (query.contains("|")) query.substring(0, query.indexOf('|')) else query
    }

    private fun showSettingsMenu(id: String) {
        val anchor = card(id) ?: return
        val items = ArrayList<MenuItem>()
        when (id) {
            CLOCK -> {
                val formats = arrayOf("24", "18:58")
                items.add(setting(R.drawable.ic_timer, R.string.widget_clock, formats[if (clockSeconds) 1 else 0]) {
                    showChoices(anchor, formats, if (clockSeconds) 1 else 0) { i ->
                        clockSeconds = i == 1
                        paintClockDate()
                    }
                })
            }
            WEATHER -> {
                items.add(setting(R.drawable.ic_location, R.string.setting_location, cityName(city())) { showCityMenu() })
                items.add(setting(R.drawable.ic_thermostat, R.string.setting_unit, if (fahrenheit()) "°F" else "°C") {
                    showChoices(anchor, arrayOf("°C", "°F"), if (fahrenheit()) 1 else 0) { i ->
                        prefs.edit().putString(UNIT_KEY, if (i == 1) "f" else "c").apply()
                        paintWeather()
                    }
                })
                items.add(opensOn(anchor, WEATHER_PAGE_KEY, WEATHER_PAGE_NAMES) { i ->
                    weatherPage = i
                    paintWeather()
                })
            }
            AIR -> {
                items.add(setting(R.drawable.ic_location, R.string.setting_location_shared, cityName(city())) { showCityMenu() })
                items.add(opensOn(anchor, AIR_PAGE_KEY, AIR_PAGE_NAMES) { i ->
                    airTomorrow = i == 1
                    paintAir()
                })
            }
            CALENDAR -> {
                val countdown = arrayOf(activity.getString(R.string.countdown_next), activity.getString(R.string.countdown_tet))
                items.add(setting(R.drawable.ic_timer, R.string.setting_countdown, countdown[if (tetOnly()) 1 else 0]) {
                    showChoices(anchor, countdown, if (tetOnly()) 1 else 0) { i ->
                        prefs.edit().putBoolean(TET_ONLY_KEY, i == 1).apply()
                        holidayStep = 0
                        paintCalendar()
                    }
                })
            }
            else -> {
                // Home Assistant
            }
        }
        host.showMenu(anchor, items)
    }

    private fun setting(icon: Int, name: Int, value: String, choose: Runnable): MenuItem {
        return MenuItem(icon, activity.getString(name), choose, value)
    }

    private fun opensOn(anchor: View, key: String, pageNames: IntArray, show: IntConsumer): MenuItem {
        val names = Array(pageNames.size) { activity.getString(pageNames[it]) }
        val current = prefs.getInt(key, 0).coerceAtMost(names.size - 1)
        return setting(R.drawable.ic_swap_vert, R.string.setting_opens, names[current]) {
            showChoices(anchor, names, current) { i ->
                prefs.edit().putInt(key, i).apply()
                show.accept(i)
            }
        }
    }

    private fun showCityMenu() {
        com.gathin.tvhome.feature.settings.widget.WeatherLocationPicker.choose(activity,
            com.gathin.tvhome.feature.settings.data.Config.load(activity)) {
            onWeatherLocationChanged?.invoke()
            if (has(AIR)) refreshAir(true)
        }
    }

    var onWeatherLocationChanged: (() -> Unit)? = null

    private fun showChoices(anchor: View, labels: Array<String>, selected: Int, choose: IntConsumer) {
        val items = ArrayList<MenuItem>()
        for (i in labels.indices) {
            val index = i
            items.add(MenuItem(if (i == selected) R.drawable.ic_radio_on else R.drawable.ic_radio_off, labels[i], Runnable { choose.accept(index) }))
        }
        host.showMenu(anchor, items)
    }

    private fun measureDock(grid: RecyclerView, columns: Int): Boolean {
        val parent = shelf.parent as? View
        val parentWidth = if (parent != null && parent.width > 0) parent.width
            else activity.resources.displayMetrics.widthPixels
        if (parentWidth <= 0) return false
        val sideMargin = Math.round(56 * density)
        val availableWidth = parentWidth - 2 * sideMargin
        val cells = slotsLimit()
        val gap = Math.round(16 * density)
        cellWidth = (availableWidth - gap * (cells - 1)) / cells
        cellPitch = cellWidth + gap
        cellOrigin = sideMargin
        tilePitch = availableWidth / maxOf(1, columns)
        dockLeft = sideMargin
        dockTiles = maxOf(1, columns)
        return true
    }

    private fun measureFallback(columns: Int): Boolean {
        return measureDock(lastGrid ?: return false, columns)
    }

    private fun tileBounds(grid: RecyclerView, position: Int): Rect? {
        val holder = grid.findViewHolderForAdapterPosition(position)
        val tile = holder?.itemView?.findViewById<View>(R.id.card) ?: return null
        if (tile.width == 0) return null
        val bounds = Rect(0, 0, tile.width, tile.height)
        (shelf.parent as ViewGroup).offsetDescendantRectToMyCoords(tile, bounds)
        return bounds
    }

    private fun dockColumnUnderCard(card: Int): Int {
        if (tilePitch <= 0) return card
        val centre = cellOrigin + card * cellPitch + cellWidth / 2
        return Math.floorDiv(centre - dockLeft, tilePitch).coerceIn(0, dockTiles - 1)
    }

    private fun slotsLimit(): Int = minOf(MAX_CARDS, lastColumns)

    private fun name(id: String): Int {
        return when (id) {
            CLOCK -> R.string.widget_clock
            WEATHER -> R.string.weather
            CONTINUE -> R.string.widget_continue
            AIR -> R.string.air_quality
            else -> R.string.lunar_calendar
        }
    }

    private fun icon(id: String): Int {
        return when (id) {
            CLOCK -> R.drawable.ic_timer
            WEATHER -> R.drawable.ic_w_partly_day
            CONTINUE -> R.drawable.ic_tv
            AIR -> R.drawable.ic_air
            else -> R.drawable.ic_calendar
        }
    }

    private fun paintClockDate() {
        val today = LocalDate.now()
        val l = LunarCalendar.toLunar(today)
        val canChi = LunarCalendar.canChi(l.year)
        val lunarStr = if (l.leap) "${l.day}/${l.month} (nhuận)" else "${l.day}/${l.month}"

        val weekdayPattern = android.text.format.DateFormat.getBestDateTimePattern(
            java.util.Locale.getDefault(),
            "EEEEdMMMM"
        )
        val darkP = Palette.dark(palette)
        clockDay.text = today.format(java.time.format.DateTimeFormatter.ofPattern(weekdayPattern))
        clockDay.setTextColor(0xFFFFFFFF.toInt())
        clockLunar.setTextColor(darkP.primary)
        clockLunar.text = "Âm lịch $lunarStr  ·  $canChi"

        val connection = com.gathin.tvhome.feature.settings.data.AgentHomeConfig(activity)
        val haEvent = connection.cachedHaCalendarEvent()
        if (!haEvent.isNullOrBlank()) {
            clockDate.text = haEvent
            clockDate.setTextColor(darkP.onSurfaceVariant)
        } else {
            val upcoming = LunarCalendar.upcoming(today)
            if (upcoming.isNotEmpty()) {
                val u = upcoming[0]
                val name = activity.getString(holidayName(u.holiday))
                clockDate.text = when (u.days.toInt()) {
                    0 -> activity.getString(R.string.holiday_today, name)
                    1 -> activity.getString(R.string.holiday_tomorrow, name)
                    else -> activity.getString(R.string.holiday_in_days, name, u.days.toInt())
                }
                clockDate.setTextColor(darkP.onSurfaceVariant)
            } else {
                clockDate.text = ""
            }
        }

        clockTime.format24Hour = if (clockSeconds) "HH:mm:ss" else "HH:mm"
        clockTime.format12Hour = if (clockSeconds) "h:mm:ss" else "h:mm"

    }

    private fun paintHaConnection() {
        tvModes.tiles()?.let { return paintModes(it) }
        if (modesShown) {
            TvModeAppearance.reset(smartHomeGroup(), tiles, tileCaptions, Palette.dark(palette))
            modesShown = false
        }
        val connection = AgentHomeConfig(activity)
        val configured = connection.homeAssistantConfigured
        val shown = withOutdoorFallback(if (configured) homeTiles else HomeTiles.EMPTY)
        shown.forEachIndexed { index, tile ->
            paintTile(index, tile.icon, tile.value, tile.caption)
        }
    }

    private fun paintModes(modes: List<TvModeTiles.Tile>) {
        val darkP = Palette.dark(palette)
        modes.forEachIndexed { index, mode ->
            paintTile(index, mode.icon, mode.title, "")
            tileValues[index].setTextSize(TypedValue.COMPLEX_UNIT_SP, MODE_TITLE_SP)
        }
        val active = modes.indexOfFirst { it.active }
        TvModeAppearance.apply(smartHomeGroup(), tiles, tileCaptions, active, darkP)
        modesShown = true
    }

    private fun smartHomeGroup(): View = shelf.findViewById(R.id.smartHomeTiles)

    private fun withOutdoorFallback(tiles: List<HomeTile>): List<HomeTile> {
        if (tiles[0].value != "—") return tiles
        val report = this.report ?: return tiles
        val outdoor = HomeTile("🌡", "${Math.round(report.temp)}°C", "Ngoài trời")
        return listOf(outdoor) + tiles.drop(1)
    }

    private fun paintTile(index: Int, icon: String, value: String, caption: String) {
        val darkP = Palette.dark(palette)
        HomeTileAppearance.icon(tileIcons[index], icon, Palette.dark(palette).primary)
        tileValues[index].text = value
        tileValues[index].setTextSize(TypedValue.COMPLEX_UNIT_SP, TILE_VALUE_SP)
        tileValues[index].setTextColor(0xFFFFFFFF.toInt())
        tileCaptions[index].text = caption
        tileCaptions[index].setTextColor(darkP.onSurfaceVariant)
    }

    private var fetchingHa = false
    private var lastHaFetch = 0L

    private fun refreshHaData(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastHaFetch < 60_000L) return
        val connection = com.gathin.tvhome.feature.settings.data.AgentHomeConfig(activity)
        if (!connection.homeAssistantConfigured) return
        if (fetchingHa) return
        fetchingHa = true
        lastHaFetch = now

        Thread {
            try {
                com.gathin.tvhome.feature.smarthome.data.HassClient(
                    connection.homeAssistantUrl,
                    connection.homeAssistantToken
                ).use { client ->
                    val event = client.nextCalendarEvent()
                    if (event != null) {
                        connection.saveHaCalendarEvent(event)
                    } else {
                        connection.clearHaCalendarEvent()
                    }

                    val states = client.states()
                    val picked = HomeTiles.pick(states, connection.homeAssistantTemperatureEntity)
                    HomeTiles.save(prefs, picked)
                    main.post { homeTiles = picked }
                    val entityId = connection.homeAssistantTemperatureEntity
                    val target = if (entityId.isNotBlank()) {
                        states.find { it.id == entityId }
                    } else {
                        states.find {
                            it.indoorSensor && it.state != "unavailable" && it.state != "unknown"
                        } ?: states.find {
                            it.id.startsWith("climate.") && it.state != "unavailable" &&
                                it.state != "unknown"
                        } ?: states.find { it.indoorSensor }
                        ?: states.find { it.id.startsWith("climate.") }
                    }
                    if (target != null) {
                        val temp = target.currentTemperature ?: target.temperature
                        val display = if (temp != null) {
                            "${temp.toInt()}°C"
                        } else if (target.state.toDoubleOrNull() != null) {
                            "${target.state.toDouble().toInt()}°C"
                        } else if (target.state.equals("unavailable", true) ||
                            target.state.equals("unknown", true)
                        ) {
                            "—"
                        } else {
                            target.state
                        }
                        connection.saveHaTemperature(display)
                        val rawName = if (target.name.isNotBlank()) target.name else target.id
                        val cleanName = if (rawName.startsWith("climate.", ignoreCase = true)) {
                            "Điều hòa"
                        } else {
                            rawName
                        }
                        connection.saveHaSensorName(cleanName)
                    }

                    val humidity = states.find {
                        (it.unit == "%" || it.id.contains("humidity", ignoreCase = true) ||
                            it.name.contains("ẩm", ignoreCase = true)) &&
                            it.state.toDoubleOrNull() != null
                    }
                    if (humidity != null && humidity.state.toDoubleOrNull() != null) {
                        connection.saveHaHumidity("${humidity.state.toDouble().toInt()}%")
                    }

                    val onLights = states.count { it.domain == "light" && it.state == "on" }
                    val totalLights = states.count { it.domain == "light" }
                    if (onLights > 0) {
                        connection.saveHaDevicesSummary("💡 $onLights đèn đang bật")
                    } else if (totalLights > 0) {
                        connection.saveHaDevicesSummary("💡 Tất cả đèn đã tắt")
                    } else {
                        connection.saveHaDevicesSummary("💡 Đèn: Sẵn sàng")
                    }

                    val activeClimate = states.find {
                        it.domain == "climate" &&
                            it.state != "off" &&
                            it.state != "unavailable" &&
                            it.state != "unknown"
                    }
                    val activeFan = states.find { it.domain == "fan" && it.state == "on" }
                    val onSwitches = states.count { it.domain == "switch" && it.state == "on" }
                    if (activeClimate != null) {
                        val t = activeClimate.temperature?.toInt()
                        val stateStr = activeClimate.state
                        val climateDetail = "Điều hòa: $stateStr" + if (t != null) " ($t°)" else ""
                        connection.saveHaClimateSummary("❄️ $climateDetail")
                    } else if (activeFan != null) {
                        connection.saveHaClimateSummary("🌀 Quạt: Đang quay")
                    } else if (onSwitches > 0) {
                        connection.saveHaClimateSummary("⚡ $onSwitches công tắc đang bật")
                    } else {
                        connection.saveHaClimateSummary("❄️ Điều hòa: Tắt")
                    }
                }
            } catch (_: Exception) {
            } finally {
                fetchingHa = false
                activity.runOnUiThread {
                    paintClockDate()
                    paintHaConnection()
                }
            }
        }.start()
    }

    fun start() {
        if (!enabled || started) return
        started = true
        paintClockDate()
        paintHaConnection()
        refreshHaData(false)
        tvModes.refresh()
        AgentEventHub.tvModeListener = { main.post { tvModes.refresh() } }
        watching.start {
            watchIndex = 0
            paintContinue()
        }
        paintContinue()
        weatherPage = prefs.getInt(WEATHER_PAGE_KEY, 0) % WEATHER_PAGES
        airTomorrow = prefs.getInt(AIR_PAGE_KEY, 0) == 1
        if (has(CALENDAR)) paintCalendar()
        if (has(AIR)) refreshAir(false)
    }

    fun stop() {
        watching.stop()
        AgentEventHub.tvModeListener = null
        if (!started) return
        started = false
    }

    fun tick() {
        if (!started) return
        paintClockDate()
        paintHaConnection()
        refreshHaData(false)
        refreshWatching(false)
        rotateWatching()
        if (has(CALENDAR)) paintCalendar()
        if (has(AIR)) refreshAir(false)
    }

    fun align(grid: RecyclerView, columns: Int, dockTop: Int) {
        lastGrid = grid
        lastColumns = columns
        lastDockTop = dockTop
        if (!enabled) return
        val header = shelf.rootView.findViewById<View>(R.id.headerRow)
        val wide = HomeHubLayout.expanded(shelf.rootView.width,
            dockTop - (header?.bottom ?: 0), density)
        if (expandedHub != wide) {
            expandedHub = wide
            HomeHubLayout.apply(shelf, wide)
            val item = if (wide) null else watching.items.firstOrNull()
            continueDock?.invoke(item, watching.poster(item?.posterUri))
        }
        slots.clear()
        var usedColumns = 0
        for (id in order) {
            val card = card(id) ?: continue
            if (card === cards[2] && (!continueShown ||
                    (continueDock != null && !expandedHub))) continue
            val cardSpan = span(card)
            if (usedColumns + cardSpan <= MAX_CARDS) {
                slots.add(card)
                usedColumns += cardSpan
            }
        }
        for (c in allCards()) {
            val visibility = if (slots.contains(c)) View.VISIBLE else View.GONE
            if (c.visibility != visibility) c.visibility = visibility
        }
        if (!measureDock(grid, columns)) return

        val inset = Math.round(WidgetCard.REACH_DP * density)
        var moved = false
        var columnIndex = 0
        for (index in slots.indices) {
            val card = slots[index]
            val cardSpan = span(card)
            val lp = card.layoutParams as FrameLayout.LayoutParams
            val left = cellOrigin + columnIndex * cellPitch - inset - shelf.left
            val width = (cardSpan - 1) * cellPitch + cellWidth + 2 * inset
            if (lp.leftMargin != left || lp.width != width) {
                lp.leftMargin = left
                lp.width = width
                card.layoutParams = lp
                moved = true
            }
            columnIndex += cardSpan
        }
        if (moved) shelf.post { for (card in slots) highlight.follow(card) }
        val headerRow = (shelf.parent as? View)?.findViewById<View>(R.id.headerRow)
        val greetingBlock = (shelf.parent as? View)?.findViewById<View>(R.id.greetingBlock)
        val minHeader = Math.round(68 * density)
        val gridUnderlap = if (grid is HomeGrid && grid.visibility == View.VISIBLE) {
            grid.underlap()
        } else 0
        val actualHeader = maxOf(headerRow?.bottom ?: 0, greetingBlock?.bottom ?: 0)
        headerUnderlap = maxOf(gridUnderlap, actualHeader, minHeader)
        WidgetShelfPlacement.apply(shelf, headerUnderlap, dockTop, density, expandedHub)
        if (slots.contains(cards[2])) fitContinueWidth()
    }

    private fun fitContinueWidth() {
        val card = cards[2]
        val column = card.findViewById<PosterColumn>(R.id.contColumn) ?: return
        val lp = card.layoutParams as FrameLayout.LayoutParams
        val top = ((shelf.layoutParams.height - lp.height) / 2).coerceAtLeast(0)
        val tilesBottom = smartHomeBottom()
        if (tilesBottom > top) lp.height = tilesBottom + card.paddingBottom - top
        lp.gravity = Gravity.TOP or Gravity.START
        lp.topMargin = top

        val natural = column.widthFor(lp.height - card.paddingTop - card.paddingBottom) +
            card.paddingLeft + card.paddingRight
        if (natural in 1 until lp.width) {
            lp.leftMargin += lp.width - natural
            lp.width = natural
        }
        card.layoutParams = lp
    }

    private fun realignContinue() {
        val grid = lastGrid ?: return
        shelf.post { align(grid, lastColumns, lastDockTop) }
    }

    private fun smartHomeBottom(): Int {
        val tiles = shelf.findViewById<View>(R.id.smartHomeTiles) ?: return -1
        if (tiles.visibility != View.VISIBLE || tiles.height == 0) return -1
        val bounds = Rect(0, 0, tiles.width, tiles.height)
        (shelf as ViewGroup).offsetDescendantRectToMyCoords(tiles, bounds)
        return bounds.bottom
    }

    fun onGridScrolled(grid: RecyclerView) {
        if (!enabled) return
        val offset = grid.computeVerticalScrollOffset()
        shelf.translationY = -offset.toFloat()
        val layout = shelf.layoutParams as ViewGroup.MarginLayoutParams
        val underHeader = offset - (layout.topMargin - headerUnderlap)
        val visibility = (1f - underHeader / maxOf(1f, shelf.height.toFloat())).coerceIn(0f, 1f)
        shelf.alpha = visibility
        shelf.visibility = if (visibility == 0f) View.INVISIBLE else View.VISIBLE
    }

    fun setPalette(p: Palette) {
        palette = p
        val darkP = Palette.dark(p)
        for (c in allCards()) c.setPalette(darkP)
        highlight.setColor(if (editing) darkP.tertiary else darkP.primary)
        addLabel.setTextColor(darkP.onSurfaceVariant)
        addIcon.imageTintList = ColorStateList.valueOf(darkP.onSurfaceVariant)
        for (t in arrayOf(clockAmPm, weatherPlace, airLabel, lunarLabel, contLabel)) {
            t.setTextColor(darkP.onSurfaceVariant)
        }
        for (t in arrayOf(clockTime, weatherTemp, lunarDate, airValue, contTitle, contRemaining)) {
            t.setTextColor(0xFFFFFFFF.toInt())
        }
        for (t in arrayOf(clockDate, weatherDetail, airDetail, holiday, contSubtitle, contCount)) {
            t.setTextColor(darkP.onSurfaceVariant)
        }
        val dividerColor = 0x33FFFFFF.toInt()
        shelf.findViewById<View>(R.id.clockDividerLine)?.setBackgroundColor(dividerColor)
        weatherIcon.setPalette(darkP)
        paintClockDate()
        paintHaConnection()
        paintContinue()
        paintTiles()
        paintAir()
    }

    fun updateWeather(fresh: Weather.Report?, day: Boolean, offline: Boolean) {
        report = fresh
        weatherDay = day
        weatherOffline = offline
        paintWeather()
        if (started) {
            paintClockDate()
            paintHaConnection()
        }
    }

    private var weatherDay = true

    private fun paintWeather() {
        val darkP = Palette.dark(palette)
        weatherTemp.setTextColor(0xFFFFFFFF.toInt())
        weatherPlace.setTextColor(0xFFFFFFFF.toInt())
        weatherDetail.setTextColor(darkP.onSurfaceVariant)
        val rep = report
        if (rep == null) {
            weatherPlace.setText(R.string.weather)
            weatherTemp.text = "–"
            weatherDetail.text = if (weatherOffline) activity.getString(R.string.weather_offline) else ""
            weatherIcon.setIcon(activity.getDrawable(R.drawable.ic_w_cloud))
            return
        }
        val place = if (rep.place.isEmpty()) activity.getString(R.string.weather) else rep.place
        val sky = Weather.sky(if (weatherPage == 2) rep.tomorrowCode else rep.code)
        if (weatherPage == 1) {
            weatherPlace.setText(R.string.weather_feels)
            weatherTemp.text = "${degrees(rep.feels)}°"
            weatherDetail.text = activity.getString(
                if (fahrenheit()) R.string.weather_details_mph else R.string.weather_details,
                rep.humidity,
                Math.round(if (fahrenheit()) rep.wind / 1.609 else rep.wind).toInt()
            )
        } else if (weatherPage == 2) {
            weatherPlace.text = activity.getString(R.string.weather_tomorrow, place)
            weatherTemp.text = "${degrees(rep.tomorrowHigh)}°"
            weatherDetail.text = activity.getString(R.string.weather_tomorrow_detail, degrees(rep.tomorrowLow), rep.tomorrowRain)
        } else {
            weatherPlace.text = place
            weatherTemp.text = "${degrees(rep.temp)}°"
            weatherDetail.text = activity.getString(
                R.string.weather_range,
                activity.getString(Weather.label(sky)),
                degrees(rep.high),
                degrees(rep.low)
            )
        }
        weatherIcon.setIcon(activity.getDrawable(Weather.icon(sky, weatherPage == 2 || weatherDay)))
    }

    private fun degrees(celsius: Double): Int = Math.round(if (fahrenheit()) celsius * 9 / 5 + 32 else celsius).toInt()

    private fun refreshAir(force: Boolean) {
        if (air == null) air = AirQuality.cached(activity)
        paintAir()
        val now = System.currentTimeMillis()
        if (airFetching) return
        val a = air
        if (!force && a != null && !a.stale(now)) return
        if (!force && now - airFailure < WEATHER_RETRY_MS) return
        airFetching = true
        val app = activity.applicationContext
        Thread({
            val fresh = try {
                AirQuality.fetch(app, city())
            } catch (_: Exception) {
                null
            }
            main.post {
                airFetching = false
                if (fresh != null) air = fresh
                else airFailure = System.currentTimeMillis()
                paintAir()
            }
        }, "air-quality").start()
    }

    private fun paintAir() {
        val darkP = Palette.dark(palette)
        airLabel.setTextColor(darkP.onSurfaceVariant)
        airValue.setTextColor(0xFFFFFFFF.toInt())
        airDetail.setTextColor(darkP.onSurfaceVariant)
        airBadge.visibility = View.VISIBLE
        airBadge.setIcon(activity.getDrawable(R.drawable.ic_air))
        val a = air
        if (a == null) {
            airLabel.setText(R.string.air_quality)
            airValue.text = "–"
            airDetail.text = if (airFailure > 0) activity.getString(R.string.air_offline) else ""
            airBadge.setColors(darkP.surfaceContainerHighest, 0xFFFFFFFF.toInt())
            return
        }
        val aqi = if (airTomorrow && a.tomorrowMax >= 0) a.tomorrowMax else a.aqi
        val level = AirQuality.level(aqi)
        val label = activity.getString(AirQuality.label(level))
        if (airTomorrow) {
            airLabel.text = activity.getString(R.string.air_tomorrow, label)
            airValue.text = if (a.tomorrowMax >= 0) a.tomorrowMax.toString() else "–"
            airDetail.text = activity.getString(R.string.air_detail_tomorrow, label)
        } else {
            airLabel.text = label
            airValue.text = a.aqi.toString()
            airDetail.text = activity.getString(R.string.air_detail, label, a.pm25)
        }
        val harmonized = Blend.harmonize(AirQuality.color(level), palette.primary)
        val hct = Hct.fromInt(harmonized)
        hct.tone = (if (palette.dark) 30 else 85).toDouble()
        val fill = hct.toInt()
        hct.tone = (if (palette.dark) 90 else 20).toDouble()
        val icon = hct.toInt()
        airBadge.setColors(fill, icon)
    }

    private fun paintCalendar() {
        val darkP = Palette.dark(palette)
        lunarLabel.setTextColor(darkP.onSurfaceVariant)
        lunarDate.setTextColor(0xFFFFFFFF.toInt())
        holiday.setTextColor(darkP.onSurfaceVariant)
        val today = LocalDate.now()
        val l = LunarCalendar.toLunar(today)
        lunarLabel.text = activity.getString(R.string.lunar_year, LunarCalendar.canChi(l.year))
        val date = "${l.day}/${l.month}"
        lunarDate.text = if (l.leap) activity.getString(R.string.lunar_leap, date) else date
        val upcoming = ArrayList(LunarCalendar.upcoming(today))
        if (tetOnly()) upcoming.removeIf { u -> u.holiday != LunarCalendar.Holiday.TET }
        if (upcoming.isEmpty()) {
            holiday.text = ""
            return
        }
        val u = upcoming[Math.floorMod(holidayStep, upcoming.size)]
        val name = activity.getString(holidayName(u.holiday))
        holiday.text = when (u.days.toInt()) {
            0 -> activity.getString(R.string.holiday_today, name)
            1 -> activity.getString(R.string.holiday_tomorrow, name)
            else -> activity.getString(R.string.holiday_in_days, name, u.days.toInt())
        }
    }

    companion object {
        private const val PROGRESS_TRACK = 0x99000000.toInt()
        private const val TILE_VALUE_SP = 18f
        private const val MODE_TITLE_SP = 13f
        const val PREF_KEY = "widgets"
        @JvmField val AGENT_WIDGET_KEY = Contract.Key.AGENT_WIDGET_ENABLED
        const val ORDER_KEY = "widget_order"
        const val CLOCK = "clock"
        const val WEATHER = "weather"
        const val CONTINUE = "continue_watching"
        const val AIR = "air"
        const val CALENDAR = "calendar"
        private val ALL = arrayOf(CLOCK, WEATHER, CONTINUE, AIR, CALENDAR)
        private const val MAX_CARDS = 4
        private const val WATCH_ROTATE_MS = 8_000L
        private const val WEATHER_RETRY_MS = 5 * 60_000L
        private const val ENTER_SCALE = 0.85f

        private const val CITY_KEY = "weather_city"
        private const val UNIT_KEY = "weather_unit"
        private const val TET_ONLY_KEY = "calendar_tet_only"
        private const val WEATHER_PAGE_KEY = "weather_page"
        private const val AIR_PAGE_KEY = "air_page"

        private val WEATHER_PAGE_NAMES = intArrayOf(R.string.page_now, R.string.page_details, R.string.page_tomorrow)
        private val AIR_PAGE_NAMES = intArrayOf(R.string.page_today, R.string.page_tomorrow)
        private val CITY_QUERIES = arrayOf("", "Hanoi|VN", "Ho Chi Minh City|VN", "Da Nang|VN", "Haiphong|VN", "Can Tho|VN", "Hue|VN", "Nha Trang|VN", "Da Lat|VN")
        private val CITY_NAMES = arrayOf(null, "Hà Nội", "TP. Hồ Chí Minh", "Đà Nẵng", "Hải Phòng", "Cần Thơ", "Huế", "Nha Trang", "Đà Lạt")

        private const val WEATHER_PAGES = 3
        private const val TILE_SETTLE_MS = 1_500L

        private fun holidayName(h: LunarCalendar.Holiday): Int {
            return when (h) {
                LunarCalendar.Holiday.NEW_YEAR -> R.string.holiday_new_year
                LunarCalendar.Holiday.TET -> R.string.holiday_tet
                LunarCalendar.Holiday.HUNG_KINGS -> R.string.holiday_hung_kings
                LunarCalendar.Holiday.REUNIFICATION -> R.string.holiday_reunification
                LunarCalendar.Holiday.LABOUR -> R.string.holiday_labour
                LunarCalendar.Holiday.MID_AUTUMN -> R.string.holiday_mid_autumn
                LunarCalendar.Holiday.NATIONAL_DAY -> R.string.holiday_national_day
                else -> R.string.holiday_christmas
            }
        }
    }
}
