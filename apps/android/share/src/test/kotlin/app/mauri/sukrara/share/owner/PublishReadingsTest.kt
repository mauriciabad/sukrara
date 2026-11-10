// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.share.owner

import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.protocol.SignedEvent
import app.mauri.sukrara.protocol.Wire
import app.mauri.sukrara.protocol.event.LivePayload
import app.mauri.sukrara.protocol.event.ReadingsCodec
import app.mauri.sukrara.protocol.event.SensorLife
import app.mauri.sukrara.protocol.event.WireReading
import app.mauri.sukrara.share.OwnerFixtures
import app.mauri.sukrara.share.OwnerFixtures.crypto
import app.mauri.sukrara.share.owner.usecase.CreateIdentity
import app.mauri.sukrara.share.owner.usecase.PublishReadings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Synthetic readings only. Events are decoded with :core:protocol as a follower would. */
class PublishReadingsTest {
    private val ctx = OwnerFixtures.context()
    private val now = Wire.hourOf(OwnerFixtures.NOW) + 1800
    private val s = CreateIdentity(ctx)(null, now).state
    private val publisher = PublishReadings(ctx)

    private fun reading(t: Long, mgdl: Int) = Reading(Instant.ofEpochSecond(t), mgdl, 1.0, SourceId.Libre3)

    private fun decrypt(content: String) = crypto.decrypt(s.readKey(crypto).secret, s.device(crypto).public, content)!!

    @Test fun aFreshReadingGoesLiveAndAnEarlierHourAsAChunkAtOnce() {
        val out = publisher.onReadings(s, listOf(reading(now - 3700, 100), reading(now - 60, 140)), now)
        val live = out.single { it.kind == Wire.KIND_LIVE }
        assertEquals(140, ReadingsCodec.decodeLive(decrypt(live.content)).reading!!.sgv)
        assertEquals(s.device(crypto).public.hex, live.pubkey)
        val chunk = out.single { it.tag("d") == Wire.chunkD(s.sid, Wire.hourOf(now - 3700)) }
        assertEquals(listOf(100), ReadingsCodec.decodeChunk(decrypt(chunk.content)).readings.map { it.sgv })
    }

    @Test fun anOldReadingIsNotLiveAndTheCurrentHourChunkIsPacedByTheTick() {
        val first = publisher.onReadings(s, listOf(reading(now - 900, 120)), now)
        assertTrue(first.none { it.kind == Wire.KIND_LIVE })
        assertEquals(Wire.chunkD(s.sid, Wire.hourOf(now)), first.single().tag("d"))
        // a minute later the current hour changed again: it waits for the next tick (≥ 5 min after the last chunk)
        assertTrue(publisher.onReadings(s, listOf(reading(now - 840, 121)), now + 60).none { it.kind == Wire.KIND_DATA })
        assertTrue(publisher.tick(s, now + 120).isEmpty())
        assertEquals(Wire.chunkD(s.sid, Wire.hourOf(now)), publisher.tick(s, now + PublishReadings.CHUNK_EVERY).single().tag("d"))
    }

    private fun live(events: List<SignedEvent>) =
        events
            .filter {
                it.kind == Wire.KIND_LIVE
            }.map { ReadingsCodec.decodeLive(decrypt(it.content)) }

    @Test fun everyReadingsLiveEventCarriesTheSensor() {
        val sensor = SensorLife(now - 3 * Wire.DAY, now + 12 * Wire.DAY, now - 3 * Wire.DAY + 3600)
        publisher.onSensor(s, sensor, now - 120)
        val out = live(publisher.onReadings(s, listOf(reading(now - 60, 140)), now))
        assertEquals(LivePayload(WireReading(now - 60, 140, 1.0), sensor), out.single())
    }

    /** #55: a new sensor warms up with no reading; followers learn it at once and again every tick until the first reading. */
    @Test fun aNewSensorIsSentWithoutAReadingAndEveryTickWhileItWarmsUp() {
        publisher.onReadings(s, listOf(reading(now - 240, 150)), now - 200)
        val old = SensorLife(now - 15 * Wire.DAY, now - 100, now - 15 * Wire.DAY + 3600)
        assertEquals(listOf(LivePayload(null, old)), live(publisher.onSensor(s, old, now - 200)))
        // the same sensor again (a re-read pairing) sends nothing
        assertTrue(publisher.onSensor(s, old, now - 190).isEmpty())
        val fresh = SensorLife(now - 60, now + 15 * Wire.DAY, now + 3540)
        assertEquals(listOf(LivePayload(null, fresh)), live(publisher.onSensor(s, fresh, now - 60)))
        val step = PublishReadings.CHUNK_EVERY
        assertEquals(listOf(LivePayload(null, fresh)), live(publisher.tick(s, now)))
        assertEquals(listOf(LivePayload(null, fresh)), live(publisher.tick(s, now + step)))
        // the first reading ends the warm-up: it carries the sensor, and the ticks stop repeating it
        val first = live(publisher.onReadings(s, listOf(reading(now + 3600, 110)), now + 3600))
        assertEquals(listOf(LivePayload(WireReading(now + 3600, 110, 1.0), fresh)), first)
        assertTrue(live(publisher.tick(s, now + 3600 + step)).isEmpty())
    }

    @Test fun aWarmUpWithNoReadingStopsBeingRepeatedAfterItsGrace() {
        val fresh = SensorLife(now - 60, now + 15 * Wire.DAY, now + 3540)
        publisher.onSensor(s, fresh, now - 60)
        assertEquals(1, live(publisher.tick(s, now + 3540 + PublishReadings.WARMUP_GRACE - 1)).size)
        assertTrue(live(publisher.tick(s, now + 3540 + PublishReadings.WARMUP_GRACE)).isEmpty())
    }

    @Test fun aSensorEndedEarlyOrRemovedIsSentAtOnceAndNothingBeforeOneIsKnown() {
        assertTrue(publisher.onSensor(s, null, now - 300).isEmpty())
        val sensor = SensorLife(now - 3 * Wire.DAY, now + 12 * Wire.DAY, now - 3 * Wire.DAY + 3600)
        publisher.onSensor(s, sensor, now - 300)
        val ended = sensor.copy(end = now - 10)
        assertEquals(listOf(LivePayload(null, ended)), live(publisher.onSensor(s, ended, now - 10)))
        // removed: a live event with no sensor clears what followers knew
        val cleared = publisher.onSensor(s, null, now)
        assertEquals(listOf(LivePayload(null, null)), live(cleared))
        assertEquals("""{"v":2}""", decrypt(cleared.single().content))
        assertTrue(live(publisher.tick(s, now + PublishReadings.CHUNK_EVERY)).isEmpty())
    }

    @Test fun republishedChunksHaveStrictlyIncreasingCreatedAt() {
        publisher.onReadings(s, listOf(reading(now - 3700, 100)), now)
        val first = publisher.republish(s, now).single()
        val second = publisher.republish(s, now).single()
        assertTrue(second.createdAt > first.createdAt)
    }
}
