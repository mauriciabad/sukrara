// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.share.ble

import app.mauri.sukrara.core.model.AlertUiState
import app.mauri.sukrara.core.model.GlucoseHub
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.LinkState
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.SourceStatus
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.core.testing.InMemorySecureStore
import app.mauri.sukrara.core.testing.InMemoryTreatmentsRepository
import app.mauri.sukrara.protocol.PublicKeyHex
import app.mauri.sukrara.protocol.Wire
import app.mauri.sukrara.protocol.event.Rumor
import app.mauri.sukrara.protocol.event.RumorCodec
import app.mauri.sukrara.protocol.invite.InviteLink
import app.mauri.sukrara.protocol.invite.Invites
import app.mauri.sukrara.protocol.invite.NearbyPairing
import app.mauri.sukrara.share.FakeRelayTransport
import app.mauri.sukrara.share.OwnerFixtures
import app.mauri.sukrara.share.OwnerFixtures.NOW
import app.mauri.sukrara.share.OwnerFixtures.crypto
import app.mauri.sukrara.share.owner.ApproveResult
import app.mauri.sukrara.share.owner.OwnerEngine
import app.mauri.sukrara.share.owner.OwnerIdentityStore
import app.mauri.sukrara.share.owner.usecase.ControlEvents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** The wearer's engine with a fake Bluetooth server: what goes over Bluetooth and what comes back. */
class OwnerNearbyTest {
    private val ctx = OwnerFixtures.context()
    private val relay = FakeRelayTransport()
    private val nearby = FakeNearbyServer()
    private val store = OwnerIdentityStore(InMemorySecureStore())

    private fun TestScope.engine(clock: FakeClock = FakeClock(Instant.ofEpochSecond(NOW))) =
        OwnerEngine(
            ctx,
            store,
            relay,
            NoHub,
            { _, _ -> true },
            InMemoryTreatmentsRepository(),
            clock,
            StandardTestDispatcher(testScheduler),
            nearby = nearby,
        )

    private suspend fun state() = (store.load() as OwnerIdentityStore.Load.Loaded).state

    @Test fun theBackupAndTheInviteNeverGoOverBluetooth() =
        runTest {
            val e = engine()
            assertTrue(e.createIdentity(backgroundScope, null))
            runCurrent()
            val link = InviteLink.parse(e.createInvite("Mom")!!)!!
            runCurrent()
            val identity = state().identity(crypto).public
            val inviteKey = Invites.inviteKey(crypto, link).public.hex
            val backupD = Wire.backupD(identity)

            assertTrue(
                "the relays got both",
                relay.published.any { it.tag("d") == backupD } && relay.published.any { it.tag("p") == inviteKey },
            )
            assertTrue(nearby.published.none { it.tag("d") == backupD })
            assertTrue(nearby.published.none { it.tag("p") == inviteKey })
            // the rest (roster, settings…) does go to nearby followers
            assertTrue(nearby.published.any { it.tag("d") == Wire.rosterD(state().sid) })
            assertNotNull(nearby.host)
            e.stop()
            assertNull(nearby.host)
        }

    @Test fun aRequestOverBluetoothReachesTheWearerAndAForgedCopyDoesNot() =
        runTest {
            val e = engine()
            assertTrue(e.createIdentity(backgroundScope, null))
            runCurrent()
            val link = InviteLink.parse(e.createInvite("Mom")!!)!!
            val mom = crypto.generate()
            val request = OwnerFixtures.request(mom, state(), link.token, "Mom")
            val host = nearby.host!!

            // same id, broken signature: dropped before it is marked seen, so the real one still counts
            host.onEvent(request.copy(sig = "0".repeat(128)), "aa:bb")
            runCurrent()
            assertNull(e.pendingRequest(mom.public.hex))

            host.onEvent(request, "aa:bb")
            runCurrent()
            assertNotNull(e.pendingRequest(mom.public.hex))
        }

    @Test fun theHelloProofChecksOnlyActiveFollowersAndForgetRemovesTheFollower() =
        runTest {
            val e = engine()
            assertTrue(e.createIdentity(backgroundScope, null))
            runCurrent()
            val link = InviteLink.parse(e.createInvite("Mom")!!)!!
            val mom = crypto.generate()
            relay.deliver("inbox", OwnerFixtures.request(mom, state(), link.token, "Mom"))
            runCurrent()
            val host = nearby.host!!
            assertTrue(host.followers().isEmpty())
            e.approve(mom.public.hex)
            runCurrent()
            assertEquals(listOf(mom.public), host.followers().toList())
            assertEquals(state().sid, host.sid())

            e.forget(mom.public.hex)
            runCurrent()
            assertTrue(host.followers().isEmpty())
            assertEquals(listOf(mom.public.hex), nearby.forgotten)
        }

    @Test fun pairNearbyOffersAnInviteInInfoUntilTheFirstRequestAndBothPhonesShowTheSameCode() =
        runTest {
            val clock = FakeClock(Instant.ofEpochSecond(NOW))
            val e = engine(clock)
            assertTrue(e.createIdentity(backgroundScope, null))
            runCurrent()
            val host = nearby.host!!
            assertNull("closed: INFO offers nothing", host.pairing())
            relay.published.clear()

            assertTrue(e.openNearbyPairing())
            runCurrent()
            val pairing = host.pairing()!!
            assertEquals(state().identity(crypto).public, pairing.identity)
            assertEquals(NOW + 600, pairing.expiry)
            assertEquals(e.nearbyPairing.value!!.token, pairing.token)
            assertTrue("no invite wrap on the relays", relay.published.none { it.kind == Wire.KIND_WRAP })
            val invite = state().invites.single { it.token == pairing.token }
            assertTrue(invite.nearby)
            // the offer carries only the commitment to the wearer's nonce
            assertEquals(NearbyPairing.commit(invite.pairNonce!!), pairing.commit)
            // a restore keeps it a nearby invite
            val st = state()
            val restored = ControlEvents.fromBackup(crypto, st.identityHex, st.deviceHex, ControlEvents.backupPayload(crypto, st))!!
            assertTrue(restored.invites.single { it.token == pairing.token }.nearby)

            // a request without the follower's nonce claims nothing
            val stranger = crypto.generate()
            host.onEvent(OwnerFixtures.request(stranger, state(), pairing.token, "Mom"), "cc:dd")
            runCurrent()
            assertNull(e.pendingRequest(stranger.public.hex))
            assertNotNull(host.pairing())

            val mom = crypto.generate()
            val na = "ab".repeat(32)
            host.onEvent(OwnerFixtures.request(mom, state(), pairing.token, "Mom", nonce = na), "aa:bb")
            runCurrent()
            val request = e.pendingRequest(mom.public.hex)!!
            assertTrue(request.nearby && !request.needsTypedCode && !request.allowFromNotification)
            assertEquals(
                NearbyPairing.code(state().identity(crypto).public, mom.public, pairing.token, na, invite.pairNonce!!),
                request.code,
            )
            assertNull("single use: the first request closes it", host.pairing())
            assertNull(e.nearbyPairing.value)
            // the claimer gets the wearer's nonce at once (relays and Bluetooth), so its phone shows the same code
            val opened =
                relay.published.filter { it.kind == Wire.KIND_WRAP && it.tag("p") == mom.public.hex }.mapNotNull { w ->
                    crypto.unwrap(mom.secret, w)?.let { RumorCodec.decode(it.kind, it.content) }
                }
            assertEquals(listOf(Rumor.PairNonce(invite.pairNonce!!)), opened)

            // the code is compared, not typed: Allow sends the key
            assertEquals(ApproveResult.Allowed, e.approve(mom.public.hex))
            runCurrent()
            assertEquals(listOf(mom.public), host.followers().toList())
        }

    @Test fun stopAndExpiryClosePairNearby() =
        runTest {
            val clock = FakeClock(Instant.ofEpochSecond(NOW))
            val e = engine(clock)
            assertTrue(e.createIdentity(backgroundScope, null))
            runCurrent()
            val host = nearby.host!!

            assertTrue(e.openNearbyPairing())
            runCurrent()
            val token = host.pairing()!!.token
            e.closeNearbyPairing()
            runCurrent()
            assertNull(host.pairing())
            assertTrue("Stop drops the unclaimed invite", state().invites.none { it.token == token })
            // a request with it afterwards opens nothing
            val late = crypto.generate()
            host.onEvent(OwnerFixtures.request(late, state(), token, "Late"), "aa:bb")
            runCurrent()
            assertNull(e.pendingRequest(late.public.hex))

            assertTrue(e.openNearbyPairing())
            runCurrent()
            assertNotNull(host.pairing())
            clock.advance(java.time.Duration.ofSeconds(601))
            testScheduler.advanceTimeBy(601_000)
            runCurrent()
            assertNull(host.pairing())
            assertNull(e.nearbyPairing.value)
        }

    private object NoHub : GlucoseHub {
        override val state = MutableStateFlow(GlucoseState.Initial)

        override suspend fun ingest(readings: List<Reading>, from: SourceId) = Unit

        override fun setSource(status: SourceStatus) = Unit

        override fun setSensor(sensor: SensorState) = Unit

        override fun setAlerts(alerts: AlertUiState) = Unit

        override fun setLink(link: LinkState?) = Unit
    }
}
