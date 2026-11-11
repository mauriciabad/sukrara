// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.share.owner

import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.core.testing.InMemorySecureStore
import app.mauri.sukrara.core.testing.InMemoryTreatmentsRepository
import app.mauri.sukrara.protocol.Wire
import app.mauri.sukrara.protocol.event.AlertPayload
import app.mauri.sukrara.share.FakeHub
import app.mauri.sukrara.share.FakeRelayTransport
import app.mauri.sukrara.share.OwnerFixtures
import app.mauri.sukrara.share.OwnerFixtures.NOW
import app.mauri.sukrara.share.OwnerFixtures.crypto
import app.mauri.sukrara.share.ble.FakeNearbyServer
import app.mauri.sukrara.share.owner.usecase.ControlEvents
import app.mauri.sukrara.share.owner.usecase.CreateIdentity
import app.mauri.sukrara.share.owner.usecase.PublishReadings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** *Over the internet* and the upload interval: what reaches the relays and what still goes over Bluetooth. */
class RelayPolicyTest {
    private val ctx = OwnerFixtures.context()
    private val relay = FakeRelayTransport()
    private val nearby = FakeNearbyServer()
    private val hub = FakeHub()
    private val store = OwnerIdentityStore(InMemorySecureStore())
    private val policy = MutableStateFlow(RelayPolicy())

    private fun TestScope.engine() =
        OwnerEngine(
            ctx,
            store,
            relay,
            hub,
            SeenWraps.None,
            InMemoryTreatmentsRepository(),
            FakeClock(Instant.ofEpochSecond(NOW)),
            StandardTestDispatcher(testScheduler),
            nearby = nearby,
            relayPolicy = policy,
        )

    private fun reading(ago: Long, mgdl: Int = 123) = Reading(Instant.ofEpochSecond(NOW - ago), mgdl, 0.5, SourceId.Libre3)

    private val alert =
        AlertPayload("a1", "low", fire = true, at = NOW, t = NOW - 30, sgv = 65, rate = -1.0, threshold = 70.0, n = 1, tr = 0)

    @Test fun offNothingReachesARelayAndBluetoothGetsEveryReadingThenOnSendsWhatTheRelaysMissed() =
        runTest {
            policy.value = RelayPolicy(on = false)
            val e = engine()
            assertTrue(e.createIdentity(backgroundScope, null))
            runCurrent()
            assertFalse("the relays never connect", relay.started)
            hub.push(reading(30))
            runCurrent()
            e.publishAlert(alert)
            runCurrent()
            assertTrue(relay.published.isEmpty())
            assertTrue(nearby.published.any { it.kind == Wire.KIND_LIVE })
            assertTrue(nearby.published.any { it.kind == Wire.KIND_ALERT })

            val toBluetooth = nearby.published.size
            policy.value = RelayPolicy(on = true)
            runCurrent()
            assertTrue(relay.started)
            val sid = (store.load() as OwnerIdentityStore.Load.Loaded).state.sid
            assertTrue("the roster that waited", relay.published.any { it.tag("d") == Wire.rosterD(sid) })
            assertTrue("the hour's chunk", relay.published.any { it.tag("c") == Wire.glucoseC(sid) })
            assertEquals("Bluetooth followers already had it", toBluetooth, nearby.published.size)

            policy.value = RelayPolicy(on = false)
            runCurrent()
            assertFalse(relay.started)
            e.stop()
        }

    @Test fun anIntervalKeepsLiveEventsOffTheRelaysButNotAlertsOrBluetooth() =
        runTest {
            policy.value = RelayPolicy(intervalMin = 15)
            val e = engine()
            assertTrue(e.createIdentity(backgroundScope, null))
            runCurrent()
            hub.push(reading(90))
            runCurrent()
            e.publishAlert(alert)
            runCurrent()
            assertTrue(relay.published.none { it.kind == Wire.KIND_LIVE })
            assertTrue(relay.published.any { it.kind == Wire.KIND_ALERT })
            assertTrue(nearby.published.any { it.kind == Wire.KIND_LIVE })

            policy.value = RelayPolicy()
            runCurrent()
            hub.push(reading(30, 130))
            runCurrent()
            assertTrue("every reading again", relay.published.any { it.kind == Wire.KIND_LIVE })
            e.stop()
        }

    @Test fun theIntervalSpacesTheCurrentHoursChunk() {
        val now = Wire.hourOf(NOW) + 60
        val s = CreateIdentity(ctx)(null, now).state
        val p = PublishReadings(ctx).apply { chunkEvery = RelayPolicy(intervalMin = 15).chunkEvery }

        fun at(t: Long) = Reading(Instant.ofEpochSecond(t), 120, 1.0, SourceId.Libre3)
        assertEquals(1, p.onReadings(s, listOf(at(now - 30)), now).count { it.kind == Wire.KIND_DATA })
        assertEquals("10 min later: no chunk yet", 0, p.onReadings(s, listOf(at(now + 570)), now + 600).count { it.kind == Wire.KIND_DATA })
        assertEquals(0, p.tick(s, now + 840).size)
        assertEquals(1, p.tick(s, now + 900).count { it.kind == Wire.KIND_DATA })
        assertEquals(PublishReadings.CHUNK_EVERY, RelayPolicy().chunkEvery)
    }

    @Test fun pairNearbyIsRememberedOnThisPhoneOnlyAndRemoveForgetsIt() =
        runTest {
            val e = engine()
            assertTrue(e.createIdentity(backgroundScope, null))
            runCurrent()
            assertTrue(e.openNearbyPairing())
            runCurrent()
            val host = nearby.host!!
            val token = host.pairing()!!.token
            val mom = crypto.generate()
            // a nearby request carries the follower's pairing nonce `n` (PROTOCOL-v2 §Nearby pairing); Allow follows
            // the code comparison, nothing is typed
            val state = (store.load() as OwnerIdentityStore.Load.Loaded).state
            host.onEvent(OwnerFixtures.request(mom, state, token, "Mom", nonce = "ab".repeat(32)), "aa:bb")
            runCurrent()
            assertTrue(e.pendingRequest(mom.public.hex)!!.nearby)
            assertEquals(ApproveResult.Allowed, e.approve(mom.public.hex))
            runCurrent()
            val st = (store.load() as OwnerIdentityStore.Load.Loaded).state
            assertEquals(setOf(mom.public.hex), st.nearbyPaired)
            assertEquals(setOf(mom.public.hex), (e.state.value as OwnerShareState.Sharing).nearbyPaired)
            val restored = ControlEvents.fromBackup(crypto, st.identityHex, st.deviceHex, ControlEvents.backupPayload(crypto, st))!!
            assertTrue("never in the owner backup", restored.nearbyPaired.isEmpty())

            e.remove(mom.public.hex)
            runCurrent()
            assertTrue((store.load() as OwnerIdentityStore.Load.Loaded).state.nearbyPaired.isEmpty())
            e.stop()
        }
}
