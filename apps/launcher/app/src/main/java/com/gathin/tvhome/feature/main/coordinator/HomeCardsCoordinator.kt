package com.gathin.tvhome.feature.main.coordinator

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.main.data.Cards


class HomeCardsCoordinator(
    private val activity: Activity,
    private val onDecideCard: (Cards.Card, Boolean) -> Unit
) {
    private var cardPrompt: Dialog? = null

    fun showCardPrompt(cards: List<Cards.Card>, root: ViewGroup) {
        val card = Cards.firstPending(cards) ?: return
        val inflater = LayoutInflater.from(activity)
        val panel = inflater.inflate(R.layout.dialog_card_prompt, root, false)
        val density = activity.resources.displayMetrics.density

        panel.findViewById<TextView>(R.id.title)?.text =
            activity.getString(R.string.card_prompt_title, card.appLabel)
        panel.findViewById<TextView>(R.id.body)?.text = when (card.state) {
            Cards.State.RESIGNED -> activity.getString(R.string.card_prompt_resigned, card.appLabel)
            Cards.State.RELABELLED ->
                activity.getString(R.string.card_prompt_relabelled, card.appLabel)
            else -> activity.getString(R.string.card_prompt_body, card.appLabel)
        }
        val names = panel.findViewById<TextView>(R.id.names)
        if (Cards.fold(card.label) == Cards.fold(card.appLabel)) {
            names?.visibility = View.GONE
        } else {
            names?.text = activity.getString(R.string.card_prompt_names, card.label, card.appLabel)
        }
        panel.findViewById<TextView>(R.id.identity)?.text = activity.getString(
            R.string.card_prompt_identity,
            card.pkg,
            Cards.shortDigest(card.digest)
        )
        val btnKeep = panel.findViewById<TextView>(R.id.btnKeep)
        val btnShow = panel.findViewById<TextView>(R.id.btnShow)

        val dialog = Dialog(activity).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(panel)
            setCanceledOnTouchOutside(false)
        }
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setLayout((440 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            window.setDimAmount(0.6f)
        }
        var decided = false
        val choose = View.OnClickListener { view ->
            if (decided) return@OnClickListener
            decided = true
            cardPrompt = null
            dialog.dismiss()
            onDecideCard(card, view.id == R.id.btnShow)
        }
        btnKeep?.setOnClickListener(choose)
        btnShow?.setOnClickListener(choose)
        cardPrompt = dialog
        dialog.show()
        btnKeep?.requestFocus()
    }
}
