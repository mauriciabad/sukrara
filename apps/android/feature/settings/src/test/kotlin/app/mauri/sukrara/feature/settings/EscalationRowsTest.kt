// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.core.model.AlertUiState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Appendix G finding 9: the escalation switch showed "on" where it does nothing. The wearer's *tell followers* only
 * where this phone owns your glucose; a follower's switch only while it follows with *Their settings*; none elsewhere
 * (someone else's LibreLinkUp or Nightscout, a follow with this phone's own settings).
 */
class EscalationRowsTest {
    private fun ui(
        owner: Boolean,
        follower: Boolean = false,
        mirrored: Boolean = false,
    ) = AlertsUi(AlertSettings(), AlertUiState(), mirrored = mirrored, follower = follower, owner = owner)

    @Test
    fun `the rows act where they show`() {
        assertEquals(Escalation.TellFollowers, ui(owner = true).escalation)
        assertEquals(Escalation.TakeTheirs, ui(owner = false, follower = true, mirrored = true).escalation)
        assertEquals(Escalation.None, ui(owner = false, follower = true, mirrored = false).escalation)
        assertEquals(Escalation.None, ui(owner = false).escalation)
    }
}
