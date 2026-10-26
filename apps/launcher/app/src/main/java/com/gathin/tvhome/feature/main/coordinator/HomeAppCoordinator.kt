package com.gathin.tvhome.feature.main.coordinator

import android.content.Context
import com.gathin.tvhome.feature.main.data.Cards
import com.gathin.tvhome.feature.main.data.LibraryApps
import com.gathin.tvhome.feature.main.data.Order
import com.gathin.tvhome.feature.main.data.CardFeature
import com.gathin.tvhome.feature.runningapps.data.AppRepository
import com.gathin.tvhome.feature.main.adapter.AppAdapter


class HomeAppCoordinator(
    private val context: Context,
    private val appRepository: AppRepository
) {
    private var cards: List<Cards.Card> = emptyList()

    fun loadTiles(
        adapter: AppAdapter,
        hiddenApps: Set<String>,
        moduleCardsEnabled: Boolean
    ): Boolean {
        val none = emptyList<Cards.Card>()
        cards = if (moduleCardsEnabled) {
            CardFeature.run(
                { Cards.discover(context, hiddenApps, Cards.load(Cards.storeFile(context))) },
                none
            )
        } else {
            none
        }
        val shown = if (cards.isEmpty()) {
            none
        } else {
            CardFeature.run({ Cards.shown(cards) }, none)
        }
        return adapter.reload(shown)
    }

    fun getCards(): List<Cards.Card> = cards

    fun saveReorder(adapter: AppAdapter) {
        val count = adapter.appCount()
        val order = ArrayList<String>(count)
        for (i in 0 until count) {
            val key = adapter.keyAt(i)
            if (!key.isNullOrEmpty()) {
                order.add(key)
            }
        }
        Order.save(Order.file(context), Order("", order))
    }
}
