// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.nightscout

import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.TreatmentOrigin
import app.mauri.sukrara.integrations.CurrentSensor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/** Untested sensor families are told apart on the site, and the conflict pause still knows them as this uploader (#147). */
class NsDocsTest {
    private val device = "Sukrara-1a2b3c"
    private val now = Instant.ofEpochSecond(1_800_000_000)

    private fun entryDevice(source: SourceId) =
        NsDocs
            .entry(Reading(now, 120, null, source), null, NsDocs.entryDevice(device, source), ZoneOffset.UTC, NsApi.V1)["device"]!!
            .jsonPrimitive.content

    @Test fun aVerifiedSensorKeepsTheInstallName() {
        assertEquals(device, entryDevice(SourceId.Libre3))
        assertEquals(device, entryDevice(SourceId.Juggluco))
    }

    @Test fun anUntestedFamilyIsNamed() {
        assertEquals("Sukrara-1a2b3c · Dexcom G7 (untested)", entryDevice(SourceId.DexcomG7))
        assertEquals("Sukrara-1a2b3c · Dexcom G6 (untested)", entryDevice(SourceId.DexcomG6))
        assertEquals("Sukrara-1a2b3c · Accu-Chek SmartGuide (untested)", entryDevice(SourceId.SmartGuide))
        assertEquals("Sukrara-1a2b3c · Aidex X (untested)", entryDevice(SourceId.AidexX))
    }

    @Test fun theConflictPauseKnowsBothNames() {
        assertTrue(NightscoutQueue.ours(device, device))
        assertTrue(NightscoutQueue.ours(entryDevice(SourceId.DexcomG7), device))
        assertFalse(NightscoutQueue.ours("Sukrara-ffffff · Dexcom G7 (untested)", device))
        assertFalse(NightscoutQueue.ours("xDrip-DexcomG7", device))
    }

    private fun doc(json: String) = Json.parseToJsonElement(json).jsonObject

    private fun t(
        kind: TreatmentKind,
        amount: Double,
        note: String = "",
    ) = Treatment("ours-1", kind, amount, now, note, now, origin = TreatmentOrigin.Phone)

    private val at = NsDocs.isoUtc(now)

    /** #119: another uploader's same treatment (time ± 2 min, the amount in the field Nightscout counts) is found. */
    @Test fun sameTreatmentMatchesTheCountedField() {
        val meal = doc("""{"_id":"m","eventType":"Meal Bolus","created_at":"$at","insulin":4.5,"carbs":45,"enteredBy":"AndroidAPS"}""")
        assertEquals("m:insulin", NsDocs.sameTreatment(t(TreatmentKind.RapidInsulin, 4.5), listOf(meal), emptySet()))
        assertEquals("m:carbs", NsDocs.sameTreatment(t(TreatmentKind.Carbs, 45.0), listOf(meal), emptySet()))
        assertNull(NsDocs.sameTreatment(t(TreatmentKind.RapidInsulin, 4.0), listOf(meal), emptySet()))
        // one of their documents stands for one entry of ours per field
        assertNull(NsDocs.sameTreatment(t(TreatmentKind.Carbs, 45.0), listOf(meal), setOf("m:carbs")))
        // xDrip+'s eventType and a local-offset created_at 90 s later
        val local = (now + Duration.ofSeconds(90)).atOffset(ZoneOffset.ofHours(2)).toString()
        val xdrip = doc("""{"_id":"x","eventType":"<none>","created_at":"$local","insulin":"2"}""")
        assertEquals("x:insulin", NsDocs.sameTreatment(t(TreatmentKind.RapidInsulin, 2.0), listOf(xdrip), emptySet()))
    }

    @Test fun sameTreatmentNeverMatchesOursDeletedOrTooFar() {
        val far = NsDocs.isoUtc(now + Duration.ofSeconds(121))
        val docs =
            listOf(
                doc("""{"_id":"1","created_at":"$at","carbs":20,"identifier":"ours-1","enteredBy":"x"}"""),
                doc("""{"_id":"2","created_at":"$at","carbs":20,"app":"Sukrara"}"""),
                doc("""{"_id":"3","created_at":"$at","carbs":20,"isValid":false}"""),
                doc("""{"_id":"4","created_at":"$far","carbs":20}"""),
                doc("""{"created_at":"$at","carbs":20}"""),
            )
        assertNull(NsDocs.sameTreatment(t(TreatmentKind.Carbs, 20.0), docs, emptySet()))
    }

    @Test fun notesAndLongActingMatchTheSameNoteText() {
        val note = doc("""{"_id":"n","eventType":"Note","created_at":"$at","notes":"Long-acting insulin 18 U"}""")
        assertEquals("n:notes", NsDocs.sameTreatment(t(TreatmentKind.LongInsulin, 18.0), listOf(note), emptySet()))
        assertNull(NsDocs.sameTreatment(t(TreatmentKind.LongInsulin, 20.0), listOf(note), emptySet()))
    }

    @Test fun sensorNotesNameTheRealFamily() {
        fun notes(source: SourceId) =
            NsDocs
                .sensorEvents(CurrentSensor("ABC123", now - Duration.ofDays(1), now + Duration.ofDays(9), source), device, ZoneOffset.UTC)
                .map { it["notes"]!!.jsonPrimitive.content }
                .distinct()
        assertEquals(listOf("Libre 3 ABC123"), notes(SourceId.Libre3))
        assertEquals(listOf("Dexcom G7 (untested) ABC123"), notes(SourceId.DexcomG7))
        assertEquals(listOf("Aidex X (untested) ABC123"), notes(SourceId.AidexX))
    }
}
