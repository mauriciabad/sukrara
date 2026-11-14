// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.alerts

import app.mauri.sukrara.alerts.AlertGroup
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * #195: an alert the person swiped or tapped away stays away until its next ring, and only then: the dismissal is saved
 * with the alert record (a restart doesn't bring it back silently); a post Android hasn't listed yet isn't taken for a
 * swipe; the swipe of a notification a new ring already replaced (its delete intent's firing) changes nothing.
 */
class AlertDismissalTest {
    private val rig = AlertRig()
    private val t0 = rig.clock.now().epochSecond
    private var runtime = rig.runtime()

    private fun reading(
        mgdl: Int,
        s: Long,
    ) = runBlocking {
        rig.clock.instant = Instant.ofEpochSecond(t0 + s)
        runtime.onState(rig.wearerState(latest = rig.reading(mgdl)).copy(monitoringStartedAt = Instant.ofEpochSecond(t0)))
    }

    private val posts get() = rig.presenter.posts

    init {
        runBlocking { rig.store.setSettings(AlertSettings()) }
    }

    @Test fun `a swiped alert stays away after a restart`() {
        reading(65, 0)
        val id = posts.single().id
        runBlocking { runtime.dismissed(AlertKind.LOW.wire, id) }
        rig.presenter.gone += AlertGroup.LEVEL
        val before = posts.size
        rig.clock.instant = Instant.ofEpochSecond(t0 + 30)
        runtime = rig.runtime() // a process death: a new runtime over the same stores
        runBlocking { runtime.start() }
        assertEquals("nothing resumed: it was swiped", before, posts.size)
        reading(60, 60)
        assertEquals("nor updated", before, posts.size)
        reading(58, 300)
        assertTrue("its repeat rings it back", posts.last().let { !it.silent && it.mgdl == 58 })
    }

    @Test fun `a post Android hasn't listed yet isn't a swipe`() {
        reading(65, 0)
        rig.presenter.gone += AlertGroup.LEVEL // not in the active list yet, seconds after our own post
        reading(75, 3) // back in range: it goes
        reading(66, 6) // low again: shown silently, not taken for swiped
        assertEquals(66, posts.last().mgdl)
        assertTrue(posts.last().silent)
    }

    @Test fun `the swipe of a notification a new ring replaced changes nothing`() {
        reading(65, 0)
        val first = posts.single().id
        reading(64, 300) // the 5-min repeat: a new firing
        assertTrue(posts.last().let { !it.silent && it.id != first })
        runBlocking { runtime.dismissed(AlertKind.LOW.wire, first) } // the old one's delete intent, late
        reading(62, 360)
        assertEquals("the new one keeps updating", 62, posts.last().mgdl)
    }
}
