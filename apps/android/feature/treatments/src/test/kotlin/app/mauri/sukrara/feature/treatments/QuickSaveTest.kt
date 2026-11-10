// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.treatments

import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.core.testing.InMemorySettingsRepository
import app.mauri.sukrara.core.testing.InMemoryTreatmentsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** The quick dialog's Save with *Undo*, and the draft the lock-screen countdown keeps (#100). */
@OptIn(ExperimentalCoroutinesApi::class)
class QuickSaveTest {
    private val t0 = Instant.parse("2026-06-15T12:00:00Z")
    private val clock = FakeClock(t0)
    private val repo = InMemoryTreatmentsRepository()
    private val glucose = MutableStateFlow(GlucoseState.Initial.copy(role = Role.Wearer))
    private val notices = mutableListOf<String>()
    private val notice =
        object : DraftNotice {
            override fun show(summary: String) {
                notices += "show:$summary"
            }

            override fun clear() {
                notices += "clear"
            }
        }
    private val hints =
        object : AlertHints {
            override fun lowNow() = false

            override val quiet = flowOf(QuietRules.NONE)
        }
    private val drafts = MemoryDraftStore()

    private fun TestScope.module() =
        TreatmentsModule(
            repo,
            LocalTreatmentSync(),
            clock,
            glucose,
            InMemorySettingsRepository(),
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            hints,
            drafts = drafts,
            draftNotice = notice,
        )

    private suspend fun visible() = repo.between(Instant.EPOCH, t0.plus(Duration.ofDays(1))).first()

    @Test fun undoTakesBackWhatThatSaveStored() =
        runTest {
            val m = module()
            m.save(listOf(NewEntry(TreatmentKind.RapidInsulin, 2.0)), null)
            val saved = m.save(listOf(NewEntry(TreatmentKind.RapidInsulin, 4.5), NewEntry(TreatmentKind.Carbs, 30.0)), null)
            assertNotNull(saved)
            assertEquals(3, visible().size)
            saved!!.undo()
            // only this save's entries go, as newer deleted versions (synced like any delete)
            assertEquals(listOf(2.0), visible().map { it.amount })
            assertTrue(repo.changedSince(Instant.EPOCH).count { it.treatment.deleted } == 2)
        }

    @Test fun nothingToUndoWhenThisPhoneMayNotAdd() =
        runTest {
            glucose.value = GlucoseState.Initial.copy(role = Role.Follower)
            assertNull(module().save(listOf(NewEntry(TreatmentKind.Carbs, 15.0)), null))
            assertTrue(visible().isEmpty())
        }

    @Test fun aDraftIsKeptOfferedAndDropped() =
        runTest {
            val m = module()
            val form = TreatmentFormState(rapid = "4.5", carbs = "30", note = "Pasta", ago = 10)
            val draft = TreatmentDraft.of(form, t0)!!
            // "10 min ago" is resolved when it is kept: the entry stays at the time it happened
            assertEquals(t0.minus(Duration.ofMinutes(10)), draft.at)
            m.keepDraft(draft, "4.5u rapid insulin · 30g carbs · Pasta")
            assertEquals(listOf("show:4.5u rapid insulin · 30g carbs · Pasta"), notices)
            val offered = m.draft()!!
            assertEquals("4.5", offered.form().rapid)
            assertEquals(t0.minus(Duration.ofMinutes(10)), offered.form().at)
            m.dropDraft()
            assertEquals("clear", notices.last())
            assertNull(m.draft())
        }

    @Test fun aDraftIsOfferedForTwelveHours() =
        runTest {
            val m = module()
            m.keepDraft(TreatmentDraft.of(TreatmentFormState(carbs = "15"), t0)!!, "15g carbs")
            clock.instant = t0.plus(TreatmentDraft.KEEP).minusSeconds(1)
            assertNotNull(m.draft())
            clock.instant = t0.plus(TreatmentDraft.KEEP)
            assertNull(m.draft())
        }

    @Test fun nothingTypedKeepsNothing() {
        assertNull(TreatmentDraft.of(TreatmentFormState(), t0))
        assertTrue(!TreatmentDraft.EMPTY.offered(t0))
    }
}
