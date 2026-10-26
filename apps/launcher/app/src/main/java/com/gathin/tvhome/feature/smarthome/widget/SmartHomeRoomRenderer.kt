package com.gathin.tvhome.feature.smarthome.widget
import com.gathin.tvhome.feature.smarthome.data.*
import com.gathin.tvhome.feature.smarthome.presenter.*
import com.gathin.tvhome.feature.smarthome.ui.*

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.gathin.tvhome.feature.settings.data.Config
import com.gathin.tvhome.feature.smarthome.data.SmartHomeAction
import com.gathin.tvhome.feature.smarthome.data.SmartHomeDevice
import org.json.JSONObject


class SmartHomeRoomRenderer(
    private val context: Context,
    private val config: Config,
    private val editMode: Boolean,
    private val onAction: (SmartHomeDevice, SmartHomeAction, JSONObject?) -> Unit,
    private val onToggleHidden: (String, View) -> Unit,
    private val onShowPresetMenu: (View, List<String>, String, (String) -> Unit) -> Unit
) {

    fun roomPriority(roomName: String): Int {
        val name = roomName.lowercase()
        return when {
            name.contains("khách") -> 10
            name.contains("cửa") || name.contains("hành lang") ||
                name.contains("lối vào") -> 20
            name.contains("bếp") || name.contains("ăn") -> 30
            name.contains("ngủ chính") || name.contains("master") ||
                name.contains("ngủ 1") -> 40
            name.contains("ngủ nhỏ") || name.contains("ngủ 2") ||
                name.contains("ngủ phụ") -> 50
            name.contains("ngủ") -> 60
            name.contains("tắm") || name.contains("toilet") || name.contains("vệ sinh") -> 70
            name.contains("ban công") || name.contains("logia") -> 80
            else -> 100
        }
    }

    fun addLabel(container: LinearLayout, label: String) {
        val title = label.removePrefix("Thiết bị khác · ").trim()
        container.addView(TextView(context).apply {
            text = title
            textSize = 17f
            setPadding(8, 20, 8, 10)
            setTextColor(0xFFFFFFFF.toInt())
            includeFontPadding = false
        })
    }

    fun renderRoom(
        container: LinearLayout,
        room: String,
        entities: List<SmartHomeDevice>,
        onFirstCard: (View) -> Unit
    ) {
        addLabel(container, room)
        val density = context.resources.displayMetrics.density
        val grid = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = (12 * density).toInt()
            }
        }

        val climates = entities.filter { it.domain == "climate" }.toMutableList()
        val fans = entities.filter { it.domain == "fan" }.toMutableList()
        val regulars = entities.filter { it.domain != "climate" && it.domain != "fan" }
            .toMutableList()

        while (climates.isNotEmpty() || fans.isNotEmpty() || regulars.isNotEmpty()) {
            if (climates.isNotEmpty() || fans.isNotEmpty()) {
                val block = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(-1, -2)
                }
                val hero1 = buildHeroCard(climates, fans, density)
                onFirstCard(hero1)
                block.addView(hero1)

                if (climates.isNotEmpty() || fans.isNotEmpty()) {
                    block.addView(buildHeroCard(climates, fans, density))
                } else {
                    block.addView(buildRegular2x2(regulars, density, onFirstCard))
                }
                grid.addView(block)
            } else {
                grid.addView(buildRegularRow(regulars, density, onFirstCard))
            }
        }
        container.addView(grid)
    }

    fun renderScenes(
        container: LinearLayout,
        scenes: List<SmartHomeDevice>,
        onFirstCard: (View) -> Unit
    ) {
        if (scenes.isEmpty()) return
        addLabel(container, "Kịch bản")
        val density = context.resources.displayMetrics.density
        val grid = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = (12 * density).toInt()
            }
        }
        scenes.chunked(4).forEach { chunk ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = 4f
                layoutParams = LinearLayout.LayoutParams(-1, -2)
            }
            chunk.forEach { entity ->
                val card = SmartHomeRegularCards.createSceneCard(
                    context, entity, editMode, config,
                    { ent, act -> onAction(ent, act, null) },
                    onToggleHidden
                )
                onFirstCard(card)
                row.addView(card)
            }
            for (i in chunk.size until 4) {
                row.addView(createSpacer(density))
            }
            grid.addView(row)
        }
        container.addView(grid)
    }

    private fun buildHeroCard(
        climates: MutableList<SmartHomeDevice>,
        fans: MutableList<SmartHomeDevice>,
        density: Float
    ): View {
        val entity = if (climates.isNotEmpty()) climates.removeAt(0) else fans.removeAt(0)
        val v = if (entity.domain == "climate") {
            SmartHomeClimateCard.create(
                context, entity, editMode, onAction, onToggleHidden
            )
        } else {
            SmartHomeFanCard.create(
                context, entity, editMode, onAction, onToggleHidden, onShowPresetMenu
            )
        }
        return v.apply {
            layoutParams = LinearLayout.LayoutParams(
                (438 * density).toInt(), (174 * density).toInt()
            ).apply {
                val m = (5 * density).toInt()
                setMargins(m, m, m, m)
            }
        }
    }

    private fun buildRegular2x2(
        regulars: MutableList<SmartHomeDevice>,
        density: Float,
        onFirstCard: (View) -> Unit
    ): View {
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        for (r in 0 until 2) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = 2f
                layoutParams = LinearLayout.LayoutParams(-1, -2)
            }
            for (c in 0 until 2) {
                if (regulars.isNotEmpty()) {
                    val reg = regulars.removeAt(0)
                    val card = SmartHomeRegularCards.createCard(
                        context, reg, editMode, config,
                        { ent, act -> onAction(ent, act, null) },
                        onToggleHidden
                    )
                    onFirstCard(card)
                    row.addView(card)
                } else {
                    row.addView(createSpacer(density))
                }
            }
            col.addView(row)
        }
        return col
    }

    private fun buildRegularRow(
        regulars: MutableList<SmartHomeDevice>,
        density: Float,
        onFirstCard: (View) -> Unit
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 4f
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        val chunk = regulars.take(4)
        regulars.removeAll(chunk)
        chunk.forEach { entity ->
            val card = SmartHomeRegularCards.createCard(
                context, entity, editMode, config,
                { ent, act -> onAction(ent, act, null) },
                onToggleHidden
            )
            onFirstCard(card)
            row.addView(card)
        }
        for (i in chunk.size until 4) {
            row.addView(createSpacer(density))
        }
        return row
    }

    private fun createSpacer(density: Float): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(0, 1, 1f).apply {
            val m = (5 * density).toInt()
            setMargins(m, m, m, m)
        }
    }
}
