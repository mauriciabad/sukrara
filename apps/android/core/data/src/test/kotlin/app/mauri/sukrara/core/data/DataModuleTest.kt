// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.core.data.db.OutboxEntity
import app.mauri.sukrara.core.model.DefaultDispatchers
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.testing.FakeClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Duration
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class DataModuleTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = FakeClock()
    private val data = DataModule(context, "0.12.0-test", DefaultDispatchers, clock, secretBox = SoftwareSecretBox())

    @After
    fun close() = data.database.close()

    @Test
    fun `ready prunes old history but keeps recent readings and pending outbox entries`() =
        runTest {
            val now = clock.now()
            data.readings.upsert(
                listOf(
                    Reading(now - Duration.ofDays(91), 100, null, SourceId.Libre3),
                    Reading(now - Duration.ofDays(89), 110, null, SourceId.Libre3),
                ),
            )
            val old = (now - Duration.ofDays(30)).toEpochMilli()
            data.database.outbox().put(OutboxEntity("pending", 1, "{}", createdAt = old, sentAt = null))
            data.database.outbox().put(OutboxEntity("sent", 1, "{}", createdAt = old, sentAt = old))

            data.ready()

            assertEquals(
                listOf(110),
                data.readings
                    .window(Instant.EPOCH)
                    .first()
                    .map { it.mgdl },
            )
            assertEquals(
                listOf("pending"),
                data.database
                    .outbox()
                    .pending()
                    .first()
                    .map { it.id },
            )
        }

    /**
     * #78: the sharing ledger holds epoch seconds (the owner engine's clock); its prune used a milliseconds cutoff, so
     * every start emptied it and the wearer handled again every gift wrap the relays still kept.
     */
    @Test
    fun `ready keeps the gift wraps seen in the last 14 days and drops older ones`() =
        runTest {
            val now = clock.now()
            data.markWrapSeen("today", now.epochSecond)
            data.markWrapSeen("last-week", (now - Duration.ofDays(7)).epochSecond)
            data.markWrapSeen("long-ago", (now - Duration.ofDays(15)).epochSecond)

            data.ready()

            assertEquals(listOf(true, true, false), listOf("today", "last-week", "long-ago").map { data.wrapSeen(it) })
        }

    @Test
    fun `ready re-reports damaged files kept by an earlier run`() =
        runTest {
            File(context.filesDir, "state").mkdirs()
            File(context.filesDir, "state/pairing.json.corrupt-1700000000000").writeText("{bad")
            File(context.filesDir, "secure").mkdirs()
            File(context.filesDir, "secure/identity.owner.bin.corrupt-1700000000001").writeText("x")
            File(context.filesDir.parentFile, "datastore").mkdirs()
            File(context.filesDir.parentFile, "datastore/alerts.preferences_pb.corrupt-1700000000002").writeText("x")

            data.ready()

            assertEquals(
                setOf(
                    StoreKind.File to "pairing.json",
                    StoreKind.Secret to "identity.owner",
                    StoreKind.Settings to "alerts",
                ),
                data.corruption.files.value
                    .map { it.kind to it.store }
                    .toSet(),
            )
        }
}
