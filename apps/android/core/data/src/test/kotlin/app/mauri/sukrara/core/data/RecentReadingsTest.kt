// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.data

import app.mauri.sukrara.core.data.readings.RecentReadings
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.core.testing.InMemoryReadingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * The readings kept in device-protected storage (#160): what the card and the alerts have right after a restart, and
 * the readings taken before the unlock until the unlock merges them into the history, each once.
 */
class RecentReadingsTest {
    @get:Rule val tmp = TemporaryFolder()

    private val clock = FakeClock()
    private val dir by lazy { tmp.newFolder("de") }
    private val recentFile get() = File(dir, "recent-readings.json")
    private val lockedFile get() = File(dir, "locked-readings.csv")

    private fun store() = RecentReadings(recentFile, lockedFile, CorruptionLog(), clock, Dispatchers.IO)

    private fun at(
        minutesAgo: Long,
        mgdl: Int = 100,
        source: SourceId = SourceId.Libre3,
    ) = Reading(clock.now() - Duration.ofMinutes(minutesAgo), mgdl, -0.5, source)

    @Test
    fun `only this phone's own readings are kept, never a followed person's or imported ones`() =
        runTest {
            val recent = store()
            recent.add(listOf(at(3), at(2, source = SourceId.Relay), at(1, source = SourceId("import-file"))), locked = true)
            assertEquals(listOf(at(3)), recent.window(Instant.EPOCH).first())
            assertEquals(listOf(at(3)), recent.lockedReadings())
        }

    @Test
    fun `the file keeps the last 3 hours, which a restart reads back`() =
        runTest {
            val recent = store()
            recent.add(listOf(at(200), at(170), at(10)), locked = false)
            assertEquals("in memory, the whole day", 3, recent.window(Instant.EPOCH).first().size)
            assertEquals(listOf(at(170), at(10)), store().window(Instant.EPOCH).first())
            assertTrue("written while unlocked: nothing for the unlock to merge", store().lockedReadings().isEmpty())
        }

    @Test
    fun `the same reading twice is kept once, the newer value`() =
        runTest {
            val recent = store()
            recent.add(listOf(at(5, 100)), locked = true)
            recent.add(listOf(at(5, 101), at(4)), locked = true)
            assertEquals(listOf(101, 100), recent.window(Instant.EPOCH).first().map { it.mgdl })
            assertEquals(2, recent.lockedReadings().size)
        }

    @Test
    fun `the unlock merges the readings taken before it into the history once and forgets them`() =
        runTest {
            val history = InMemoryReadingsRepository()
            history.upsert(listOf(at(300), at(5)))
            val recent = store()
            recent.add(listOf(at(5), at(4), at(3)), locked = true)

            assertEquals(3, recent.mergeInto(history))
            assertFalse(lockedFile.exists())
            assertEquals(listOf(at(300), at(5), at(4), at(3)), history.range(Instant.EPOCH, clock.now()))
            assertEquals("nothing left to merge again", 0, store().mergeInto(history))
            assertEquals(4, history.range(Instant.EPOCH, clock.now()).size)
        }

    @Test
    fun `a line cut short by a crash is skipped, a file of another version is not read`() =
        runTest {
            store().add(listOf(at(2)), locked = true)
            lockedFile.appendText("17680")
            assertEquals(listOf(at(2)), store().lockedReadings())

            lockedFile.writeText("sukrara-locked-readings v0\n${at(1).t.toEpochMilli()},90,,libre3\n")
            assertTrue(store().lockedReadings().isEmpty())
            recentFile.writeText("""{"v":0,"readings":[{"t":${at(1).t.toEpochMilli()},"mgdl":90,"source":"libre3"}]}""")
            assertTrue(store().window(Instant.EPOCH).first().isEmpty())
        }

    @Test
    fun `a role change removes the old role's readings everywhere`() =
        runTest {
            val recent = store()
            recent.add(listOf(at(3), at(2, source = SourceId("synthetic"))), locked = true)
            recent.deleteFrom(setOf(SourceId.Libre3))
            assertEquals(listOf(at(2, source = SourceId("synthetic"))), recent.window(Instant.EPOCH).first())
            assertEquals(listOf(at(2, source = SourceId("synthetic"))), store().lockedReadings())
            assertEquals(listOf(at(2, source = SourceId("synthetic"))), store().window(Instant.EPOCH).first())
        }
}
