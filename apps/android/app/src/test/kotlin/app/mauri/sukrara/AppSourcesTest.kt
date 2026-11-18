// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.SourceKind
import app.mauri.sukrara.core.model.SourceSpec
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.TreatmentOrigin
import app.mauri.sukrara.core.model.storage.TreatmentRecord
import app.mauri.sukrara.core.testing.InMemoryReadingsRepository
import app.mauri.sukrara.core.testing.InMemoryStateStore
import app.mauri.sukrara.core.testing.InMemoryTreatmentsRepository
import app.mauri.sukrara.feature.sharing.HistoryLoss
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * A change of whose glucose never mixes two people: every reading and treatment goes, after [AppSources.loss] said
 * what. A source change for the same person keeps them (docs/44 "Person × Source").
 */
class AppSourcesTest {
    private val t0 = Instant.parse("2026-01-15T12:00:00Z")
    private val store = SourceStore(InMemoryStateStore(SourceRecord()), InMemoryStateStore(SourceRecord()))
    private val readings = InMemoryReadingsRepository()
    private val treatments = InMemoryTreatmentsRepository()

    /** What an unanswered install reads (an update from before the question). */
    private val inferred = MutableStateFlow<SourceSpec?>(null)
    private val running = combine(store.data, inferred) { r, i -> r.spec ?: i }
    private val sources = AppSources(store, running, readings, treatments)

    private fun reading(
        min: Long,
        source: SourceId,
    ) = Reading(t0.plusSeconds(min * 60), 100 + min.toInt(), null, source)

    private suspend fun addTreatment() =
        treatments.replace(
            TreatmentRecord(Treatment("t1", TreatmentKind.Carbs, 30.0, t0, modifiedAt = t0, origin = TreatmentOrigin.Follower)),
        )

    private suspend fun all() = readings.range(Instant.EPOCH, t0.plusSeconds(86_400))

    @Test fun theirsToYoursDeletesTheirReadingsAndTreatments() =
        runTest {
            sources.set(SourceSpec.TheirSukrara)
            readings.upsert(listOf(reading(0, SourceId.Relay), reading(5, SourceId.Nearby)))
            addTreatment()

            sources.set(SourceSpec.YourSensor)
            readings.upsert(listOf(reading(10, SourceId.Libre3)))

            assertEquals(SourceSpec.YourSensor, sources.stored.first())
            assertEquals(listOf(SourceId.Libre3), all().map { it.source })
            assertTrue(treatments.between(Instant.EPOCH, t0.plusSeconds(60)).first().isEmpty())
        }

    @Test fun aChangeOfWhoseGlucoseLetsGoOfTheOtherPersonsSiteAndLibreLinkUpPick() =
        runTest {
            // Appendix G finding 4: Ana's pick read on as "yours"
            var after: Pair<SourceRecord, SourceSpec>? = null
            val sources = AppSources(store, running, readings, treatments, anotherPerson = { was, spec -> after = was to spec })
            sources.set(SourceSpec(Person.Theirs, SourceKind.LibreLinkUp))
            store.update { it.copy(libreLinkUpPatient = "ana", nightscoutUrl = "https://ana.example") }

            val yours = SourceSpec(Person.Yours, SourceKind.LibreLinkUp)
            sources.set(yours)

            val r = store.data.first()
            assertEquals(yours, r.spec)
            assertEquals("", r.libreLinkUpPatient)
            assertEquals("", r.nightscoutUrl)
            assertEquals("ana", after?.first?.libreLinkUpPatient)
            assertEquals("https://ana.example", after?.first?.nightscoutUrl)
            assertEquals(yours, after?.second)

            // the same person: both stay (the way back)
            after = null
            store.update { it.copy(libreLinkUpPatient = "me", nightscoutUrl = "https://me.example") }
            sources.set(SourceSpec(Person.Yours, SourceKind.Nightscout))
            assertEquals("me", store.data.first().libreLinkUpPatient)
            assertEquals("https://me.example", store.data.first().nightscoutUrl)
            assertNull(after)
        }

    @Test fun aSourceChangeForTheSamePersonKeepsTheHistory() =
        runTest {
            sources.set(SourceSpec(Person.Yours, SourceKind.Sukrara))
            readings.upsert(listOf(reading(0, SourceId.Relay)))
            addTreatment()

            assertNull(sources.loss(SourceSpec.YourSensor))
            sources.set(SourceSpec.YourSensor)

            assertEquals(1, all().size)
            assertEquals(1, treatments.between(Instant.EPOCH, t0.plusSeconds(60)).first().size)
        }

    /**
     * Appendix G finding 5: the new source's backfill overwrote what the previous one read (another person's too). A
     * source change for the same person fences the history at its last reading; a person change, whose history goes,
     * lifts it.
     */
    @Test fun aSourceChangeForTheSamePersonFencesTheHistoryTheNextSourceContinues() =
        runTest {
            val sources = AppSources(store, running, readings, treatments, setFence = { readings.fence = it })
            sources.set(SourceSpec(Person.Yours, SourceKind.Sukrara))
            readings.upsert(listOf(reading(0, SourceId.Relay), reading(10, SourceId.Relay)))

            sources.set(SourceSpec(Person.Yours, SourceKind.LibreLinkUp))
            assertEquals(t0.plusSeconds(600), readings.fence)
            // LibreLinkUp's backfill: over the kept readings it is dropped, in the gap between them it fills
            readings.upsert(listOf(reading(1, SourceId.LibreLinkUp), reading(5, SourceId.LibreLinkUp), reading(11, SourceId.LibreLinkUp)))
            assertEquals(
                listOf(0L to SourceId.Relay, 5L to SourceId.LibreLinkUp, 10L to SourceId.Relay),
                all().map { (it.t.epochSecond - t0.epochSecond) / 60 to it.source },
            )

            sources.set(SourceSpec.TheirSukrara)
            assertNull(readings.fence)
        }

    @Test fun lossSaysWhatAChangeOfPersonWouldDelete() =
        runTest {
            sources.set(SourceSpec.TheirSukrara)
            readings.upsert(listOf(reading(0, SourceId.Relay), reading(30, SourceId.Nearby)))
            addTreatment()

            // two readings 30 min apart: two 5-minute stretches, 10 minutes of glucose
            assertEquals(HistoryLoss(10, 1, t0, t0.plusSeconds(30 * 60)), sources.loss(SourceSpec.YourSensor))
            assertNull(sources.loss(SourceSpec(Person.Theirs, SourceKind.PhoneApp)))
            // asking deletes nothing
            assertEquals(2, all().size)
        }

    @Test fun theFirstAnswerOnANewInstallDeletesNothing() =
        runTest {
            readings.upsert(listOf(reading(0, SourceId.Libre3)))
            assertNull(sources.loss(SourceSpec.TheirSukrara))
            sources.set(SourceSpec.TheirSukrara)
            assertEquals(1, all().size)
        }

    @Test fun historyFromBeforeTheQuestionSaysWhoseItIs() =
        runTest {
            assertNull(historyOwner(readings))
            readings.upsert(listOf(reading(0, SourceId.Relay), reading(5, SourceId.Nearby)))
            assertEquals(SourceSpec.TheirSukrara, historyOwner(readings))
            // any first-hand or imported reading is the person's own (a follow can't bring those)
            readings.upsert(listOf(reading(10, SourceId("import-libreview"))))
            assertEquals(SourceSpec.YourSensor, historyOwner(readings))
        }

    /**
     * Someone else's Nightscout (or LibreLinkUp) whose source file is damaged: its readings were taken for the phone's
     * own (they aren't relayed) and the next answer of *Someone else's* asked to delete them. Online readings belong to
     * either person, so nothing is guessed: setup asks.
     */
    @Test fun onlineReadingsAloneDontSayWhoseTheyAre() =
        runTest {
            readings.upsert(listOf(reading(0, SourceId.Nightscout), reading(5, SourceId.LibreLinkUp)))
            assertNull(historyOwner(readings))
            assertNull(infer())
            // relayed before (a person change deletes the other's): someone else's
            readings.upsert(listOf(reading(10, SourceId.Relay)))
            assertEquals(SourceSpec.TheirSukrara, historyOwner(readings))
            // an import is only ever your own
            readings.upsert(listOf(reading(15, SourceId("import-file"))))
            assertEquals(SourceSpec.YourSensor, historyOwner(readings))
        }

    private suspend fun infer(
        following: Boolean = false,
        unlocked: Boolean = true,
        paired: Boolean = false,
        juggluco: Boolean = false,
    ) = inferredSource({ following }, readings.takeIf { unlocked }, { paired }, { juggluco })

    private val yourJuggluco = SourceSpec(Person.Yours, SourceKind.PhoneApp)

    @Test fun anUpdateKeepsReadingTheSourceThisPhoneReadLast() =
        runTest {
            // a Juggluco reader keeps the sensor's pairing from before it switched (maybe ended): Juggluco stays the
            // source, or its live readings would go unread after the update
            readings.upsert(listOf(reading(0, SourceId.Libre3), reading(10, SourceId.Juggluco)))
            assertEquals(yourJuggluco, infer(paired = true))
            // a sensor reader whose pairing was forgotten (an ended sensor) with Juggluco merely installed: the sensor
            readings.upsert(listOf(reading(20, SourceId.Libre3)))
            assertEquals(SourceSpec.YourSensor, infer(paired = false, juggluco = true))
            // a follow wins over everything (a follower never read anything itself)
            assertEquals(SourceSpec.TheirSukrara, infer(following = true, paired = true))
        }

    @Test fun withoutOwnReadingsThePairingThenJugglucoThenTheHistoryDecide() =
        runTest {
            assertNull(infer())
            assertEquals(SourceSpec.YourSensor, infer(paired = true, juggluco = true))
            assertEquals(yourJuggluco, infer(juggluco = true))
            // imports and relayed readings say whose, not where from
            readings.upsert(listOf(reading(0, SourceId("import-libreview"))))
            assertEquals(SourceSpec.YourSensor, infer())
        }

    @Test fun beforeTheFirstUnlockThePairingDecides() =
        runTest {
            readings.upsert(listOf(reading(0, SourceId.Juggluco)))
            assertEquals(SourceSpec.YourSensor, infer(unlocked = false, paired = true))
            assertNull(infer(unlocked = false))
        }

    @Test fun anUpdatedInstallComparesWithWhatItReads() =
        runTest {
            inferred.value = SourceSpec.YourSensor
            readings.upsert(listOf(reading(0, SourceId.Libre3)))
            addTreatment()
            // the same person: its history stays
            assertNull(sources.loss(SourceSpec.YourSensor))
            // someone else's: what it read was the person's own history
            assertEquals(HistoryLoss(5, 1, t0, t0), sources.loss(SourceSpec.TheirSukrara))
            sources.set(SourceSpec.YourSensor)
            assertEquals(1, all().size)
        }
}
