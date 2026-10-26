package com.gathin.tvhome.feature.smarthome.ui
import com.gathin.tvhome.feature.smarthome.data.*
import com.gathin.tvhome.feature.smarthome.presenter.*

import com.gathin.tvhome.feature.smarthome.presenter.SmartHomePresenter
import com.gathin.tvhome.feature.smarthome.presenter.SmartHomeIntent
import com.gathin.tvhome.feature.smarthome.presenter.SmartHomeState
import com.gathin.tvhome.feature.smarthome.presenter.SmartHomeEffect
import com.gathin.tvhome.feature.smarthome.data.SmartHomeDevice
import com.gathin.tvhome.feature.smarthome.data.HassClient
import com.gathin.tvhome.feature.smarthome.data.SmartHomeRepositoryImpl
import com.gathin.tvhome.feature.smarthome.widget.*
import com.gathin.tvhome.core.view.SideSheet
import android.app.Activity
import android.app.Dialog
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.settings.data.Config
import com.gathin.tvhome.feature.smarthome.data.HomeAssistantRemoteDataSourceImpl
import com.gathin.tvhome.feature.smarthome.data.SmartHomeAction
import com.gathin.tvhome.core.view.ShapeBadge
import com.gathin.tvhome.core.design.M3Skin
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.core.motion.FocusScrollView
import com.gathin.tvhome.feature.smarthome.widget.SmartHomeHelper
import com.gathin.tvhome.feature.smarthome.widget.SmartHomePresetMenu
import com.gathin.tvhome.feature.smarthome.widget.SmartHomeRegularCards
import com.gathin.tvhome.feature.smarthome.widget.SmartHomeRoomRenderer
import com.gathin.tvhome.feature.settings.data.AgentHomeConfig
import com.gathin.tvhome.core.util.dialogScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject



class SmartHomeSheet(
    private val activity: Activity,
    private val config: Config,
    private val onOpenHaSettings: () -> Unit,
    private val onDismiss: (() -> Unit)? = null
) {
    private val settings = AgentHomeConfig(activity)
    private val dialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)

    private val remoteDataSource by lazy {
        HomeAssistantRemoteDataSourceImpl {
            val url = settings.homeAssistantUrl.ifBlank { "http://192.168.1.50:8123" }
            val token = settings.homeAssistantToken
            if (token.isNotBlank()) runCatching { HassClient(url, token) }.getOrNull() else null
        }
    }
    private val repository by lazy { SmartHomeRepositoryImpl(remoteDataSource) }
    private val presenter by lazy {
        SmartHomePresenter(repository, settings, dialog.dialogScope)
    }

    private lateinit var scrim: View
    private lateinit var sheet: View
    private lateinit var subtitle: TextView
    private lateinit var refresh: ImageView
    private lateinit var configure: ImageView
    private lateinit var btnEdit: LinearLayout
    private lateinit var btnEditIcon: ImageView
    private lateinit var btnEditText: TextView
    private lateinit var devices: LinearLayout
    private lateinit var scroll: FocusScrollView

    private var sideSheet: SideSheet? = null
    private var firstCardView: View? = null

    fun show() {
        val view = LayoutInflater.from(activity).inflate(R.layout.activity_smarthome, null)
        dialog.setContentView(view)
        bindViews(view)

        val palette = Palette.dark(Palette.currentOrBaseline())
        M3Skin.apply(sheet, palette, config.animations, config.reducedMotion)
        sheet.background = activity.getDrawable(R.drawable.dark_panel)
        sideSheet = SideSheet(activity, scrim, sheet, config) {
            dialog.dismiss()
            onDismiss?.invoke()
        }
        scrim.setOnClickListener { close() }
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                close()
                true
            } else false
        }
        dialog.show()
        sideSheet?.slideIn(scroll)

        observePresenter()
        startPeriodicRefresh()
        presenter.processIntent(SmartHomeIntent.Load)
    }

    private fun bindViews(view: View) {
        scrim = view.findViewById(R.id.scrim)
        sheet = view.findViewById(R.id.sheet)
        subtitle = view.findViewById(R.id.smarthomeHeaderSubtitle)
        refresh = view.findViewById(R.id.btnRefresh)
        configure = view.findViewById(R.id.btnConfigure)
        btnEdit = view.findViewById(R.id.btnEdit)
        btnEditIcon = view.findViewById(R.id.btnEditIcon)
        btnEditText = view.findViewById(R.id.btnEditText)
        scroll = view.findViewById(R.id.smarthomeScroll)

        val sections = view.findViewById<LinearLayout>(R.id.sectionsContainer)
        devices = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        sections.addView(devices)
        view.findViewById<ShapeBadge>(R.id.smarthomeBadge).apply {
            setIcon(activity.getDrawable(R.drawable.ic_home_assistant))
            val p = Palette.dark(Palette.currentOrBaseline())
            setColors(p.primaryContainer, p.onPrimaryContainer)
        }
        configure.setOnClickListener {
            dialog.dismiss()
            onOpenHaSettings()
        }
        refresh.setOnClickListener { presenter.processIntent(SmartHomeIntent.Load) }
        btnEdit.setOnClickListener { presenter.processIntent(SmartHomeIntent.ToggleEditMode) }
    }

    private fun observePresenter() {
        dialog.dialogScope.launch {
            presenter.state.collect { state -> render(state) }
        }
        dialog.dialogScope.launch {
            presenter.effects.collect { effect ->
                when (effect) {
                    is SmartHomeEffect.ShowToast -> Toast.makeText(
                        activity, effect.message, Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun startPeriodicRefresh() {
        dialog.dialogScope.launch {
            while (isActive) {
                delay(15_000)
                if (dialog.isShowing && !presenter.state.value.isEditMode) {
                    presenter.processIntent(SmartHomeIntent.Load)
                }
            }
        }
    }

    private fun render(state: SmartHomeState) {
        refresh.isEnabled = !state.isLoading
        if (state.errorMessage != null && state.devices.isEmpty()) {
            devices.removeAllViews()
            subtitle.text = "Không có dữ liệu · ${state.errorMessage}"
            return
        }

        val rows = state.devices
        val rawUrl = settings.homeAssistantUrl.ifBlank { "http://192.168.1.50:8123" }
        val host = SmartHomeHelper.serverHost(rawUrl)

        if (state.isEditMode) {
            btnEditIcon.setImageResource(R.drawable.ic_check)
            btnEditText.text = "Lưu"
            btnEditText.setTextColor(0xFF38BDF8.toInt())
            subtitle.text =
                "Bấm vào thiết bị để ẩn hoặc hiện · Bấm Lưu khi hoàn tất"
        } else {
            btnEditIcon.setImageResource(R.drawable.ic_edit)
            btnEditText.text = "Chỉnh sửa"
            btnEditText.setTextColor(0xFFFFFFFF.toInt())
            val sub = SmartHomeHelper.resolveSubtitle(settings, rows, host, false)
            if (sub.isNotBlank()) subtitle.text = sub
        }

        val supported = setOf("light", "switch", "fan", "climate", "cover", "scene", "script")
        val available = rows.filter { it.domain in supported && it.available }
        val hiddenSet = if (state.isEditMode) state.pendingHiddenEntities else state.hiddenEntities
        val visible = if (state.isEditMode) available
        else available.filter { !hiddenSet.contains(it.id) }

        if (SmartHomeHelper.tryUpdateInPlace(devices, visible, state.isEditMode)) {
            if (state.isEditMode) updateEditBadges(state.pendingHiddenEntities)
            return
        }

        rebuildDevices(visible, state)
    }

    private fun updateEditBadges(pendingHidden: Set<String>) {
        for (i in 0 until devices.childCount) {
            val child = devices.getChildAt(i)
            if (child is ViewGroup) {
                updateBadgesInContainer(child, pendingHidden)
            }
        }
    }

    private fun updateBadgesInContainer(parent: ViewGroup, pendingHidden: Set<String>) {
        val tag = parent.tag as? String
        if (tag != null) {
            SmartHomeRegularCards.updateCardEditState(parent, pendingHidden.contains(tag))
        }
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is ViewGroup) updateBadgesInContainer(child, pendingHidden)
        }
    }

    private fun rebuildDevices(visible: List<SmartHomeDevice>, state: SmartHomeState) {
        val savedY = scroll.scrollY
        val focusTag = activity.currentFocus?.tag
        firstCardView = null
        devices.removeAllViews()

        val renderer = SmartHomeRoomRenderer(
            activity, config, state.isEditMode,
            { ent, act, vals -> executeAction(ent, act, vals ?: JSONObject()) },
            { id, _ -> presenter.processIntent(SmartHomeIntent.ToggleDeviceHidden(id)) },
            { a, m, c, s -> SmartHomePresetMenu.show(activity, a, m, c, s) }
        )

        val other = visible.filter { it.domain !in setOf("scene", "script") }
        other.groupBy { it.area.ifBlank { "Thiết bị khác · ${it.domain}" } }
            .entries
            .sortedWith(compareBy({ renderer.roomPriority(it.key) }, { it.key }))
            .forEach { (room, list) ->
                renderer.renderRoom(devices, room, list.sortedBy { it.name }) { card ->
                    if (firstCardView == null) firstCardView = card
                }
            }

        renderer.renderScenes(devices, visible.filter { it.domain in setOf("scene", "script") }) {
            if (firstCardView == null) firstCardView = it
        }

        if (state.isEditMode) updateEditBadges(state.pendingHiddenEntities)

        scroll.post {
            if (!dialog.isShowing) return@post
            val target = if (focusTag != null) devices.findViewWithTag<View>(focusTag) else null
            if (target != null) {
                target.requestFocus()
            } else {
                firstCardView?.requestFocus() ?: configure.requestFocus()
                scroll.scrollTo(0, savedY)
            }
        }
    }

    private fun executeAction(
        entity: SmartHomeDevice,
        action: SmartHomeAction,
        values: JSONObject
    ) {
        val card = devices.findViewWithTag<View>(entity.id)
        if (card != null && action.service in setOf("toggle", "turn_on", "turn_off")) {
            val next = SmartHomeHelper.nextToggleState(action.service, entity.state)
            SmartHomeRegularCards.updateCardUi(card, entity.copy(state = next))
        }
        presenter.processIntent(SmartHomeIntent.ExecuteAction(entity, action, values))
    }

    fun close() {
        if (sideSheet?.closing() == true) return
        sideSheet?.close() ?: run {
            dialog.dismiss()
            onDismiss?.invoke()
        }
    }
}
