// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.protocol

import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.TreatmentOrigin
import app.mauri.sukrara.core.model.storage.TreatmentRecord
import app.mauri.sukrara.core.model.supersedes
import app.mauri.sukrara.core.testing.InMemoryTreatmentsRepository
import app.mauri.sukrara.protocol.event.TreatmentPayload
import app.mauri.sukrara.protocol.testing.ContractFiles
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * contracts/treatments/merge.json (SPEC §1): the wire comparison followers judge with ([TreatmentPayload.newerThan]),
 * the model's [supersedes] and the store every test fake uses agree on every case, in both arrival orders. Room runs
 * the same cases in `:core:data` `SukraraDatabaseTest`, the Mac in `TreatmentTests`.
 */
class TreatmentMergeTest {
    /** [wins] = `inc`, `cur` or `same` (an equal `(u, o)`: the second arrival changes nothing). */
    private class Case(val name: String, val cur: JsonObject, val inc: JsonObject, val wins: String) {
        val incWins get() = wins == "inc"
    }

    private val cases =
        ContractFiles.json("treatments/merge.json").getValue("cases").jsonArray.map {
            val c = it.jsonObject
            Case(c.str("name"), c.getValue("cur").jsonObject, c.getValue("inc").jsonObject, c.str("wins"))
        }

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

    private fun payload(v: JsonObject) =
        TreatmentPayload("x", "rapid", v.getValue("a").jsonPrimitive.double, 100, "", v.getValue("u").jsonPrimitive.long, false, v.str("o"))

    private fun treatment(v: JsonObject) =
        Treatment(
            id = "x",
            kind = TreatmentKind.RapidInsulin,
            amount = v.getValue("a").jsonPrimitive.double,
            at = Instant.ofEpochSecond(100),
            modifiedAt = Instant.ofEpochMilli(v.getValue("u").jsonPrimitive.long),
            origin = TreatmentOrigin.entries.single { it.wire == v.str("o") },
        )

    @Test fun theWireComparisonFollowsTheCases() {
        cases.forEach { c ->
            assertEquals(c.name, c.incWins, payload(c.inc).newerThan(payload(c.cur)))
            assertEquals(c.name, c.wins == "cur", payload(c.cur).newerThan(payload(c.inc)))
        }
    }

    @Test fun theModelsMergeFollowsTheCases() {
        cases.forEach { c ->
            assertEquals(c.name, c.incWins, treatment(c.inc).supersedes(treatment(c.cur)))
            assertEquals(c.name, c.wins == "cur", treatment(c.cur).supersedes(treatment(c.inc)))
        }
    }

    @Test fun theStoreKeepsTheWinnerInEitherOrder() =
        runTest {
            cases.forEach { c ->
                val cur = TreatmentRecord(treatment(c.cur))
                val inc = TreatmentRecord(treatment(c.inc))
                listOf(cur to inc, inc to cur).forEach { (first, second) ->
                    val winner =
                        if (c.wins == "same") {
                            first
                        } else if (c.incWins) {
                            inc
                        } else {
                            cur
                        }
                    val repo = InMemoryTreatmentsRepository()
                    repo.upsertIfNewer(first)
                    assertEquals(c.name, winner === second, repo.upsertIfNewer(second))
                    assertEquals(c.name, winner, repo.get("x"))
                }
            }
        }
}
