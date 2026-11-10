// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.upload

import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.TreatmentOrigin
import app.mauri.sukrara.core.model.storage.TreatmentRecord
import app.mauri.sukrara.core.model.storage.UploadHold
import app.mauri.sukrara.integrations.libreview.LibreViewQueue
import app.mauri.sukrara.integrations.nightscout.NightscoutQueue
import app.mauri.sukrara.integrations.nightscout.NsOptions
import app.mauri.sukrara.integrations.nightscout.NsState
import app.mauri.sukrara.integrations.tidepool.TidepoolQueue
import app.mauri.sukrara.integrations.tidepool.TpRoleState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * A pen entry waits for its session to close before LibreView, Nightscout or Tidepool get it (docs/29): until 10 min
 * after its last dose a top-up can still grow it, and sending it earlier would upload one version and then change it.
 */
class UploadHoldTest {
    private val now = Instant.parse("2026-10-20T13:30:00Z")
    private val nowS = now.epochSecond

    private fun record(
        id: String,
        minutesAgo: Long,
    ) = TreatmentRecord(
        Treatment(
            id,
            TreatmentKind.RapidInsulin,
            8.5,
            now.minusSeconds(minutesAgo * 60),
            modifiedAt = now.minusSeconds(minutesAgo * 60),
            origin = TreatmentOrigin.Phone,
        ),
        penJson = "{\"pen\":\"AB1\"}",
    )

    // the pen's last dose was when the entry was logged; its session closes 10 min later
    private val hold = UploadHold { r -> r.treatment.at.epochSecond + 600 }
    private val open = record("open", minutesAgo = 4)
    private val closed = record("closed", minutesAgo = 11)

    @Test fun libreViewWaitsForTheSession() {
        val sent = LibreViewQueue.entries(listOf(open, closed), emptyMap(), from = 0, nowS = nowS, hold = hold)
        assertEquals(listOf("closed"), sent.map { it.id })
        assertEquals(2, LibreViewQueue.entries(listOf(open, closed), emptyMap(), from = 0, nowS = nowS + 360, hold = hold).size)
    }

    @Test fun nightscoutWaitsForTheSession() {
        val plan = NightscoutQueue.plan(emptyList(), listOf(open, closed), null, NsState(), NsOptions(), nowS, hold)
        assertEquals(listOf("closed"), plan.upserts.map { it.treatment.id })
    }

    @Test fun tidepoolWaitsForTheSession() {
        val plan =
            TidepoolQueue.plan(emptyList(), listOf(open, closed), TpRoleState(), false, true, notes = true, nowS = nowS, hold = hold)
        assertEquals(listOf("closed"), plan.upserts.map { it.id })
    }

    @Test fun withoutPensNothingWaits() {
        val plan = NightscoutQueue.plan(emptyList(), listOf(open, closed), null, NsState(), NsOptions(), nowS)
        assertEquals(setOf("open", "closed"), plan.upserts.map { it.treatment.id }.toSet())
    }
}
