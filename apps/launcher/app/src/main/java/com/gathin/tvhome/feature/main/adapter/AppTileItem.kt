package com.gathin.tvhome.feature.main.adapter

import android.content.ComponentName
import android.graphics.drawable.Drawable


class Entry(
    @JvmField val kind: Int,
    @JvmField val label: String,
    @JvmField val component: ComponentName,
    @JvmField val banner: Drawable?,
    @JvmField val icon: Drawable?,
    caption: String?,
    accent: Int
) {
    @JvmField val pkg: String = component.packageName
    @JvmField val name: String = if (caption == null) label else "$label \u00B7 $caption"
    @JvmField var ambient: Int = accent

    constructor(label: String, component: ComponentName, banner: Drawable?, icon: Drawable?) :
            this(APP, label, component, banner, icon, null, 0)

    companion object {
        const val APP = 0
        const val HOME_SETTINGS = 1
        const val CARD = 2
        const val EMPTY = 3
    }
}
