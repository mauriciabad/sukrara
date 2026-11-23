// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.home

import app.mauri.sukrara.core.model.AlertReadiness
import app.mauri.sukrara.core.model.AlertRequirement
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.PenDoseInfo
import app.mauri.sukrara.core.model.PenEntryInfo
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.storage.StateStore
import app.mauri.sukrara.core.model.storage.TreatmentDelivery
import app.mauri.sukrara.core.model.storage.TreatmentRecord
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.core.testing.InMemoryStateStore
import app.mauri.sukrara.core.testing.InMemoryTreatmentsRepository
import app.mauri.sukrara.feature.home.banners.BannerKind
import app.mauri.sukrara.feature.home.banners.BannerMemory
import app.mauri.sukrara.feature.home.banners.GarminTip
import app.mauri.sukrara.feature.home.banners.GarminUpdateTip
import app.mauri.sukrara.feature.home.banners.HomeBanners
import app.mauri.sukrara.feature.home.banners.HomeEnvironment
import app.mauri.sukrara.graph.GraphConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class Deps : HomeDeps {
        override val clock = FakeClock(Samples.NOW)
        override val glucose = MutableStateFlow(Samples.wearer())
        override val treatments = InMemoryTreatmentsRepository()
        override val graphConfig = MutableStateFlow(GraphConfig())
        override val readiness: StateFlow<AlertReadiness?> = MutableStateFlow(AlertReadiness.Ready)
        override val environment = MutableStateFlow(HomeEnvironment())
        override val dataProblems = MutableStateFlow<List<DataProblem>>(emptyList())
        override val bannerMemory: StateStore<BannerMemory> = InMemoryStateStore(BannerMemory())
        override val uploadMarks = MutableStateFlow<UploadMarks?>(null)

        override fun acknowledge(problem: DataProblem) {
            dataProblems.value -= problem
        }

        override fun resumeMonitoring() = Unit

        val alertActions = mutableListOf<HomeAlertAction>()

        override fun alert(action: HomeAlertAction) {
            alertActions += action
        }

        override fun fix(requirement: AlertRequirement) = Unit

        /** What the treatment log would allow (false = a follower and the wearer's entry). */
        var mayChange = true
        val writes = mutableListOf<String>()

        override suspend fun deleteTreatment(id: String): Treatment? {
            writes += "delete $id"
            if (!mayChange) return null
            val stored = treatments.get(id) ?: return null
            treatments.upsertIfNewer(stored.copy(treatment = stored.treatment.copy(deleted = true, modifiedAt = clock.now())))
            return stored.treatment
        }

        override suspend fun mayChangeTreatment(id: String) = mayChange

        override suspend fun editNote(
            id: String,
            note: String,
        ): Treatment? {
            writes += "note $id $note"
            if (!mayChange) return null
            val stored = treatments.get(id) ?: return null
            treatments.upsertIfNewer(stored.copy(treatment = stored.treatment.copy(note = note, modifiedAt = clock.now().plusMillis(500))))
            return stored.treatment
        }

        override suspend fun undoTreatment(previous: Treatment) {
            writes += "undo ${previous.id}"
            treatments.upsertIfNewer(TreatmentRecord(previous.copy(modifiedAt = clock.now().plusSeconds(1))))
        }

        /** The pen entries the log holds (id → its doses), and whether this phone has pens to correct them. */
        val pens = mutableMapOf<String, PenEntryInfo>()
        var hasPens = true
        var correctRefused = false
        var restoreRefused = false

        override suspend fun penEntries(ids: Collection<String>) = ids.filterTo(HashSet()) { it in pens }

        override suspend fun penEntry(id: String) = pens[id]

        override fun pensEditable() = hasPens

        override suspend fun correctPen(
            shown: PenEntryInfo,
            primed: List<Boolean>,
        ): PenEntryInfo? {
            writes += "correct ${shown.id} $primed"
            return if (correctRefused) null else shown
        }

        override suspend fun restorePen(was: PenEntryInfo): Boolean {
            writes += "restore ${was.id}"
            return !restoreRefused
        }

        override suspend fun deliveries(ids: Collection<String>) =
            ids
                .mapNotNull { id ->
                    treatments.get(id)?.delivery?.takeIf { it != TreatmentDelivery.Local && it != TreatmentDelivery.Delivered }?.let {
                        id to
                            it
                    }
                }.toMap()

        override suspend fun retryTreatment(id: String) {
            writes += "retry $id"
            treatments.get(id)?.let { treatments.replace(it.copy(delivery = TreatmentDelivery.Pending)) }
        }

        override val penQuestions = MutableStateFlow<List<app.mauri.sukrara.core.model.PenQuestionInfo>>(emptyList())
        var answerRefused = false

        override suspend fun answerPen(
            question: app.mauri.sukrara.core.model.PenQuestionInfo,
            answer: app.mauri.sukrara.core.model.PenAnswer,
        ): Boolean {
            writes += "answer ${question.id} $answer"
            if (!answerRefused) penQuestions.value -= question
            return !answerRefused
        }
    }

    @Test fun `the pen question card answers the oldest question, and one that changed since says so`() =
        runTest(dispatcher) {
            val deps = Deps()

            fun q(id: String) =
                app.mauri.sukrara.core.model.PenQuestionInfo(
                    id,
                    "AB12",
                    same = false,
                    keys = listOf("k-$id"),
                    doses = listOf(PenDoseInfo("k-$id", 1000, 1.5, primed = false, incomplete = false)),
                    units = 1.5,
                    t = 1000,
                )
            deps.penQuestions.value = listOf(q("a"), q("b"))
            val vm = HomeViewModel(deps)
            backgroundScope.launch { vm.penQuestions.collect {} }
            runCurrent()
            assertEquals(listOf("a", "b"), vm.penQuestions.value.map { it.id })
            vm.answerPen(q("a"), app.mauri.sukrara.core.model.PenAnswer.Suggested)
            runCurrent()
            assertEquals(listOf("b"), vm.penQuestions.value.map { it.id })
            assertNull(vm.penNotice.value)
            deps.answerRefused = true
            vm.answerPen(
                q("b"),
                app.mauri.sukrara.core.model.PenAnswer
                    .Same(null),
            )
            runCurrent()
            assertEquals(PenNotice(PenNotice.Kind.ChangedSince), vm.penNotice.value)
            assertEquals(listOf("answer a Suggested", "answer b Same(entry=null)"), deps.writes)
        }

    @Test fun `injected hint shows after carbs without rapid insulin near them, for an hour`() {
        val now = Samples.NOW
        val carbs = Samples.treatment(id = "c", minutesAgo = 10).copy(kind = app.mauri.sukrara.core.model.TreatmentKind.Carbs)
        assertTrue(PenHints.carbsWithoutInsulin(listOf(carbs), now))
        // rapid insulin 20 min before the carbs: nothing to hint
        val rapid = Samples.treatment(id = "r", minutesAgo = 30)
        assertTrue(!PenHints.carbsWithoutInsulin(listOf(carbs, rapid), now))
        // carbs more than an hour ago: the hint is gone
        assertTrue(!PenHints.carbsWithoutInsulin(listOf(carbs.copy(at = now.minusSeconds(3700))), now))
    }

    private fun penEntry(t: Treatment) =
        PenEntryInfo(
            id = t.id,
            serial = "AB12",
            kind = "rapid",
            units = t.amount,
            primed = 1.0,
            t = t.at.epochSecond,
            note = "",
            doses =
                listOf(
                    PenDoseInfo("0", t.at.epochSecond - 60, 1.0, primed = true, incomplete = false),
                    PenDoseInfo("1", t.at.epochSecond, t.amount, primed = false, incomplete = false),
                ),
            replaced = null,
        )

    @Test fun `a pen row opens its sheet, Save corrects it and Undo puts it back`() =
        runTest(dispatcher) {
            val deps = Deps()
            val pen = Samples.treatment(id = "p1")
            val typed = Samples.treatment(id = "t2", minutesAgo = 20)
            deps.treatments.upsertIfNewer(TreatmentRecord(pen))
            deps.treatments.upsertIfNewer(TreatmentRecord(typed))
            deps.pens[pen.id] = penEntry(pen)
            val vm = HomeViewModel(deps)
            backgroundScope.launch { vm.state.collect {} }
            runCurrent()
            assertEquals(setOf(pen.id), vm.state.value.fromPen)

            vm.openPen(pen)
            runCurrent()
            val sheet = vm.penSheet.value!!
            assertEquals(penEntry(pen), sheet.entry)
            assertTrue(sheet.editable && sheet.deletable)

            vm.correctPen(listOf(true, true))
            runCurrent()
            assertNull(vm.penSheet.value)
            assertEquals(PenNotice(PenNotice.Kind.Removed, penEntry(pen)), vm.penNotice.value)
            vm.penNoticeShown(vm.penNotice.value!!)
            vm.undoPen(penEntry(pen))
            runCurrent()
            assertEquals(listOf("correct p1 [true, true]", "restore p1"), deps.writes)
            assertNull(vm.penNotice.value)

            // the entry changed meanwhile: nothing saved, and a refused Undo says so
            vm.openPen(pen)
            runCurrent()
            deps.correctRefused = true
            vm.correctPen(listOf(true, false))
            runCurrent()
            assertEquals(PenNotice(PenNotice.Kind.ChangedSince), vm.penNotice.value)
            deps.restoreRefused = true
            vm.undoPen(penEntry(pen))
            runCurrent()
            assertEquals(PenNotice(PenNotice.Kind.UndoFailed), vm.penNotice.value)
        }

    @Test fun `on a follower the pen sheet is read only and a non-pen row opens nothing`() =
        runTest(dispatcher) {
            val deps = Deps()
            val pen = Samples.treatment(id = "p1")
            deps.treatments.upsertIfNewer(TreatmentRecord(pen))
            deps.pens[pen.id] = penEntry(pen)
            deps.mayChange = false
            deps.hasPens = false
            val vm = HomeViewModel(deps)
            backgroundScope.launch { vm.state.collect {} }
            runCurrent()
            vm.openPen(pen)
            runCurrent()
            val sheet = vm.penSheet.value!!
            assertEquals(false, sheet.editable)
            assertEquals(false, sheet.deletable)
            vm.correctPen(listOf(true, true))
            vm.deletePen()
            runCurrent()
            assertEquals(emptyList<String>(), deps.writes)
            vm.closePen()
            vm.openPen(Samples.treatment(id = "t9"))
            runCurrent()
            assertNull(vm.penSheet.value)
        }

    @Test fun `Delete on the pen sheet is home's delete with its Undo`() =
        runTest(dispatcher) {
            val deps = Deps()
            val pen = Samples.treatment(id = "p1")
            deps.treatments.upsertIfNewer(TreatmentRecord(pen))
            deps.pens[pen.id] = penEntry(pen)
            val vm = HomeViewModel(deps)
            backgroundScope.launch { vm.state.collect {} }
            runCurrent()
            vm.openPen(pen)
            runCurrent()
            vm.deletePen()
            runCurrent()
            assertNull(vm.penSheet.value)
            assertEquals(listOf("delete p1"), deps.writes)
            assertEquals(
                pen.id,
                vm.state.value.undo
                    ?.id,
            )
        }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `state follows the hub and says why when there is no value`() =
        runTest(dispatcher) {
            val deps = Deps()
            val vm = HomeViewModel(deps)
            backgroundScope.launch { vm.state.collect {} }
            runCurrent()
            assertNull(vm.state.value.reason)
            deps.glucose.value = Samples.noSensorYet()
            runCurrent()
            assertEquals(NoDataReason.NoSensorYet(follower = false), vm.state.value.reason)
            deps.glucose.value = GlucoseState.Initial
            runCurrent()
            assertEquals(NoDataReason.SetupIncomplete, vm.state.value.reason)
        }

    @Test fun `damaged data shows the banner until acknowledged`() =
        runTest(dispatcher) {
            val deps = Deps()
            val vm = HomeViewModel(deps)
            backgroundScope.launch { vm.state.collect {} }
            val problem = DataProblem(DamagedStore.Settings, "settings", "SerializationException", "settings.corrupt-1.json", Samples.NOW)
            deps.dataProblems.value = listOf(problem)
            runCurrent()
            assertEquals(BannerKind.DataProblem, vm.state.value.banner)
            vm.acknowledge(problem)
            runCurrent()
            assertNull(vm.state.value.banner)
        }

    /** #75: a writer's entry says *Sending…*, then *Not sent* with *Retry*; Retry sends it again. */
    @Test fun `entries on their way show their delivery and Retry sends again`() =
        runTest(dispatcher) {
            val deps = Deps()
            val sending = Samples.treatment("s")
            val failed = Samples.treatment("f", minutesAgo = 10)
            deps.treatments.upsertIfNewer(TreatmentRecord(sending, delivery = TreatmentDelivery.Pending))
            deps.treatments.upsertIfNewer(TreatmentRecord(failed, delivery = TreatmentDelivery.Failed))
            deps.treatments.upsertIfNewer(TreatmentRecord(Samples.treatment("d", minutesAgo = 20), delivery = TreatmentDelivery.Delivered))
            val vm = HomeViewModel(deps)
            backgroundScope.launch { vm.state.collect {} }
            runCurrent()
            assertEquals(mapOf("s" to TreatmentDelivery.Pending, "f" to TreatmentDelivery.Failed), vm.state.value.delivery)
            vm.retry(failed)
            runCurrent()
            assertEquals(listOf("retry f"), deps.writes)
            assertEquals(mapOf("s" to TreatmentDelivery.Pending, "f" to TreatmentDelivery.Pending), vm.state.value.delivery)
        }

    @Test fun `delete and undo go through the treatment log, a refused delete offers no undo`() =
        runTest(dispatcher) {
            val deps = Deps()
            val t = Samples.treatment()
            deps.treatments.upsertIfNewer(TreatmentRecord(t))
            deps.mayChange = false
            val vm = HomeViewModel(deps)
            backgroundScope.launch { vm.state.collect {} }
            vm.delete(t)
            runCurrent()
            assertEquals(setOf(t.id), vm.state.value.readOnly)
            assertEquals(listOf("delete ${t.id}"), deps.writes)
            assertNull(vm.state.value.undo)
            assertEquals(
                listOf(t.id),
                vm.state.value.treatments
                    .map { it.id },
            )
        }

    @Test fun `delete leaves a tombstone, undo brings it back as a newer version`() =
        runTest(dispatcher) {
            val deps = Deps()
            val t = Samples.treatment()
            deps.treatments.upsertIfNewer(TreatmentRecord(t))
            val vm = HomeViewModel(deps)
            backgroundScope.launch { vm.state.collect {} }
            runCurrent()
            assertEquals(
                listOf(t.id),
                vm.state.value.treatments
                    .map { it.id },
            )

            vm.delete(t)
            runCurrent()
            assertTrue(
                deps.treatments
                    .get(t.id)!!
                    .treatment.deleted,
            )
            assertEquals(t, vm.state.value.undo)
            assertTrue(
                vm.state.value.treatments
                    .isEmpty(),
            )

            vm.undoDelete()
            runCurrent()
            assertEquals(listOf("delete ${t.id}", "undo ${t.id}"), deps.writes)
            val restored = deps.treatments.get(t.id)!!.treatment
            assertEquals(false, restored.deleted)
            assertTrue(restored.modifiedAt > t.modifiedAt)
            assertNull(vm.state.value.undo)
            assertEquals(
                listOf(t.id),
                vm.state.value.treatments
                    .map { it.id },
            )
        }

    @Test fun `entries LibreView has get a cloud mark and deleting one says it leaves LibreView too`() =
        runTest(dispatcher) {
            val deps = Deps()
            val t = Samples.treatment()
            deps.treatments.upsertIfNewer(TreatmentRecord(t))
            val vm = HomeViewModel(deps)
            backgroundScope.launch { vm.state.collect {} }
            runCurrent()
            assertTrue(
                vm.state.value.marks
                    .isEmpty(),
            )

            deps.uploadMarks.value =
                object : UploadMarks {
                    override fun mark(
                        t: Treatment,
                        now: Instant,
                    ) = UploadMark.Uploaded

                    override fun removesOnDelete(t: Treatment) = true
                }
            runCurrent()
            assertEquals(mapOf(t.id to UploadMark.Uploaded), vm.state.value.marks)

            vm.delete(t)
            runCurrent()
            assertTrue(vm.state.value.undoInClinic)
        }

    /** The snackbar decides how long Undo is offered (longer with TalkBack), not a timer of the view model's (#74). */
    @Test fun `the undo offer lasts until the snackbar goes`() =
        runTest(dispatcher) {
            val deps = Deps()
            val t = Samples.treatment()
            deps.treatments.upsertIfNewer(TreatmentRecord(t))
            val vm = HomeViewModel(deps)
            backgroundScope.launch { vm.state.collect {} }
            vm.delete(t)
            runCurrent()
            assertEquals(t, vm.state.value.undo)
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals("a minute on, still offered while the snackbar shows", t, vm.state.value.undo)
            vm.undoShown()
            runCurrent()
            assertNull(vm.state.value.undo)
        }

    @Test fun `safety banners ignore dismiss`() =
        runTest(dispatcher) {
            val deps = Deps()
            deps.dataProblems.value =
                listOf(DataProblem(DamagedStore.Settings, "settings", "SerializationException", "settings.corrupt-1.json", Samples.NOW))
            val vm = HomeViewModel(deps)
            backgroundScope.launch { vm.state.collect {} }
            vm.dismiss(BannerKind.DataProblem)
            runCurrent()
            assertEquals(BannerKind.DataProblem, vm.state.value.banner)
        }

    @Test fun `the Garmin tip shows while a watch has an app and sending is off, closes per app and goes with sending on`() =
        runTest(dispatcher) {
            val deps = Deps()
            val vm = HomeViewModel(deps)
            backgroundScope.launch { vm.state.collect {} }
            runCurrent()
            assertNull(vm.state.value.banner)
            // Aura on the watch, sending off: the tip, naming the app and the watch
            val aura = GarminTip("Forerunner 265", listOf("aura"), 42)
            deps.environment.value = HomeEnvironment(garminOff = aura)
            runCurrent()
            assertEquals(BannerKind.GarminOff, vm.state.value.banner)
            assertEquals(aura, vm.state.value.garminTip)
            // closed: gone, and it stays closed for Aura
            vm.dismiss(BannerKind.GarminOff)
            runCurrent()
            assertNull(vm.state.value.banner)
            assertEquals(
                1,
                deps.bannerMemory.data
                    .first()
                    .closed,
            )
            // the data field turns up on the watch too: back, naming both (no single name)
            deps.environment.value = HomeEnvironment(garminOff = GarminTip("Forerunner 265", listOf("aura", "field"), null))
            runCurrent()
            assertEquals(BannerKind.GarminOff, vm.state.value.banner)
            // Undo on the snackbar brings a closed one back; Show closed tips does too
            vm.dismiss(BannerKind.GarminOff)
            runCurrent()
            assertNull(vm.state.value.banner)
            vm.restore(BannerKind.GarminOff)
            runCurrent()
            assertEquals(BannerKind.GarminOff, vm.state.value.banner)
            // sending on (or no app on any watch): no tip
            deps.environment.value = HomeEnvironment(garminOff = null)
            runCurrent()
            assertNull(vm.state.value.banner)
        }

    @Test fun `the update banner shows while a watch app reads another version, can't be closed and ends by itself`() =
        runTest(dispatcher) {
            val deps = Deps()
            val vm = HomeViewModel(deps)
            backgroundScope.launch { vm.state.collect {} }
            runCurrent()
            val tip = GarminUpdateTip("Descent", "aura", 42, newer = false)
            deps.environment.value = HomeEnvironment(garminUpdate = tip, garminOff = GarminTip("Descent", listOf("field"), 1))
            runCurrent()
            assertEquals(BannerKind.GarminUpdate, vm.state.value.banner)
            assertEquals(tip, vm.state.value.garminUpdate)
            // no close: a dismiss changes nothing
            vm.dismiss(BannerKind.GarminUpdate)
            runCurrent()
            assertEquals(BannerKind.GarminUpdate, vm.state.value.banner)
            // the watch reads this phone's version again: gone (the next tip shows)
            deps.environment.value = HomeEnvironment(garminOff = GarminTip("Descent", listOf("field"), 1))
            runCurrent()
            assertEquals(BannerKind.GarminOff, vm.state.value.banner)
        }

    @Test fun `the Garmin tip sits below the safety banners and the updates tip, above the other tips`() {
        val env =
            HomeEnvironment(
                garminOff = GarminTip("Venu 3", listOf("app"), 1),
                updateChecksOff = true,
                backedUp = false,
                clinicUploadSet = false,
            )
        val memory = BannerMemory()
        assertEquals(BannerKind.TurnOnUpdates, HomeBanners.pick(null, env, false, 0, memory, Samples.NOW))
        assertEquals(BannerKind.GarminOff, HomeBanners.pick(null, env.copy(updateChecksOff = false), false, 0, memory, Samples.NOW))
        val closed = memory.dismissKeys(GarminTip("Venu 3", listOf("app"), 1).keys, Samples.NOW)
        assertEquals(BannerKind.NotBackedUp, HomeBanners.pick(null, env.copy(updateChecksOff = false), false, 0, closed, Samples.NOW))
        // Show closed tips brings it back
        assertEquals(
            BannerKind.GarminOff,
            HomeBanners.pick(null, env.copy(updateChecksOff = false), false, 0, closed.restoreAll(), Samples.NOW),
        )
    }

    @Test fun `the Treatments page lists 31 days with the same marks, pens and delete as home's 24 h (#207)`() =
        runTest(dispatcher) {
            val deps = Deps()
            val today = Samples.treatment(id = "today", minutesAgo = 60)
            val lastWeek = Samples.treatment(id = "week", minutesAgo = 7 * 24 * 60)
            val tooOld = Samples.treatment(id = "old", minutesAgo = 32 * 24 * 60)
            listOf(today, lastWeek, tooOld).forEach { deps.treatments.upsertIfNewer(TreatmentRecord(it)) }
            deps.pens[lastWeek.id] = penEntry(lastWeek)
            val home = HomeViewModel(deps)
            val page = HomeViewModel(deps, HomeViewModel.HISTORY_WINDOW)
            backgroundScope.launch { home.state.collect {} }
            backgroundScope.launch { page.state.collect {} }
            runCurrent()
            assertEquals(
                listOf("today"),
                home.state.value.treatments
                    .map { it.id },
            )
            assertEquals(
                listOf("today", "week"),
                page.state.value.treatments
                    .map { it.id },
            )
            assertEquals(setOf("week"), page.state.value.fromPen)

            page.delete(lastWeek)
            runCurrent()
            assertEquals(
                listOf("today"),
                page.state.value.treatments
                    .map { it.id },
            )
            assertEquals(lastWeek, page.state.value.undo)
            page.undoDelete()
            runCurrent()
            assertEquals(
                listOf("today", "week"),
                page.state.value.treatments
                    .map { it.id },
            )
            assertEquals(listOf("delete week", "undo week"), deps.writes)
        }

    @Test fun `a pen entry's note is saved through the log, with Undo (#207)`() =
        runTest(dispatcher) {
            val deps = Deps()
            val pen = Samples.treatment(id = "p1")
            deps.treatments.upsertIfNewer(TreatmentRecord(pen))
            deps.pens[pen.id] = penEntry(pen)
            val vm = HomeViewModel(deps)
            backgroundScope.launch { vm.state.collect {} }
            runCurrent()
            vm.openPen(pen)
            runCurrent()
            vm.notePen("Pizza")
            runCurrent()
            assertEquals(
                "Pizza",
                deps.treatments
                    .get("p1")
                    ?.treatment
                    ?.note,
            )
            val notice = vm.penNotice.value!!
            assertEquals(PenNotice.Kind.NoteChanged, notice.kind)
            vm.undoNote(notice.noteUndo!!)
            runCurrent()
            assertEquals(
                "",
                deps.treatments
                    .get("p1")
                    ?.treatment
                    ?.note,
            )
            assertEquals(listOf("note p1 Pizza", "undo p1"), deps.writes)
        }
}
