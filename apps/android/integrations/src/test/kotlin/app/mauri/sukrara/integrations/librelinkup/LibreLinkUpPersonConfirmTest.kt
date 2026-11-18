// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.librelinkup

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.mauri.sukrara.core.data.libreview.LluPerson
import app.mauri.sukrara.core.model.OnlineHealth
import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Settings › Source with LibreLinkUp: another person than the one whose readings this phone holds is read only after
 * *Read …'s glucose* (docs/44 Appendix G finding 6, the Mac's `LibreLinkUpConfirm`); *Cancel* keeps the person read.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LibreLinkUpPersonConfirmTest {
    @get:Rule val compose = createComposeRule()

    private val now = Instant.parse("2026-11-18T12:00:00Z")
    private val anaRead =
        LluSourceView(
            signedIn = true,
            email = "parent@example.com",
            patient = "ana",
            name = "Ana Example",
            people = listOf(LluPerson("ana", "Ana Example"), LluPerson("leo", "Leo Example")),
            health = OnlineHealth.Live,
            newest = now.minusSeconds(60),
            held = "ana",
        )
    private val calls = mutableListOf<String>()
    private val actions =
        LibreLinkUpSourceActions(choose = { calls += "choose:$it" }, keepPerson = { calls += "keep" })

    private fun show(view: LluSourceView) =
        compose.setContent { SukraraTheme { LibreLinkUpSourceRows(view, Person.Theirs, now, actions) } }

    @Test
    fun `picking another person asks first, and Cancel reads no one new`() {
        show(anaRead)
        compose.onNodeWithText("Person").performClick()
        compose.onNodeWithText("Leo Example").performClick()
        compose.onNodeWithText("Read Leo Example's glucose?").assertExists()
        compose.onNodeWithText("Ana Example's readings on this phone", substring = true).assertExists()
        assertEquals(emptyList<String>(), calls)
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Read Leo Example's glucose?").assertDoesNotExist()
        assertEquals(emptyList<String>(), calls)

        compose.onNodeWithText("Person").performClick()
        compose.onNodeWithText("Leo Example").performClick()
        compose.onNodeWithText("Read Leo Example's glucose").performClick()
        assertEquals(listOf("choose:leo"), calls)
    }

    @Test
    fun `the person read again is no change, nothing asked`() {
        show(anaRead.copy(patient = "", name = "", held = "ana", health = OnlineHealth.NotShared))
        compose.onNodeWithText("Person").performClick()
        compose.onNodeWithText("Ana Example").performClick()
        assertEquals(listOf("choose:ana"), calls)
    }

    @Test
    fun `another person a sign-in found asks the same, Cancel keeps the person read`() {
        show(anaRead.copy(pending = LluPerson("leo", "Leo Example")))
        compose.onNodeWithText("Read Leo Example's glucose?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(listOf("keep"), calls)
    }
}
