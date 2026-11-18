// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import app.mauri.sukrara.core.model.LifeSettings
import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.core.model.SourceKind
import app.mauri.sukrara.core.model.SourceSpec
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The source file's side of the sensor life (docs/44 decision 10): one wear length per source, remembered across
 * sensors and source changes; the manual start belongs to the source and person it was set for.
 */
class SourceRecordLifeTest {
    private val nightscout = SourceSpec(Person.Yours, SourceKind.Nightscout)
    private val llu = SourceSpec(Person.Yours, SourceKind.LibreLinkUp)
    private val set = SourceRecord.of(nightscout).copy(manualStart = 1_792_000_000)

    @Test fun `switching sources clears the manual start, the same answer again keeps it`() {
        assertEquals(1_792_000_000L, set.with(nightscout).manualStart)
        assertEquals(0L, set.with(llu).manualStart)
        // and back: the old start doesn't come back (it would say Sensor ended and keep no data quiet)
        assertEquals(0L, set.with(llu).with(nightscout).manualStart)
    }

    @Test fun `the person changing clears the manual start`() {
        assertEquals(0L, set.with(SourceSpec(Person.Theirs, SourceKind.Nightscout)).manualStart)
    }

    @Test fun `each source keeps its own wear length through source changes`() {
        val r = SourceRecord.of(llu).withWear(SourceKind.LibreLinkUp, 20160).withWear(SourceKind.Nightscout, 14400)
        val back = r.with(nightscout).with(llu)
        assertEquals(20160, back.lifeOf(SourceKind.LibreLinkUp, llu).wearMin)
        assertEquals(14400, back.lifeOf(SourceKind.Nightscout, nightscout).wearMin)
        // a source that says its length has none
        assertEquals(r, r.withWear(SourceKind.Sensor, 20160))
    }

    @Test fun `a stored length that isn't valid reads as the source's own default or nearest`() {
        // LibreLinkUp: 14 or 15 days only, else 15
        assertEquals(
            21600,
            SourceRecord
                .of(llu)
                .withWear(SourceKind.LibreLinkUp, 14400)
                .lifeOf(SourceKind.LibreLinkUp, llu)
                .wearMin,
        )
        assertEquals(21600, SourceRecord.of(llu).lifeOf(SourceKind.LibreLinkUp, llu).wearMin)
        // the others: whole days from 1 to 30
        assertEquals(
            43200,
            SourceRecord
                .of(nightscout)
                .withWear(SourceKind.Nightscout, 99_999)
                .lifeOf(SourceKind.Nightscout, nightscout)
                .wearMin,
        )
    }

    @Test fun `the manual start is only the running source's`() {
        assertEquals(LifeSettings(Person.Yours, 21600, 1_792_000_000), set.lifeOf(SourceKind.Nightscout, nightscout))
        assertEquals(0L, set.lifeOf(SourceKind.LibreLinkUp, llu).manualStartS)
    }
}
