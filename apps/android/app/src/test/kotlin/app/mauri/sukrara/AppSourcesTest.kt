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

    @Test fun lossSaysWhatAChangeOfPersonWouldDelete() =
        runTest {
            sources.set(SourceSpec.TheirSukrara)
            readings.upsert(listOf(reading(0, SourceId.Relay), reading(30, SourceId.Nearby)))
            addTreatment()

            assertEquals(HistoryLoss(2, 1, t0, t0.plusSeconds(30 * 60)), sources.loss(SourceSpec.YourSensor))
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
            assertEquals(HistoryLoss(1, 1, t0, t0), sources.loss(SourceSpec.TheirSukrara))
            sources.set(SourceSpec.YourSensor)
            assertEquals(1, all().size)
        }
}
