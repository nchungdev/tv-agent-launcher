package com.gathin.tvhome.feature.main.data



object DockSlots {

    @JvmStatic
    fun empty(docked: Int, dockSize: Int, appCount: Int, fillDock: Boolean): Int {
        if (docked <= 0 || appCount <= docked || fillDock || docked >= dockSize) return 0
        return dockSize - docked
    }

    @JvmStatic
    fun focusable(position: Int, docked: Int, emptySlots: Int): Int {
        if (emptySlots <= 0) return position
        if (position in docked until (docked + emptySlots)) {
            return (docked - 1).coerceAtLeast(0)
        }
        return position
    }
}
