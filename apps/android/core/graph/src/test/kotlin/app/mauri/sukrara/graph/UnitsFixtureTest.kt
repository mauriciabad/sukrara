// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.graph

import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.settings.SettingsSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * THE glucose unit ([GlucoseUnit], :core:model) against contracts/settings/units.json: the factor, values and rates as
 * written (one decimal / two, the language's separator), typed values back to stored mg/dL, and every mmol/L variant of
 * the settings schema (every value of its grid comes back unchanged). The Mac's GlucoseUnitTests run the same file.
 */
class UnitsFixtureTest {
    private val contract: JsonObject by lazy {
        val dir = System.getProperty("sukrara.contracts") ?: error("sukrara.contracts not set: run through Gradle")
        Json.parseToJsonElement(File(dir, "settings/units.json").readText()).jsonObject
    }

    private fun cases(key: String) = contract.getValue(key).jsonArray.map { it.jsonArray }

    private fun locale(tag: String) = Locale.forLanguageTag(tag)

    @Test fun `the factor is the contract's`() {
        assertEquals(contract.getValue("factor").jsonPrimitive.double, GlucoseUnit.MGDL_PER_MMOL, 0.0)
    }

    @Test fun `values are written as the contract says`() {
        val cs = cases("value")
        assertTrue(cs.size >= 10)
        for ((mgdl, unit, lang, want) in cs) {
            val u = GlucoseUnit.of(unit.jsonPrimitive.content)
            assertEquals(
                "$mgdl $unit $lang",
                want.jsonPrimitive.content,
                u.text(mgdl.jsonPrimitive.int, locale(lang.jsonPrimitive.content)),
            )
        }
    }

    @Test fun `rates are written as the contract says`() {
        for ((rate, unit, lang, want) in cases("rate")) {
            val u = GlucoseUnit.of(unit.jsonPrimitive.content)
            assertEquals(
                "$rate $unit $lang",
                want.jsonPrimitive.content,
                u.rateText(rate.jsonPrimitive.double, locale(lang.jsonPrimitive.content)),
            )
        }
    }

    @Test fun `typed mmol per litre becomes the stored mg per dL on the step`() {
        for ((shown, step, want) in cases("input")) {
            assertEquals(
                "$shown on $step",
                want.jsonPrimitive.double,
                GlucoseUnit.MMOL.stored(shown.jsonPrimitive.double, step.jsonPrimitive.double),
                1e-9,
            )
        }
    }

    @Test fun `the symbols are the contract's`() {
        val symbols = contract.getValue("symbol").jsonObject
        for (u in GlucoseUnit.entries) {
            val (value, rate) = symbols.getValue(u.key).jsonArray.map { it.jsonPrimitive.content }
            assertEquals(value, u.symbol)
            assertEquals(rate, u.rateSymbol)
        }
    }

    /** Every glucose format has its mmol/L variant, and every value of it is stored as a valid value and shown back. */
    @Test fun `every mmol per litre variant comes back unchanged`() {
        val glucose = SettingsSpec.NUMBERS.values.filter { it.unit.startsWith("mg/dL") }
        assertTrue(glucose.size >= 10)
        for (f in glucose) {
            val m = checkNotNull(f.mmol) { "${f.id} has no mmol variant" }
            val steps = Math.round((m.max - m.min) / m.step)
            val grid = (0..steps).map { m.min + it * m.step } + m.presets
            for (x in grid) {
                val stored = f.stored(x, GlucoseUnit.MMOL)
                assertTrue("${f.id}: $x mmol/L → $stored", f.valid(stored))
                assertEquals("${f.id}: $x mmol/L → $stored", x, f.shown(stored, GlucoseUnit.MMOL), 1e-9)
                assertTrue("${f.id}: $x is on the variant's grid", m.valid(f.shown(stored, GlucoseUnit.MMOL)))
            }
            assertEquals(
                "${f.id}: the variant's default is the stored default shown",
                m.default,
                f.shown(f.default, GlucoseUnit.MMOL),
                1e-9,
            )
            // mg/dL is the format itself
            assertEquals(f, f.inUnit(GlucoseUnit.MGDL))
            assertEquals(f.default, f.stored(f.default, GlucoseUnit.MGDL), 0.0)
        }
    }
}
