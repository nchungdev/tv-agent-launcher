package com.gathin.tvhome.feature.account.data

object GoogleAccountSelection {
    fun select(visibleNames: List<String>, preferredName: String?): String? {
        return visibleNames.firstOrNull { it == preferredName } ?: visibleNames.singleOrNull()
    }
}
