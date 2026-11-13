// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import app.mauri.sukrara.core.data.settings.UpdateSettings
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.core.testing.InMemorySettingsRepository
import app.mauri.sukrara.core.testing.InMemoryStateStore
import app.mauri.sukrara.feature.home.banners.BannerKind
import app.mauri.sukrara.feature.home.banners.BannerMemory
import app.mauri.sukrara.feature.sharing.setup.UpdateChoice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The daily update check is opt-in on Android: the suggestion (setup's step, home's and the overview's banner) asks
 * until it is answered once — *Turn on*, *Not now*, or the switch turned off in About — and *Show closed tips* asks again.
 */
class UpdateSuggestionTest {
    private val settings = InMemorySettingsRepository()
    private val memory = InMemoryStateStore(BannerMemory())

    private fun TestScope.suggestion() = UpdateSuggestion(settings, { memory }, FakeClock(), backgroundScope)

    @Test fun `off and unanswered at first - the existing and the new installs both get asked`() =
        runTest(UnconfinedTestDispatcher()) {
            val s = suggestion()
            assertEquals(UpdateChoice.Open, s.choice.first())
            assertTrue(s.banner.first())
            assertTrue(s.checksOff.first())
        }

    @Test fun `Turn on answers it everywhere`() =
        runTest(UnconfinedTestDispatcher()) {
            val s = suggestion()
            s.turnOn()
            assertTrue(settings.flow(UpdateSettings.AutomaticChecks).first())
            assertEquals(UpdateChoice.On, s.choice.first())
            assertFalse(s.banner.first())
        }

    @Test fun `Not now keeps it off and closes the tip until Undo or Show closed tips`() =
        runTest(UnconfinedTestDispatcher()) {
            val s = suggestion()
            s.setup().notNow()
            assertEquals(UpdateChoice.Off, s.choice.first())
            assertEquals(1, memory.data.first().closed)
            s.restore()
            assertEquals(UpdateChoice.Open, s.choice.first())
            s.dismiss()
            assertEquals(UpdateChoice.Off, s.choice.first())
            memory.update { it.restoreAll() }
            assertTrue(s.banner.first())
        }

    @Test fun `turning the switch off in About closes the tip - a deliberate off isn't asked about`() =
        runTest(UnconfinedTestDispatcher()) {
            val s = suggestion()
            s.start()
            settings.set(UpdateSettings.AutomaticChecks, true)
            assertFalse(memory.data.first().dismissed(BannerKind.TurnOnUpdates, FakeClock().now()))
            settings.set(UpdateSettings.AutomaticChecks, false)
            assertEquals(UpdateChoice.Off, s.choice.first())
        }
}
