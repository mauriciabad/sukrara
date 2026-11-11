// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.alerts

import app.mauri.sukrara.alerts.AlertGroup
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertMemory
import app.mauri.sukrara.alerts.AlertReading
import app.mauri.sukrara.alerts.AlertRole
import app.mauri.sukrara.alerts.AlertState
import app.mauri.sukrara.alerts.Episode
import app.mauri.sukrara.alerts.EpisodeQuiet
import app.mauri.sukrara.alerts.Firing
import app.mauri.sukrara.alerts.MirrorMemory
import app.mauri.sukrara.alerts.Side
import app.mauri.sukrara.core.model.AlertHold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What an app update waits for (#188), from the alert runtime's saved state and the hub's live summary. */
class AlertHoldsTest {
    private fun firing(kind: AlertKind) = Firing(kind, "id", 1, AlertReading(1000, 50), 54.0, 1000)

    private fun state(vararg episodes: Pair<AlertGroup, Episode>) =
        AlertState(role = AlertRole.WEARER, local = AlertMemory(episodes = mapOf(*episodes)))

    @Test fun `nothing on or showing holds nothing`() {
        assertEquals(AlertHold(), AlertHolds.of(null))
        assertTrue(AlertHolds.of(state()).quiet)
    }

    @Test fun `an alert whose notification is up holds the update`() {
        val high = Episode(Side.HIGH, 900, AlertKind.HIGH).apply { shown = firing(AlertKind.HIGH) }
        val hold = AlertHolds.of(state(AlertGroup.LEVEL to high))
        assertEquals(setOf("high"), hold.showing)
        assertFalse(hold.veryLow)
    }

    @Test fun `a very low on holds it even once answered (muted, carbs logged)`() {
        val low = Episode(Side.LOW, 900, AlertKind.VERY_LOW).apply { quiet = EpisodeQuiet.TREATMENT }
        val hold = AlertHolds.of(state(AlertGroup.LEVEL to low))
        assertTrue(hold.veryLow)
        assertFalse(hold.quiet)
        // over: the side went back in range
        low.on = false
        assertTrue(AlertHolds.of(state(AlertGroup.LEVEL to low)).quiet)
    }

    @Test fun `a follower's mirrored alert and the hub's live firing hold it too`() {
        val mirrored =
            AlertState(
                role = AlertRole.FOLLOWER_MIRROR,
                mirror =
                    MirrorMemory(
                        shown =
                            mapOf(AlertGroup.NO_DATA to firing(AlertKind.NO_DATA)),
                    ),
            )
        assertEquals(setOf("no_data"), AlertHolds.of(mirrored).showing)
        assertEquals(AlertHold(setOf("very_low"), veryLow = true), AlertHolds.of(null, live = setOf("very_low")))
    }
}
