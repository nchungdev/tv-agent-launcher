package com.gathin.tvhome.core.design

import android.content.Context
import android.content.res.Resources
import com.gathin.tvhome.R

object Fonts {

    private val FAMILIES = intArrayOf(
        R.font.sans_display, R.font.sans_headline, R.font.sans_medium, R.font.sans_regular
    )

    @JvmStatic
    fun preload(context: Context) {
        val res = context.resources
        val loaders = Array(FAMILIES.size) { i ->
            val family = FAMILIES[i]
            Thread({
                try {
                    res.getFont(family)
                } catch (_: Resources.NotFoundException) {
                }
            }, "font-preload")
        }
        for (loader in loaders) loader.start()
        try {
            for (loader in loaders) loader.join()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
