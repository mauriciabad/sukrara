// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.model

import app.mauri.sukrara.core.testing.Contracts
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Test

/** [KindOrder] against every case of contracts/treatments/kind-order.json (the Mac runs the same: KindOrderTests). */
class KindOrderTest {
    private val doc = Json.parseToJsonElement(Contracts.file("treatments/kind-order.json").readText()).jsonObject
    private val kinds = mapOf("rapid" to TreatmentKind.RapidInsulin, "long" to TreatmentKind.LongInsulin, "carbs" to TreatmentKind.Carbs)

    private fun list(key: String) = doc.getValue(key).jsonArray.map { it.jsonPrimitive.content }

    @Test
    fun `the constants are the contract's`() {
        assertEquals(list("usual").map(kinds::getValue), KindOrder.USUAL)
        assertEquals(list("carbs_alerts").toSet(), KindOrder.CARBS_ALERTS)
        assertEquals(doc.getValue("pen_s").jsonPrimitive.long, KindOrder.PEN_S)
    }

    @Test
    fun `every case gives the contract's order`() {
        val now = 1_794_823_776L
        val cases = doc.getValue("cases").jsonArray.map { it.jsonObject }
        for (c in cases) {
            val pen =
                c["pen"]?.takeIf { it != JsonNull }?.jsonObject?.let {
                    PenReadAt(kinds.getValue(it.getValue("k").jsonPrimitive.content), now - it.getValue("ago_s").jsonPrimitive.long)
                }
            val facts =
                KindFacts(
                    reading = c["reading"]?.jsonPrimitive?.intOrNull,
                    lowBelow = c.getValue("lowBelow").jsonPrimitive.double,
                    ringing =
                        c
                            .getValue("ringing")
                            .jsonArray
                            .map { it.jsonPrimitive.content }
                            .toSet(),
                    pen = pen,
                )
            val expected = c.getValue("order").jsonArray.map { kinds.getValue(it.jsonPrimitive.content) }
            assertEquals(c.getValue("name").jsonPrimitive.content, expected, KindOrder.of(facts, now))
            assertEquals(expected.first(), KindOrder.focus(facts, now))
        }
    }
}
