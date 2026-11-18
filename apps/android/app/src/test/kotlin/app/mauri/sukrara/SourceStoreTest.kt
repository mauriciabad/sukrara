// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.core.model.SourceKind
import app.mauri.sukrara.core.model.SourceSpec
import app.mauri.sukrara.core.testing.InMemoryStateStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A damaged source file (read as "no answer" after quarantine) never stops monitoring: the other copy answers. */
class SourceStoreTest {
    private val primary = InMemoryStateStore(SourceRecord())
    private val backup = InMemoryStateStore(SourceRecord())
    private val store = SourceStore(primary, backup)
    private val yours = SourceRecord.of(SourceSpec.YourSensor)
    private val theirs = SourceRecord.of(SourceSpec.TheirSukrara)

    @Test fun writesBothCopies() =
        runTest {
            store.update { yours }
            assertEquals(SourceSpec.YourSensor, primary.data.first().spec)
            assertEquals(SourceSpec.YourSensor, backup.data.first().spec)
        }

    @Test fun storesTheContractIds() {
        assertEquals(SourceRecord("yours", "sensor"), yours)
        assertEquals(SourceRecord("theirs", "sukrara"), theirs)
        assertEquals(SourceSpec(Person.Theirs, SourceKind.PhoneApp), SourceRecord("theirs", "phoneApp").spec)
    }

    @Test fun anUnknownIdOrAPairNoSourceOffersIsNoAnswer() {
        assertNull(SourceRecord("yours", "libreLinkUp").spec)
        assertNull(SourceRecord("theirs", "sensor").spec)
        assertNull(SourceRecord("someone", "sensor").spec)
    }

    @Test fun damagedPrimaryKeepsTheSourceAndHealsIt() =
        runTest {
            store.update { yours }
            primary.update { SourceRecord() } // what TypedStore gives after quarantining a damaged source.json
            assertEquals(SourceSpec.YourSensor, store.data.first().spec)
            store.heal()
            assertEquals(SourceSpec.YourSensor, primary.data.first().spec)
        }

    @Test fun damagedBackupIsRewrittenFromThePrimary() =
        runTest {
            store.update { theirs }
            backup.update { SourceRecord() }
            assertEquals(SourceSpec.TheirSukrara, store.data.first().spec)
            store.heal()
            assertEquals(SourceSpec.TheirSukrara, backup.data.first().spec)
        }

    @Test fun aClearCutBetweenTheTwoWritesKeepsMonitoring() =
        runTest {
            store.update { yours }
            primary.update { SourceRecord() } // the clear reached source.json, not the backup
            store.heal()
            assertEquals(SourceSpec.YourSensor, store.data.first().spec)
        }

    @Test fun clearingTheSourceClearsBothCopies() =
        runTest {
            store.update { yours }
            store.update { SourceRecord() }
            store.heal()
            assertNull(store.data.first().spec)
        }
}
