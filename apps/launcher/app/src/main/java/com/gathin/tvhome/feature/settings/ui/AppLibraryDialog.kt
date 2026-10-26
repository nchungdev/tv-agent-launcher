package com.gathin.tvhome.feature.settings.ui
import com.gathin.tvhome.core.design.Fonts
import com.gathin.tvhome.feature.settings.data.*
import com.gathin.tvhome.feature.settings.widget.*

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.main.data.Cards
import com.gathin.tvhome.feature.main.data.LibraryApps
import com.gathin.tvhome.core.view.GlassView
import com.gathin.tvhome.core.design.M3Skin
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.core.design.Shapes
import com.gathin.tvhome.core.motion.Motion
import java.text.Collator
import java.util.ArrayList
import java.util.HashMap
import java.util.LinkedHashMap
import java.util.function.Predicate


private const val CATALOG_HEIGHT_DP = 214
private const val CARD_WIDTH_DP = 112
private const val CARD_HEIGHT_DP = 112

class AppLibraryDialog(
    private val activity: Activity,
    private val animations: Boolean,
    private val reducedMotion: Boolean,
    private val onHome: Predicate<String>,
    private val changed: Runnable,
    dismissed: Runnable
) {

    private val panel = LinearLayout(activity)
    private val rtl: Boolean = activity.resources.getBoolean(R.bool.rtl_app_grid)
    private var instructions: TextView? = null
    private var totalApps = 0
    @JvmField val popup: PopupWindow
    private val density: Float = activity.resources.displayMetrics.density
    private var apps: List<ResolveInfo> = ArrayList()
    private var selectedPackage: String? = null
    private var selectedPosition = 0
    private var selectedOffset = 0
    private val installTimes = HashMap<String, Long>()
    private val labels = HashMap<String, String>()
    private var catalogTime = 0L
    private var actionPopup: PopupWindow? = null
    private var catalog: RecyclerView? = null
    private var shelf: FrameLayout? = null
    private var highlight: GlassView? = null
    private val colors: Palette = Palette.currentOrBaseline()
    private var spring: SpringAnimation? = null
    private var scrollOffset = 0f
    private var highlightShown = false
    private val catalogWidth: Int
    private val tvPackages = HashSet<String>()

    init {
        panel.layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(dp(12), dp(12), dp(12), dp(12))
        panel.background = surface(colors.surfaceContainerHigh, Shapes.EXTRA_LARGE.toInt())
        panel.elevation = dp(6).toFloat()

        val decor = activity.window.decorView
        catalogWidth = dp(800).coerceAtMost(dp(200).coerceAtLeast(decor.width - dp(64)))
        val width = catalogWidth
        val height = dp(CATALOG_HEIGHT_DP)

        popup = PopupWindow(panel, width, height, true).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            animationStyle = 0
            setOnDismissListener {
                dismissActions()
                stopHighlight()
                dismissed.run()
            }
        }
    }

    private fun stopHighlight() {
        spring?.cancel()
        catalog?.stopScroll()
        catalog = null
        highlightShown = false
    }

    private fun highlight(card: View, animate: Boolean) {
        val h = highlight ?: return
        if (card.parent == null) return
        val rect = Rect(0, 0, card.width, card.height)
        shelf?.offsetDescendantRectToMyCoords(card, rect)
        val lp = h.layoutParams
        lp.width = rect.width() + dp(12)
        lp.height = rect.height() + dp(12)
        h.layoutParams = lp
        h.translationY = (rect.top - dp(6)).toFloat()
        val x = (rect.left - dp(6)).toFloat() + scrollOffset
        val sp = spring
        if (!highlightShown || !animate || !animations || sp == null) {
            spring?.cancel()
            h.translationX = x - scrollOffset
        } else {
            sp.animateToFinalPosition(x)
        }
        highlightShown = true
        h.alpha = 1f
    }

    private fun surface(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun resize(width: Int, height: Int) {
        popup.width = width
        popup.height = height
        if (popup.isShowing) popup.update(width, height)
    }

    fun close() {
        dismissActions()
        popup.dismiss()
    }

    fun show() {
        refresh()
        popup.showAtLocation(activity.window.decorView, Gravity.CENTER, 0, 0)
        val container = panel.rootView
        val lp = container.layoutParams
        if (lp is WindowManager.LayoutParams) {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            lp.dimAmount = 0.55f
            activity.windowManager.updateViewLayout(container, lp)
        }
        panel.alpha = 0f
        panel.animate().alpha(1f).setDuration(180).start()
    }

    fun refresh() {
        dismissActions()
        val pm = activity.packageManager
        val unique = LinkedHashMap<String, ResolveInfo>()
        tvPackages.clear()
        for (category in arrayOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            for (info in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                val ai = info.activityInfo ?: continue
                if (!ai.exported) continue
                val pkg = ai.packageName
                if (Intent.CATEGORY_LEANBACK_LAUNCHER == category) tvPackages.add(pkg)
                if (pkg != activity.packageName && !unique.containsKey(pkg)) unique[pkg] = info
            }
        }
        totalApps = unique.size
        val fresh = ArrayList(unique.values)
        installTimes.clear()
        labels.clear()
        val home = HashMap<String, Boolean>()
        for (app in fresh) {
            val pkg = app.activityInfo.packageName
            home[pkg] = onHome.test(pkg)
            label(app)
            try {
                installTimes[pkg] = pm.getPackageInfo(pkg, 0).firstInstallTime
            } catch (_: Exception) {
                installTimes[pkg] = 0L
            }
        }
        catalogTime = System.currentTimeMillis()
        val collator = Collator.getInstance()
        fresh.sortWith { a, b ->
            val pa = a.activityInfo.packageName
            val pb = b.activityInfo.packageName
            val priority = LibraryApps.comparePriority(
                home[pa] == true, installTimes[pa] ?: 0L, home[pb] == true, installTimes[pb] ?: 0L, catalogTime
            )
            if (priority != 0) return@sortWith priority
            val byLabel = collator.compare(label(a), label(b))
            if (byLabel != 0) byLabel else pa.compareTo(pb)
        }
        apps = fresh
        showCatalog()
    }

    private fun label(app: ResolveInfo): String {
        val pkg = app.activityInfo.packageName
        val cached = labels[pkg]
        if (cached != null) return cached
        val value = try {
            Cards.cleanLabel(app.loadLabel(activity.packageManager)?.toString(), pkg)
        } catch (_: Exception) {
            pkg
        }
        labels[pkg] = value
        return value
    }

    private fun showCatalog() {
        stopHighlight()
        resize(catalogWidth, dp(CATALOG_HEIGHT_DP))
        panel.removeAllViews()

        val header = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(4), dp(8), dp(4))
        }
        val title = text(activity.getString(R.string.app_library), 20, colors.onSurface).apply {
            typeface = Fonts.REGULAR
            gravity = Gravity.CENTER_VERTICAL
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(32), 1f))

        val countText = activity.resources.getQuantityString(R.plurals.library_app_count, totalApps, totalApps)
        val count = text(countText, 13, colors.onSurfaceVariant).apply { isSingleLine = true }
        header.addView(count, LinearLayout.LayoutParams(-2, -2))
        panel.addView(header, LinearLayout.LayoutParams(-1, dp(38)))

        instructions = text(activity.getString(R.string.library_hint_add), 13, colors.onSurfaceVariant).apply {
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(12), 0, dp(12), dp(6))
        }
        panel.addView(instructions, LinearLayout.LayoutParams(-1, dp(24)))

        if (apps.isEmpty()) {
            instructions?.visibility = View.GONE
            val emptyTitle = text(activity.getString(R.string.library_empty), 18, colors.onSurface).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(12), dp(10), dp(12), dp(8))
            }
            panel.addView(emptyTitle, LinearLayout.LayoutParams(-1, -2))
            panel.isFocusableInTouchMode = true
            panel.requestFocus()
            return
        }

        val sf = FrameLayout(activity).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            clipChildren = false
        }
        shelf = sf

        val list = object : RecyclerView(activity) {}
        list.layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        catalog = list
        list.layoutManager = object : LinearLayoutManager(activity, HORIZONTAL, false) {
            override fun isLayoutRTL(): Boolean = rtl
        }
        list.itemAnimator = null
        list.overScrollMode = View.OVER_SCROLL_NEVER
        list.clipToPadding = false
        list.setPadding(dp(10), dp(6), dp(10), dp(6))
        list.adapter = CatalogAdapter()
        sf.addView(list, FrameLayout.LayoutParams(-1, -1))

        val hl = GlassView(activity, null).apply {
            setRadius(dp(20).toFloat())
            alpha = 0f
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        highlight = hl
        sf.addView(hl, FrameLayout.LayoutParams(dp(CARD_WIDTH_DP + 12), dp(CARD_HEIGHT_DP + 4)))

        scrollOffset = 0f
        val sp = SpringAnimation(hl, object : FloatPropertyCompat<View>("libraryScrollX") {
            override fun getValue(view: View): Float = view.translationX + scrollOffset
            override fun setValue(view: View, value: Float) { view.translationX = value - scrollOffset }
        })
        spring = sp
        Motion.DEFAULT_SPATIAL.reduced(reducedMotion).apply(sp)
        hl.setRingColor(colors.primary)
        hl.setRadius(dp(Shapes.LARGE.toInt()).toFloat())

        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                scrollOffset += dx
                hl.translationX = hl.translationX - dx
            }
        })
        panel.addView(sf, LinearLayout.LayoutParams(-1, 0, 1f))

        var position = minOf(selectedPosition, apps.size - 1)
        for (i in apps.indices) {
            if (apps[i].activityInfo.packageName == selectedPackage) {
                position = i
                break
            }
        }
        (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, selectedOffset)
        val target = position
        list.post {
            if (catalog !== list || !popup.isShowing) return@post
            val holder = list.findViewHolderForAdapterPosition(target)
            holder?.itemView?.requestFocus()
        }
    }

    private fun dismissActions() {
        val menu = actionPopup
        actionPopup = null
        menu?.dismiss()
    }

    private fun restoreCardFocus() {
        val cat = catalog ?: return
        if (!popup.isShowing) return
        cat.post {
            if (catalog == null || !popup.isShowing || actionPopup != null) return@post
            val holder = cat.findViewHolderForAdapterPosition(selectedPosition)
            holder?.itemView?.requestFocus()
        }
    }

    private fun changeHome(app: ResolveInfo, add: Boolean) {
        val pkg = app.activityInfo.packageName
        if (!LibraryApps.save(activity, pkg, add)) {
            Toast.makeText(activity, R.string.library_save_failed, Toast.LENGTH_LONG).show()
            return
        }
        selectedOffset = 0
        val cat = catalog
        if (add && cat != null) {
            val holder = cat.findViewHolderForAdapterPosition(selectedPosition)
            if (holder != null) {
                val card = holder.itemView
                val params = card.layoutParams as RecyclerView.LayoutParams
                val lm = cat.layoutManager as LinearLayoutManager
                selectedOffset = if (rtl) {
                    cat.width - cat.paddingRight - lm.getDecoratedRight(card) - params.rightMargin
                } else {
                    lm.getDecoratedLeft(card) - params.leftMargin - cat.paddingLeft
                }
            }
            val next = LibraryApps.nextFocus(selectedPosition, apps.size) { index ->
                !onHome.test(apps[index].activityInfo.packageName)
            }
            if (next >= 0) {
                selectedPackage = apps[next].activityInfo.packageName
                selectedPosition = next
            }
        }
        dismissActions()
        changed.run()
        refresh()
    }

    private fun open(app: ResolveInfo) {
        close()
        val pkg = app.activityInfo.packageName
        val intent = Intent(Intent.ACTION_MAIN).setComponent(ComponentName(pkg, app.activityInfo.name))
        if (tvPackages.contains(pkg)) intent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            activity.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(activity, activity.getString(R.string.cant_open, label(app)), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showActions(app: ResolveInfo, anchor: View) {
        dismissActions()
        val pkg = app.activityInfo.packageName
        selectedPackage = pkg
        spring?.cancel()

        val menuPanel = activity.layoutInflater.inflate(R.layout.popup_inputs, panel, false)
        val rows = menuPanel.findViewById<LinearLayout>(R.id.rows)
        val menu = PopupWindow(menuPanel, dp(220), ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            animationStyle = 0
            elevation = 0f
            setOnDismissListener {
                if (actionPopup === this) actionPopup = null
                restoreCardFocus()
            }
        }
        actionPopup = menu

        addAction(rows, R.string.library_open) { open(app) }
        val home = onHome.test(pkg)
        addAction(rows, if (home) R.string.library_remove_shortcut else R.string.library_add_home) {
            changeHome(app, !home)
        }
        addAction(rows, R.string.library_app_info) {
            close()
            try {
                activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
            } catch (_: Exception) {
                Toast.makeText(activity, activity.getString(R.string.cant_open, label(app)), Toast.LENGTH_SHORT).show()
            }
        }

        var menuWidth = dp(220)
        for (i in 0 until rows.childCount) {
            val title = rows.getChildAt(i).findViewById<TextView>(R.id.title)
            menuWidth = maxOf(menuWidth, Math.ceil(title.paint.measureText(title.text.toString()).toDouble()).toInt() + dp(58))
            title.ellipsize = TextUtils.TruncateAt.END
        }
        menuWidth = minOf(menuWidth, panel.width - dp(24))
        menu.width = menuWidth
        menuPanel.measure(View.MeasureSpec.makeMeasureSpec(menuWidth, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)

        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val panelLocation = IntArray(2)
        panel.getLocationOnScreen(panelLocation)
        val screen = Rect(
            panelLocation[0], panelLocation[1],
            panelLocation[0] + panel.width, panelLocation[1] + panel.height
        )
        val x = (location[0] + anchor.width - dp(12)).coerceIn(screen.left + dp(12), screen.right - menuWidth - dp(12))
        val y = (location[1] + dp(8)).coerceIn(screen.top + dp(12), screen.bottom - menuPanel.measuredHeight - dp(12))

        menuPanel.alpha = 0f
        menuPanel.pivotY = 0f
        menuPanel.scaleY = 0.92f
        menuPanel.translationY = -dp(8).toFloat()
        menu.showAtLocation(activity.window.decorView, Gravity.TOP or Gravity.START, x, y)
        M3Skin.apply(menuPanel, colors, animations, reducedMotion)
        Motion.DEFAULT_SPATIAL.reduced(reducedMotion).drive(menuPanel.animate().alpha(1f).scaleY(1f).translationY(0f)).start()
        rows.getChildAt(0).requestFocus()
    }

    private fun addAction(rows: LinearLayout, labelRes: Int, action: Runnable) {
        val row = activity.layoutInflater.inflate(R.layout.item_input, rows, false)
        row.findViewById<TextView>(R.id.title).setText(labelRes)
        row.isFocusableInTouchMode = true
        row.findViewById<View>(R.id.icon).visibility = View.GONE
        row.findViewById<View>(R.id.dot).visibility = View.GONE
        row.findViewById<View>(R.id.status).visibility = View.GONE
        row.setOnClickListener { action.run() }
        rows.addView(row)
        row.setOnKeyListener { v, key, event ->
            val index = rows.indexOfChild(v)
            event.action == KeyEvent.ACTION_DOWN && (key == KeyEvent.KEYCODE_DPAD_LEFT ||
                    key == KeyEvent.KEYCODE_DPAD_RIGHT ||
                    (key == KeyEvent.KEYCODE_DPAD_UP && index == 0) ||
                    (key == KeyEvent.KEYCODE_DPAD_DOWN && index == rows.childCount - 1))
        }
    }

    private fun text(value: String, size: Int, color: Int): TextView {
        return TextView(activity).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            typeface = Fonts.MEDIUM
            letterSpacing = 0.01f
            includeFontPadding = false
        }
    }

    private fun dp(value: Int): Int = Math.round(value * density)

    private inner class CatalogAdapter : RecyclerView.Adapter<AppLibraryRow>() {
        override fun onCreateViewHolder(parent: ViewGroup, type: Int): AppLibraryRow {
            val row = FrameLayout(activity)
            val params = RecyclerView.LayoutParams(dp(CARD_WIDTH_DP), dp(CARD_HEIGHT_DP)).apply {
                setMargins(dp(6), dp(2), dp(6), dp(2))
            }
            row.layoutParams = params
            row.setPadding(dp(8), dp(10), dp(8), dp(8))
            row.isFocusableInTouchMode = true
            row.isClickable = true

            val content = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            row.addView(content, FrameLayout.LayoutParams(-1, -1))

            val icon = ImageView(activity).apply {
                id = R.id.icon
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            content.addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)))

            val name = text("", 14, colors.onSurface).apply {
                id = R.id.title
                gravity = Gravity.CENTER
                maxLines = 1
            }
            content.addView(name, LinearLayout.LayoutParams(-1, dp(20)).apply { topMargin = dp(8) })

            val status = text("", 11, colors.onSurfaceVariant).apply {
                id = R.id.status
                gravity = Gravity.CENTER
                maxLines = 1
            }
            content.addView(status, LinearLayout.LayoutParams(-1, -2))

            val check = View(activity).apply { id = R.id.dot }
            val badge = FrameLayout.LayoutParams(dp(18), dp(18), Gravity.TOP or Gravity.END).apply {
                setMargins(0, dp(2), dp(2), 0)
            }
            row.addView(check, badge)
            return AppLibraryRow(row)
        }

        override fun onBindViewHolder(holder: AppLibraryRow, position: Int) {
            val app = apps[position]
            val pkg = app.activityInfo.packageName
            val icon = holder.itemView.findViewById<ImageView>(R.id.icon)
            try {
                icon.setImageDrawable(app.loadIcon(activity.packageManager))
            } catch (_: Exception) {
                icon.setImageResource(R.drawable.ic_apps)
            }
            val name = holder.itemView.findViewById<TextView>(R.id.title)
            name.text = label(app)
            name.ellipsize = TextUtils.TruncateAt.END

            val status = holder.itemView.findViewById<TextView>(R.id.status)
            val home = onHome.test(pkg)
            val recent = LibraryApps.recent(installTimes[pkg] ?: 0L, catalogTime)
            status.text = activity.getString(if (home) R.string.library_on_home else if (recent) R.string.library_recent else R.string.library_add_home)
            status.visibility = View.VISIBLE

            val cardColor = if (home) colors.secondaryContainer else colors.surfaceContainerHighest
            holder.itemView.background = surface(cardColor, Shapes.LARGE.toInt())

            val check = holder.itemView.findViewById<View>(R.id.dot)
            check.layoutParams.width = dp(if (home) 18 else 8)
            check.layoutParams.height = dp(if (home) 18 else 8)
            if (home) {
                check.setBackgroundResource(R.drawable.library_check)
                check.backgroundTintList = ColorStateList.valueOf(colors.primary)
            } else {
                check.background = surface(colors.tertiary, 8)
            }
            check.visibility = if (home || recent) View.VISIBLE else View.GONE

            holder.itemView.setOnClickListener {
                if (onHome.test(pkg)) open(app) else changeHome(app, true)
            }
            holder.itemView.setOnLongClickListener {
                showActions(app, it)
                true
            }
            holder.itemView.setOnFocusChangeListener { v, focused ->
                if (focused) {
                    selectedPackage = pkg
                    instructions?.text = activity.getString(if (onHome.test(pkg)) R.string.library_hint_open else R.string.library_hint_add)
                    v.post { if (v.hasFocus() && catalog != null) highlight(v, true) }
                    val index = holder.bindingAdapterPosition
                    if (index != RecyclerView.NO_POSITION) selectedPosition = index
                }
            }
            holder.itemView.setOnKeyListener { _, key, event ->
                val index = holder.bindingAdapterPosition
                event.action == KeyEvent.ACTION_DOWN && (key == KeyEvent.KEYCODE_DPAD_UP ||
                        key == KeyEvent.KEYCODE_DPAD_DOWN ||
                        ((if (rtl) key == KeyEvent.KEYCODE_DPAD_RIGHT else key == KeyEvent.KEYCODE_DPAD_LEFT) && index == 0) ||
                        ((if (rtl) key == KeyEvent.KEYCODE_DPAD_LEFT else key == KeyEvent.KEYCODE_DPAD_RIGHT) && index == apps.size - 1))
            }
        }

        override fun getItemCount(): Int = apps.size
    }
}
