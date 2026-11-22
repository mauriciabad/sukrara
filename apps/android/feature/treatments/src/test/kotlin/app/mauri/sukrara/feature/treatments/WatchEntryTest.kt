// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.treatments

import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.PenDuplicates
import app.mauri.sukrara.core.model.PenNearEntry
import app.mauri.sukrara.core.model.SourceSpec
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.TreatmentOrigin
import app.mauri.sukrara.core.model.storage.TreatmentRecord
import app.mauri.sukrara.core.testing.Contracts
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * contracts/watchface/payload/entries.json through the phone's own entry path ([TreatmentsModule.fromWatch]): every
 * case that gets past the endpoint's checks (signature, send time, id, kind; WatchWritesTest) ends as the contract
 * says, and stores exactly the entry it names, never a second one for a known id.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchEntryTest {
    private val file = Json.parseToJsonElement(Contracts.file("watchface/payload/entries.json").readText()).jsonObject
    private val now = Instant.ofEpochSecond(file.getValue("now").jsonPrimitive.long)

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

    private fun kind(k: String) =
        when (k) {
            "rapid" -> TreatmentKind.RapidInsulin
            "long" -> TreatmentKind.LongInsulin
            "carbs" -> TreatmentKind.Carbs
            else -> TreatmentKind.Note
        }

    private suspend fun TestScope.module(
        repo: InMemoryTreatmentsRepository,
        sync: Sync,
        pen: JsonObject?,
    ): TreatmentsModule {
        for (s in file.getValue("stored").jsonArray.map { it.jsonObject }) {
            val at = Instant.ofEpochSecond(s.getValue("t").jsonPrimitive.long)
            val t =
                Treatment(
                    s.getValue("id").jsonPrimitive.content,
                    kind(s.getValue("k").jsonPrimitive.content),
                    s.getValue("a").jsonPrimitive.double,
                    at,
                    modifiedAt = at,
                    origin = TreatmentOrigin.Phone,
                    deleted = s["del"]?.jsonPrimitive?.booleanOrNull ?: false,
                )
            repo.upsertIfNewer(TreatmentRecord(t))
        }
        val near = pen?.let { PenNearEntry("pen-1", it.getValue("a").jsonPrimitive.double, it.getValue("t").jsonPrimitive.long) }
        return TreatmentsModule(
            repo,
            sync,
            FakeClock(now),
            MutableStateFlow(GlucoseState.Initial.copy(spec = SourceSpec.YourSensor)),
            InMemorySettingsRepository(),
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            hints,
            penDuplicates = PenDuplicates { k, _ -> near?.takeIf { k == "rapid" } },
        )
    }

    @Test
    fun everyCaseThePhonesPathDecides() =
        runTest {
            var ran = 0
            for (c in file.getValue("cases").jsonArray.map { it.jsonObject }) {
                val r =
                    c
                        .getValue("answer")
                        .jsonObject
                        .getValue("r")
                        .jsonPrimitive.content
                if (r in setOf("sig", "clock", "id", "kind")) continue
                ran++
                val name = c.getValue("name").jsonPrimitive.content
                val body = c.getValue("body").jsonObject
                val phone = c["phone"]?.jsonObject
                val open = phone?.get("open")?.jsonPrimitive?.booleanOrNull ?: true
                val repo = InMemoryTreatmentsRepository()
                val sync = Sync(if (open) TreatmentWriter(key = null, throughOutbox = false) else null)
                val m = module(repo, sync, phone?.get("pen")?.jsonObject)
                val before = repo.between(Instant.EPOCH, now.plusSeconds(86_400)).first().size
                val id = body.getValue("id").jsonPrimitive.content
                val got =
                    m.fromWatch(
                        id,
                        kind(body.getValue("k").jsonPrimitive.content),
                        body.getValue("a").jsonPrimitive.content,
                        Instant.ofEpochSecond(body.getValue("t").jsonPrimitive.long),
                        body.getValue("f").jsonPrimitive.content == "1",
                    )
                val expected =
                    when (got) {
                        is FromWatch.Stored -> "stored"
                        is FromWatch.Known -> "known"
                        is FromWatch.Pen -> "pen"
                        FromWatch.Amount -> "amount"
                        FromWatch.When -> "when"
                        FromWatch.Off -> "off"
                    }
                assertEquals(name, r, expected)
                val after = repo.between(Instant.EPOCH, now.plusSeconds(86_400)).first()
                val stores = c["stores"]?.takeIf { it !is JsonNull }?.jsonObject
                if (stores == null) {
                    assertEquals("$name: nothing stored", before, after.size)
                    assertTrue(name, sync.published.isEmpty())
                } else {
                    assertEquals("$name: one more", before + 1, after.size)
                    val t = repo.get(id)!!.treatment
                    assertEquals(name, kind(stores.getValue("k").jsonPrimitive.content), t.kind)
                    assertEquals(name, stores.getValue("a").jsonPrimitive.double, t.amount, 1e-9)
                    assertEquals(name, stores.getValue("t").jsonPrimitive.long, t.at.epochSecond)
                    assertEquals(name, TreatmentOrigin.Phone, t.origin)
                    // and synced like any entry the phone adds
                    assertEquals(
                        name,
                        id,
                        sync.published
                            .single()
                            .treatment.id,
                    )
                }
                if (got is FromWatch.Known) {
                    val e =
                        c
                            .getValue("answer")
                            .jsonObject["e"]
                            ?.takeIf { it !is JsonNull }
                            ?.jsonObject
                    assertEquals(name, e?.getValue("a")?.jsonPrimitive?.double, got.treatment?.amount)
                }
            }
            assertTrue(ran >= 15)
        }
}
