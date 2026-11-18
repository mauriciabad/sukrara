// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.model

import app.mauri.sukrara.core.testing.Contracts
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

/** [OnlineHealth] against contracts/sources/health.json. */
class OnlineHealthTest {
    private val health = Json.parseToJsonElement(Contracts.file("sources/health.json").readText()).jsonObject

    private fun id(e: Enum<*>) = e.name.replaceFirstChar(Char::lowercase)

    @Test
    fun `the thresholds and the states are the contract's`() {
        assertEquals(health.getValue("late_after_s").jsonPrimitive.long, OnlineHealth.LATE_AFTER.seconds)
        assertEquals(health.getValue("silent_after_s").jsonPrimitive.long, OnlineHealth.SILENT_AFTER.seconds)
        assertEquals(health.getValue("states").jsonArray.map { it.jsonPrimitive.content }, OnlineHealth.entries.map(::id))
    }

    @Test
    fun `every case gets its state`() {
        for (c in health.getValue("cases").jsonArray.map { it.jsonObject }) {
            val outcome =
                c
                    .getValue("outcome")
                    .takeIf { it != JsonNull }
                    ?.jsonPrimitive
                    ?.content
            val age =
                c
                    .getValue("newestAge_s")
                    .takeIf { it != JsonNull }
                    ?.jsonPrimitive
                    ?.int
            val got =
                OnlineHealth.of(
                    outcome?.let { o -> CallOutcome.entries.first { id(it) == o } },
                    c.getValue("online").jsonPrimitive.boolean,
                    age?.let { Duration.ofSeconds(it.toLong()) },
                    c["warmingUp"]?.jsonPrimitive?.boolean ?: false,
                )
            assertEquals(c.getValue("name").jsonPrimitive.content, c.getValue("state").jsonPrimitive.content, id(got))
        }
    }
}
