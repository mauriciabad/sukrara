// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.garmin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.integrations.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * #199: the Garmin page links each of Sukrara's apps to its Connect IQ store listing, whatever the switch says, and
 * only once the listing is published (#247): never a link that goes nowhere.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h2400dp")
class GarminStoreLinksTest {
    @get:Rule val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun show(
        store: ConnectIqStore,
        on: Boolean,
        opened: MutableList<GarminApp> = ArrayList(),
    ) = compose.setContent {
        SukraraTheme(dark = false, dynamic = false) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                GarminContent(
                    GarminUi(GarminLinkState(on = on, garminConnect = true), store = store),
                    Instant.parse("2026-11-22T10:00:00Z"),
                    GarminActions(openStore = { opened += it }),
                )
            }
        }
    }

    @Test
    fun noListingNoLink() {
        show(ConnectIqStore(emptyMap()), on = true)
        compose.onNodeWithText(app.getString(R.string.in_garmin_store_group)).assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.in_garmin_aura_what)).assertDoesNotExist()
    }

    @Test
    fun aPublishedListingOpensFromItsRowWithTheSwitchOffToo() {
        val opened = ArrayList<GarminApp>()
        show(ConnectIqStore(mapOf(GarminApp.Field to "f1")), on = false, opened)
        compose.onNodeWithText(app.getString(R.string.in_garmin_store_group)).performScrollTo()
        compose.onNodeWithText(app.getString(R.string.in_garmin_aura_what)).assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.in_garmin_field_what)).performScrollTo().performClick()
        assertEquals(listOf(GarminApp.Field), opened)
    }
}
