// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.core.data.db.OutboxEntity
import app.mauri.sukrara.core.data.db.RejectEntity
import app.mauri.sukrara.core.data.db.RoomReadingsRepository
import app.mauri.sukrara.core.data.db.RoomTreatmentsRepository
import app.mauri.sukrara.core.data.db.SeenWrapEntity
import app.mauri.sukrara.core.data.db.SukraraDatabase
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.SourceSpec
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.TreatmentOrigin
import app.mauri.sukrara.core.model.storage.StoredSpan
import app.mauri.sukrara.core.model.storage.TreatmentDelivery
import app.mauri.sukrara.core.model.storage.TreatmentRecord
import app.mauri.sukrara.core.testing.Contracts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class SukraraDatabaseTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SukraraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val readings = RoomReadingsRepository(db.readings())
    private val treatments = RoomTreatmentsRepository(db.treatments())
    private val t0 = Instant.parse("2026-01-15T12:00:00Z")

    @After
    fun close() = db.close()

    private fun reading(
        minutes: Long,
        mgdl: Int,
        source: SourceId = SourceId.Libre3,
    ) = Reading(t0.plus(Duration.ofMinutes(minutes)), mgdl, 0.5, source)

    @Test
    fun `a change of whose glucose deletes every reading, after saying what it deletes`() =
        runTest {
            readings.upsert(listOf(reading(0, 100), reading(5, 110, SourceId.Juggluco), reading(10, 200, SourceId.Relay)))
            assertEquals(StoredSpan(3, t0, t0.plusSeconds(600)), readings.span())
            assertEquals(StoredSpan(1, t0.plusSeconds(600), t0.plusSeconds(600)), readings.spanOf(setOf(SourceId.Relay)))
            assertEquals(3, readings.deleteAll())
            assertEquals(StoredSpan.EMPTY, readings.span())
        }

    @Test
    fun `the rank rule keeps one reading per measurement in Room`() =
        runTest {
            // the relayed copy first (another Sukrara), then this phone's sensor reads the same minute: the sensor's stays
            readings.upsert(listOf(reading(0, 100, SourceId.Relay), reading(1, 101, SourceId.Relay)))
            readings.upsert(listOf(reading(1, 102)))
            assertEquals(listOf(102 to SourceId.Libre3), readings.window(t0).first().map { it.mgdl to it.source })
            // a relayed copy arriving later is dropped; the sensor's next minute stays
            readings.upsert(listOf(reading(2, 103, SourceId.Nearby), reading(2, 104)))
            assertEquals(listOf(102, 104), readings.window(t0).first().map { it.mgdl })
        }

    @Test
    fun `window returns readings from a time, oldest first`() =
        runTest {
            readings.upsert(listOf(reading(10, 120), reading(0, 100), reading(5, 110)))
            assertEquals(listOf(110, 120), readings.window(t0.plusSeconds(60)).first().map { it.mgdl })
            assertEquals(120, readings.latest().first()?.mgdl)
        }

    @Test
    fun `the same time and source is replaced, another source's copy goes by rank`() =
        runTest {
            readings.upsert(listOf(reading(0, 100)))
            readings.upsert(listOf(reading(0, 101), reading(0, 99, SourceId.Relay)))
            assertEquals(
                setOf(101 to SourceId.Libre3),
                readings
                    .window(t0)
                    .first()
                    .map { it.mgdl to it.source }
                    .toSet(),
            )
        }

    @Test
    fun `range is half-open and retention deletes older readings`() =
        runTest {
            readings.upsert((0L..4L).map { reading(it * 5, 100 + it.toInt()) })
            assertEquals(listOf(101, 102), readings.range(t0.plusSeconds(300), t0.plusSeconds(900)).map { it.mgdl })
            assertEquals(2, readings.deleteBefore(t0.plusSeconds(600)))
            assertEquals(
                102,
                readings
                    .window(Instant.EPOCH)
                    .first()
                    .first()
                    .mgdl,
            )
        }

    private fun record(
        modifiedMinutes: Long,
        amount: Double,
        deleted: Boolean = false,
    ) = TreatmentRecord(
        treatment =
            Treatment(
                id = "tr-1",
                kind = TreatmentKind.RapidInsulin,
                amount = amount,
                at = t0,
                modifiedAt = t0.plus(Duration.ofMinutes(modifiedMinutes)),
                deleted = deleted,
                origin = TreatmentOrigin.Phone,
            ),
        creator = "creator-key",
        delivery = TreatmentDelivery.Pending,
        penJson = "{\"pen\":\"x\"}",
    )

    @Test
    fun `treatments - last writer wins and round-trips every field`() =
        runTest {
            assertTrue(treatments.upsertIfNewer(record(1, 4.0)))
            assertFalse("older loses", treatments.upsertIfNewer(record(0, 9.0)))
            assertFalse("same version loses", treatments.upsertIfNewer(record(1, 9.0)))
            assertTrue(treatments.upsertIfNewer(record(2, 5.0)))
            assertEquals(record(2, 5.0), treatments.get("tr-1"))
        }

    /** contracts/treatments/merge.json: the greater `(u, o)` wins in either arrival order (equal `u`: the greater origin). */
    @Test
    fun `treatments - the contract's merge cases through Room`() =
        runTest {
            val cases =
                Json
                    .parseToJsonElement(Contracts.file("treatments/merge.json").readText())
                    .jsonObject
                    .getValue("cases")
                    .jsonArray
            cases.forEachIndexed { i, c ->
                val case = c.jsonObject
                val name = case.getValue("name").jsonPrimitive.content
                val cur = mergeRecord("m$i", case.getValue("cur").jsonObject)
                val inc = mergeRecord("m$i", case.getValue("inc").jsonObject)
                val wins = case.getValue("wins").jsonPrimitive.content
                // both arrival orders keep the same version (`same`: the second one changes nothing)
                listOf(cur to inc, inc to cur).forEach { (first, second) ->
                    val winner =
                        if (wins == "same") {
                            first
                        } else if (wins == "inc") {
                            inc
                        } else {
                            cur
                        }
                    assertTrue(name, treatments.upsertIfNewer(first))
                    assertEquals(name, winner === second, treatments.upsertIfNewer(second))
                    assertEquals(name, winner, treatments.get("m$i"))
                    treatments.deleteAll()
                }
            }
        }

    private fun mergeRecord(
        id: String,
        v: JsonObject,
    ) = TreatmentRecord(
        treatment =
            Treatment(
                id = id,
                kind = TreatmentKind.RapidInsulin,
                amount = v.getValue("a").jsonPrimitive.double,
                at = t0,
                modifiedAt = Instant.ofEpochMilli(v.getValue("u").jsonPrimitive.long),
                origin = TreatmentOrigin.entries.single { it.wire == v.getValue("o").jsonPrimitive.content },
            ),
    )

    @Test
    fun `deleted treatments are hidden but stay as tombstones for sync`() =
        runTest {
            treatments.upsertIfNewer(record(1, 4.0))
            treatments.upsertIfNewer(record(2, 4.0, deleted = true))
            assertTrue(treatments.between(t0.minusSeconds(60), t0.plusSeconds(60)).first().isEmpty())
            assertEquals(1, treatments.changedSince(t0).size)
            assertNull(treatments.get("missing"))
            assertEquals("a tombstone isn't counted", StoredSpan.EMPTY, treatments.span())
            treatments.upsertIfNewer(record(3, 4.0))
            assertEquals(StoredSpan(1, t0, t0), treatments.span())
        }

    @Test
    fun `since gives every live treatment from its start on, however late, oldest first`() =
        runTest {
            fun at(id: String, at: Instant, deleted: Boolean = false) =
                record(1, 4.0, deleted).let { r -> r.copy(treatment = r.treatment.copy(id = id, at = at, deleted = deleted)) }
            treatments.upsertIfNewer(at("before", t0.minusSeconds(1)))
            treatments.upsertIfNewer(at("far", t0.plus(Duration.ofDays(20_000))))
            treatments.upsertIfNewer(at("start", t0))
            treatments.upsertIfNewer(at("gone", t0.plusSeconds(60), deleted = true))
            assertEquals(listOf("start", "far"), treatments.since(t0).first().map { it.id })
        }

    @Test
    fun `seen wraps dedupe, rejects and outbox`() =
        runTest {
            assertFalse(db.seenWraps().contains("w1"))
            assertTrue(db.seenWraps().insert(SeenWrapEntity("w1", 1)) != -1L)
            assertTrue(db.seenWraps().insert(SeenWrapEntity("w1", 2)) == -1L)
            assertTrue(db.seenWraps().contains("w1"))
            db.rejects().insert(RejectEntity(at = 5, why = "bad signature", author = null, via = "relay.example"))
            assertEquals(
                "bad signature",
                db
                    .rejects()
                    .recent(10)
                    .first()
                    .single()
                    .why,
            )

            db.outbox().put(OutboxEntity("e1", version = 1, event = "{}", createdAt = 1, sentAt = null))
            db.outbox().put(OutboxEntity("e1", version = 2, event = "{\"v\":2}", createdAt = 1, sentAt = null))
            assertEquals(0, db.outbox().markSent("e1", version = 1, at = 3))
            assertEquals(1, db.outbox().markSent("e1", version = 2, at = 3))
            assertTrue(
                db
                    .outbox()
                    .pending()
                    .first()
                    .isEmpty(),
            )
        }
}
