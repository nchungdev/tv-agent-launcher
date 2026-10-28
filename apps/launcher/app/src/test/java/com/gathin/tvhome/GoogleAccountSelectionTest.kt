package com.gathin.tvhome

import com.gathin.tvhome.feature.account.data.GoogleAccountSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleAccountSelectionTest {
    @Test
    fun keepsChosenAccountWhenSystemOrderChanges() {
        assertEquals(
            "chosen@example.com",
            GoogleAccountSelection.select(
                listOf("other@example.com", "chosen@example.com"), "chosen@example.com"
            )
        )
    }

    @Test
    fun doesNotShowRemovedOrHiddenAccount() {
        assertNull(GoogleAccountSelection.select(emptyList(), "removed@example.com"))
    }

    @Test
    fun doesNotGuessWhichOfMultipleAccountsIsActive() {
        assertNull(
            GoogleAccountSelection.select(
                listOf("one@example.com", "two@example.com"), "removed@example.com"
            )
        )
    }

    @Test
    fun showsOnlyVisibleAccountWithoutRequiringSelection() {
        assertEquals(
            "visible@example.com",
            GoogleAccountSelection.select(listOf("visible@example.com"), null)
        )
    }
}
