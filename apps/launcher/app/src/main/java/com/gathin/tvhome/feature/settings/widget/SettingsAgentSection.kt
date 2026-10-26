package com.gathin.tvhome.feature.settings.widget
import com.gathin.tvhome.feature.settings.data.*
import com.gathin.tvhome.feature.settings.ui.*

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.settings.data.Config
import com.gathin.tvhome.service.InputKeyService
import com.gathin.tvhome.feature.agent.coordinator.DuinchBrain
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.feature.agent.ui.AgentAssistantShortcut


class SettingsAgentSection(
    private val activity: Activity,
    private val config: Config,
    private val choices: SharedPreferences,
    private val palette: Palette
) {
    private lateinit var assistantTargetChoice: TextView
    private lateinit var viewerChoice: TextView

    fun bind(view: View) {
        initAssistantTarget(view)
        initAgentApp(view)
        initHomeAssistant(view)
        initViewer(view)
        initMenuInputs(view)
    }

    private fun initAssistantTarget(view: View) {
        assistantTargetChoice = view.findViewById(R.id.assistantTargetChoice)
        assistantTargetChoice.setCompoundDrawablesRelativeWithIntrinsicBounds(
            SettingsIcons.icon(activity, palette, R.drawable.ic_auto_awesome), null, null, null
        )
        showAssistantTarget()
        assistantTargetChoice.setOnClickListener { chooseAssistantTarget() }
    }

    private fun showAssistantTarget() {
        val target = AgentAssistantShortcut.target(activity)
        val label = AgentAssistantShortcut.targetLabel(activity, target)
        assistantTargetChoice.text = SettingsChoiceDialog.item(
            activity, activity.getString(R.string.assistant_target_title), label
        )
    }

    private fun chooseAssistantTarget() {
        val labels = Array<CharSequence>(AgentAssistantShortcut.TARGET_COUNT) {
            AgentAssistantShortcut.targetLabel(activity, it)
        }
        val selected = AgentAssistantShortcut.target(activity)
        SettingsChoiceDialog.chooseOne(
            activity, R.string.assistant_target_title, labels, null, selected, { index ->
                AgentAssistantShortcut.setTarget(activity, index)
                showAssistantTarget()
            }, config
        )
    }

    private fun initAgentApp(view: View) {
        val agentChoice = view.findViewById<TextView>(R.id.agentChoice)
        agentChoice.text = SettingsChoiceDialog.item(
            activity, activity.getString(R.string.duinch_agent_title),
            activity.getString(R.string.duinch_agent_desc)
        )
        agentChoice.setCompoundDrawablesRelativeWithIntrinsicBounds(
            SettingsIcons.icon(activity, palette, R.drawable.ic_mic), null, null, null
        )
        agentChoice.setOnClickListener {
            AgentSettingsSheet(activity, config).show()
        }
    }

    private fun initHomeAssistant(view: View) {
        val homeAssistantChoice = view.findViewById<TextView>(R.id.homeAssistantChoice)
        homeAssistantChoice.setText(R.string.widget_home_assistant)
        homeAssistantChoice.setCompoundDrawablesRelativeWithIntrinsicBounds(
            SettingsIcons.icon(activity, palette, R.drawable.ic_home_assistant), null, null, null
        )
        homeAssistantChoice.setOnClickListener {
            HomeAssistantSettingsSheet(activity, config).show()
        }
    }

    private fun initViewer(view: View) {
        viewerChoice = view.findViewById(R.id.viewerChoice)
        viewerChoice.setCompoundDrawablesRelativeWithIntrinsicBounds(
            SettingsIcons.icon(activity, palette, R.drawable.ic_person), null, null, null
        )
        showViewerChoice()
        viewerChoice.setOnClickListener { chooseViewer() }
    }

    private fun showViewerChoice() {
        val viewer = DuinchBrain.viewer(activity)
        val label = DuinchBrain.viewerLabel(activity, viewer)
        viewerChoice.text = SettingsChoiceDialog.item(
            activity, activity.getString(R.string.duinch_viewer_title), label
        )
    }

    private fun chooseViewer() {
        val labels = Array<CharSequence>(DuinchBrain.VIEWER_COUNT) {
            DuinchBrain.viewerLabel(activity, it)
        }
        val selected = DuinchBrain.viewer(activity)
        SettingsChoiceDialog.chooseOne(
            activity, R.string.duinch_viewer_title, labels, null, selected, { index ->
                DuinchBrain.setViewer(activity, index)
                showViewerChoice()
            }, config
        )
    }

    private fun initMenuInputs(view: View) {
        val menuChoice = view.findViewById<TextView>(R.id.menuInputsChoice) ?: return
        val inputManager = activity.getSystemService(TvInputManager::class.java)
        var hasInputs = false
        if (inputManager != null) {
            for (info in inputManager.tvInputList) {
                if (info.isPassthroughInput && info.type != TvInputInfo.TYPE_TUNER) {
                    hasInputs = true
                    break
                }
            }
        }
        if (!hasInputs) {
            menuChoice.visibility = View.GONE
            return
        }
        menuChoice.setCompoundDrawablesRelativeWithIntrinsicBounds(
            SettingsIcons.icon(activity, palette, R.drawable.ic_input), null, null, null
        )
        val names = arrayOf(
            activity.getString(R.string.smarthome_state_off),
            activity.getString(R.string.smarthome_state_on)
        )
        val initial = if (choices.getBoolean(InputKeyService.MENU_OPENS_INPUTS, false)) 1 else 0
        menuChoice.text = SettingsChoiceDialog.item(
            activity, activity.getString(R.string.menu_inputs), names[initial]
        )
        menuChoice.setOnClickListener {
            val selected = if (choices.getBoolean(InputKeyService.MENU_OPENS_INPUTS, false)) 1 else 0
            SettingsChoiceDialog.chooseOne(
                activity, R.string.menu_inputs,
                names.map { it as CharSequence }.toTypedArray(), null, selected, { index ->
                    choices.edit().putBoolean(
                        InputKeyService.MENU_OPENS_INPUTS, index == 1
                    ).apply()
                    menuChoice.text = SettingsChoiceDialog.item(
                        activity, activity.getString(R.string.menu_inputs), names[index]
                    )
                }, config
            )
        }
    }

    private fun open(intent: Intent) {
        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(activity, R.string.not_available, Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(activity, R.string.not_available, Toast.LENGTH_SHORT).show()
        }
    }
}
