// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.treatments

import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.TreatmentOrigin
import app.mauri.sukrara.core.model.storage.TreatmentDelivery
import app.mauri.sukrara.core.model.storage.TreatmentRecord
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.core.testing.InMemorySettingsRepository
import app.mauri.sukrara.core.testing.InMemoryTreatmentsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Editing an entry from home's list (#75) and *Retry* on a writer's entry nothing took. */
@OptIn(ExperimentalCoroutinesApi::class)
class EditAndRetryTest {
    private val t0 = Instant.parse("2026-06-15T12:00:00Z")
    private val clock = FakeClock(t0)
    private val repo = InMemoryTreatmentsRepository()

    private class Sync(
        writer: TreatmentWriter?,
    ) : TreatmentSync {
        override val writer = MutableStateFlow(writer)
        val published = mutableListOf<TreatmentRecord>()

        override suspend fun publish(record: TreatmentRecord) {
            published += record
        }
    }

    private val hints =
        object : AlertHints {
            override fun lowNow() = false

            override val quiet = flowOf(QuietRules.NONE)
        }

    private fun TestScope.module(
        sync: TreatmentSync,
        role: Role,
    ) = TreatmentsModule(
        repo,
        sync,
        clock,
        MutableStateFlow(GlucoseState.Initial.copy(role = role)),
        InMemorySettingsRepository(),
        CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        hints,
    )

    private val stored =
        Treatment("a", TreatmentKind.RapidInsulin, 4.0, t0.minusSeconds(1800), "", t0.minusSeconds(1800), origin = TreatmentOrigin.Phone)

    @Test fun anEditChangesAmountTimeAndNoteAndUndoBringsItBack() =
        runTest {
            repo.upsertIfNewer(TreatmentRecord(stored))
            val sync = Sync(TreatmentWriter(null, throughOutbox = false))
            val m = module(sync, Role.Wearer)
            val t = m.editable("a")!!
            val earlier = t0.minusSeconds(2400)
            val saved = m.edit(t, NewEntry(TreatmentKind.RapidInsulin, 4.5, "pizza"), earlier)!!
            val now = repo.get("a")!!.treatment
            assertEquals(4.5, now.amount, 0.0)
            assertEquals("pizza", now.note)
            assertEquals(earlier, now.at)
            assertEquals(TreatmentKind.RapidInsulin, now.kind)
            assertTrue(now.modifiedAt > stored.modifiedAt)
            saved.undo()
            val back = repo.get("a")!!.treatment
            assertEquals(4.0, back.amount, 0.0)
            assertEquals(stored.at, back.at)
            // the undo is a newer version too, so it wins on every device
            assertTrue(back.modifiedAt > now.modifiedAt)
            assertEquals(2, sync.published.size)
        }

    @Test fun onlyWhatThisPhoneMayChangeOpensForEditing() =
        runTest {
            repo.upsertIfNewer(TreatmentRecord(stored))
            // a follower may change only the entries it created: not the wearer's
            val m = module(Sync(TreatmentWriter("ana", throughOutbox = true)), Role.Follower)
            assertNull(m.editable("a"))
            assertNull(m.editable("gone"))
        }

    @Test fun theEditFormHoldsTheEntry() {
        val form = TreatmentFormState.editing(stored.copy(kind = TreatmentKind.Carbs, amount = 30.0, note = "toast"))
        assertEquals("30", form.carbs)
        assertEquals("", form.rapid)
        assertEquals("toast", form.note)
        assertEquals(stored.at, form.at)
        assertEquals(listOf(NewEntry(TreatmentKind.Carbs, 30.0, "toast")), form.entries)
        form.carbs = "600"
        assertNull(form.entries)
        form.carbs = ""
        assertFalse(form.canSave)
    }

    @Test fun retrySendsAFailedEntryAgain() =
        runTest {
            val sync = Sync(TreatmentWriter("ana", throughOutbox = true))
            val log = TreatmentLog(repo, sync, clock)
            repo.upsertIfNewer(
                TreatmentRecord(stored.copy(origin = TreatmentOrigin.Follower), creator = "ana", delivery = TreatmentDelivery.Pending),
            )
            // only an entry nothing took
            assertFalse(log.retry("a"))
            repo.replace(repo.get("a")!!.copy(delivery = TreatmentDelivery.Failed))
            assertTrue(log.retry("a"))
            assertEquals(TreatmentDelivery.Pending, repo.get("a")!!.delivery)
            // the same version, sent again
            assertEquals(
                stored.modifiedAt,
                sync.published
                    .single()
                    .treatment.modifiedAt,
            )
            assertFalse(log.retry("missing"))
        }
}
