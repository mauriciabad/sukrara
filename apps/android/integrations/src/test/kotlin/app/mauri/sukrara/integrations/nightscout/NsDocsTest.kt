// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.nightscout

import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.integrations.CurrentSensor
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
