// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.garmin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
 * #199: on the Garmin page each of Sukrara's apps on no watch is a *Connect IQ store* row that opens its store page,
 * whatever the switch says; one on a watch is listed under it instead, never both.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h2400dp")
class GarminStoreLinksTest {
    @get:Rule val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val now = Instant.parse("2026-11-22T10:00:00Z")

    private fun watch(vararg apps: GarminApp) =
        GarminWatch.of(
            265L,
            "Forerunner 265",
            true,
            GarminApp.entries.flatMap { it.ids }.associateWith { id -> AppLink(installed = apps.any { it.id == id }) },
        )

    private fun show(
        s: GarminLinkState,
        opened: MutableList<GarminApp> = ArrayList(),
    ) = compose.setContent {
        SukraraTheme(dark = false, dynamic = false) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                GarminContent(GarminUi(s), now, GarminActions(openStore = { opened += it }))
            }
        }
    }

    private fun once(name: GarminApp) = compose.onAllNodesWithText(app.getString(name.nameRes)).fetchSemanticsNodes().size

    @Test
    fun withTheSwitchOffEveryAppIsAStoreRowThatOpensItsPage() {
        val opened = ArrayList<GarminApp>()
        show(GarminLinkState(on = false, garminConnect = true), opened)
        compose.onNodeWithText(app.getString(R.string.in_garmin_store_group)).performScrollTo()
        GarminApp.entries.forEach { assertEquals(1, once(it)) }
        compose.onNodeWithText(app.getString(R.string.in_garmin_field_what)).performScrollTo().performClick()
        assertEquals(listOf(GarminApp.Field), opened)
    }

    @Test
    fun anAppOnTheWatchIsListedUnderItTheRestInTheStore() {
        val opened = ArrayList<GarminApp>()
        show(GarminLinkState(garminConnect = true, answering = true, watches = listOf(watch(GarminApp.Aura))), opened)
        // the face under the watch, not in the store; the watch app and the field only in the store
        compose.onNodeWithText(app.getString(R.string.in_garmin_faces_group)).performScrollTo()
        compose.onNodeWithText(app.getString(R.string.in_garmin_aura_what)).assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.in_garmin_sukrara_group)).assertDoesNotExist()
        GarminApp.entries.forEach { assertEquals(1, once(it)) }
        compose.onNodeWithText(app.getString(R.string.in_garmin_app_what)).performScrollTo().performClick()
        assertEquals(listOf(GarminApp.App), opened)
    }

    @Test
    fun withEveryAppOnTheWatchThereIsNoStoreGroup() {
        show(GarminLinkState(garminConnect = true, answering = true, watches = listOf(watch(*GarminApp.entries.toTypedArray()))))
        compose.onNodeWithText(app.getString(R.string.in_garmin_store_group)).assertDoesNotExist()
        GarminApp.entries.forEach { assertEquals(1, once(it)) }
    }
}
