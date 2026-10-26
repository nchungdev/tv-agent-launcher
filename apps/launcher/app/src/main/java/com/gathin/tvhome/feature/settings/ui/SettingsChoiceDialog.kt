package com.gathin.tvhome.feature.settings.ui
import com.gathin.tvhome.feature.settings.data.*
import com.gathin.tvhome.feature.settings.widget.*

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.TypefaceSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.settings.data.Config
import com.gathin.tvhome.feature.main.data.LocalAdb
import com.gathin.tvhome.core.design.M3Skin
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.core.design.Shapes
import java.util.concurrent.Executor
import java.util.function.IntConsumer


object SettingsChoiceDialog {

    const val TURN_OFF_COMMAND = "input keyevent KEYCODE_POWER"
    const val RESTART_COMMAND = "reboot"

    fun chooseOne(
        activity: Activity,
        titleRes: Int,
        labels: Array<CharSequence>,
        captions: Array<CharSequence>?,
        selected: Int,
        choose: IntConsumer,
        config: Config
    ) {
        val p = Palette.currentOrBaseline()
        val d = activity.resources.displayMetrics.density
        val panel = dialogPanel(activity, titleRes, p, d)
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, -2))

        var dialog: Dialog? = null
        val inflater = LayoutInflater.from(activity)
        for (i in labels.indices) {
            val index = i
            val row = inflater.inflate(R.layout.item_input, list, false)
            row.findViewById<TextView>(R.id.title).text = labels[i]
            val status = row.findViewById<TextView>(R.id.status)
            if (captions != null && captions[i].isNotEmpty()) {
                status.text = captions[i]
            } else {
                status.visibility = View.GONE
            }
            row.findViewById<View>(R.id.icon).visibility = View.GONE
            val dot = row.findViewById<View>(R.id.dot)
            dot.visibility = if (i == selected) View.VISIBLE else View.GONE
            dot.background = GradientDrawable().apply {
                setColor(p.primary)
                cornerRadius = 100f
            }
            row.isFocusableInTouchMode = true
            row.setOnFocusChangeListener { _, hasFocus ->
                (dot.background as? GradientDrawable)?.setColor(
                    if (hasFocus) p.inverseOnSurface else p.primary
                )
            }
            row.setOnClickListener {
                choose.accept(index)
                dialog?.dismiss()
            }
            list.addView(row)
        }

        dialog = showDialog(activity, panel, p, config, d)
        list.getChildAt(selected.coerceIn(0, labels.size - 1))?.requestFocus()
    }

    fun editName(
        activity: Activity,
        config: Config,
        choices: SharedPreferences,
        nameKey: String,
        defaultName: String,
        onChanged: () -> Unit
    ) {
        val p = Palette.currentOrBaseline()
        val d = activity.resources.displayMetrics.density
        val panel = dialogPanel(activity, R.string.your_name, p, d)

        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            val pad = (12 * d).toInt()
            setPadding(pad, pad, pad, pad)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(p.onSurface)
            setHintTextColor(p.onSurfaceVariant)
            hint = defaultName
            val current = choices.getString(nameKey, "") ?: ""
            setText(current)
            setSelection(length())
            background = GradientDrawable().apply {
                setColor(p.surfaceContainerHighest)
                cornerRadius = Shapes.SMALL * d
            }
        }
        val inputLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (16 * d).toInt() }
        panel.addView(input, inputLp)

        val buttons = dialogButtonRow(activity, panel, d)
        val save = dialogButton(activity, R.string.save, d)
        val cancel = dialogButton(activity, android.R.string.cancel, d)
        buttons.addView(save)
        buttons.addView(cancel)
        (cancel.layoutParams as LinearLayout.LayoutParams).marginStart = (8 * d).toInt()

        val dialog = showDialog(activity, panel, p, config, d)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        save.setOnClickListener {
            choices.edit().putString(nameKey, input.text.toString().trim()).apply()
            dialog.dismiss()
            onChanged()
        }
        cancel.setOnClickListener { dialog.dismiss() }
        input.requestFocus()
    }

    fun confirmPower(
        activity: Activity,
        config: Config,
        worker: Executor,
        onCommandRun: () -> Unit
    ) {
        val p = Palette.currentOrBaseline()
        val d = activity.resources.displayMetrics.density
        val panel = dialogPanel(activity, R.string.power_off_action, p, d)

        val message = TextView(activity).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(p.onSurfaceVariant)
            text = activity.getString(R.string.power_off_desc)
        }
        val msgLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (8 * d).toInt() }
        panel.addView(message, msgLp)

        val buttons = dialogButtonRow(activity, panel, d)
        val turnOff = dialogButton(activity, R.string.power_off_action, d)
        val restart = dialogButton(activity, R.string.reboot, d)
        val cancel = dialogButton(activity, android.R.string.cancel, d)
        buttons.addView(turnOff)
        buttons.addView(restart)
        buttons.addView(cancel)
        (restart.layoutParams as LinearLayout.LayoutParams).marginStart = (8 * d).toInt()
        (cancel.layoutParams as LinearLayout.LayoutParams).marginStart = (8 * d).toInt()

        val dialog = showDialog(activity, panel, p, config, d)
        turnOff.setOnClickListener {
            dialog.dismiss()
            onCommandRun()
            runCommand(activity, worker, TURN_OFF_COMMAND, R.string.reboot_desc)
        }
        restart.setOnClickListener {
            dialog.dismiss()
            onCommandRun()
            runCommand(activity, worker, RESTART_COMMAND, R.string.reboot_desc)
        }
        cancel.setOnClickListener { dialog.dismiss() }
        turnOff.requestFocus()
    }

    fun runCommand(activity: Activity, worker: Executor, command: String, errorRes: Int) {
        val adb = LocalAdb(activity)
        worker.execute {
            try {
                adb.shell(command)
            } catch (_: Exception) {
                activity.runOnUiThread {
                    Toast.makeText(activity, errorRes, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun dialogPanel(context: Context, titleRes: Int, p: Palette, d: Float): LinearLayout {
        val panel = LinearLayout(context).apply {
            tag = "m3:panel"
            orientation = LinearLayout.VERTICAL
            val pad = (20 * d).toInt()
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(p.surfaceContainerHigh)
                cornerRadius = Shapes.EXTRA_LARGE * d
            }
            elevation = 6 * d
        }
        val title = TextView(context).apply {
            setText(titleRes)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(p.onSurface)
            typeface = context.resources.getFont(R.font.sans_medium)
        }
        panel.addView(
            title,
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        return panel
    }

    fun dialogButtonRow(context: Context, panel: LinearLayout, d: Float): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (20 * d).toInt() }
        panel.addView(row, lp)
        return row
    }

    fun dialogButton(context: Context, labelRes: Int, d: Float): TextView {
        val btn = TextView(context).apply {
            setText(labelRes)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            tag = "m3:button"
            val h = (12 * d).toInt()
            setPadding(h, (8 * d).toInt(), h, (8 * d).toInt())
        }
        btn.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return btn
    }

    fun showDialog(
        activity: Activity,
        panel: View,
        p: Palette,
        config: Config,
        d: Float
    ): Dialog {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(panel)
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(0))
            window.setLayout((420 * d).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            window.setDimAmount(0.55f)
        }
        M3Skin.apply(panel, p, config.animations, config.reducedMotion)
        dialog.show()
        return dialog
    }

    fun item(activity: Activity, title: String, detail: String): CharSequence {
        val builder = SpannableStringBuilder(title)
        if (detail.isNotEmpty()) {
            builder.append('\n').append(detail)
            val start = title.length + 1
            val end = builder.length
            builder.setSpan(
                RelativeSizeSpan(0.78f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            val tf = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                TypefaceSpan(activity.resources.getFont(R.font.sans_regular))
            } else {
                TypefaceSpan("sans-serif")
            }
            builder.setSpan(tf, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return builder
    }
}
