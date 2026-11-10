// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.share.ble

import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.core.testing.InMemoryReadingsRepository
import app.mauri.sukrara.core.testing.InMemorySecureStore
import app.mauri.sukrara.core.testing.InMemoryTreatmentsRepository
import app.mauri.sukrara.protocol.Wire
import app.mauri.sukrara.protocol.ble.BleMessages
import app.mauri.sukrara.protocol.event.Profile
import app.mauri.sukrara.protocol.event.Rumor
import app.mauri.sukrara.protocol.follow.FollowStatus
import app.mauri.sukrara.protocol.follow.FollowerState
import app.mauri.sukrara.protocol.follow.HeldKey
import app.mauri.sukrara.protocol.follow.NearbyPair
import app.mauri.sukrara.protocol.invite.NearbyPairing
import app.mauri.sukrara.share.FakeRelayTransport
import app.mauri.sukrara.share.OwnerFixtures
import app.mauri.sukrara.share.OwnerFixtures.NOW
import app.mauri.sukrara.share.OwnerFixtures.crypto
import app.mauri.sukrara.share.follower.FollowState
import app.mauri.sukrara.share.follower.FollowerCodec
import app.mauri.sukrara.share.follower.FollowerEngine
import app.mauri.sukrara.share.follower.FollowerRecord
import app.mauri.sukrara.share.follower.FollowerStore
import app.mauri.sukrara.share.follower.JoinResult
import app.mauri.sukrara.share.follower.NearbyOffer
import app.mauri.sukrara.share.follower.NearbySearch
import app.mauri.sukrara.share.follower.PairingCheck
import app.mauri.sukrara.share.owner.usecase.ApproveRequest
import app.mauri.sukrara.share.owner.usecase.CreateIdentity
import app.mauri.sukrara.share.owner.usecase.CreateInvite
import app.mauri.sukrara.share.owner.usecase.HandleInbox
import app.mauri.sukrara.share.owner.usecase.OpenNearbyPairing
import app.mauri.sukrara.share.owner.usecase.PublishReadings
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** The follower engine with a fake Bluetooth path beside the fake relays (the test plays the wearer's phone). */
class FollowerNearbyTest {
    private val ctx = OwnerFixtures.context()
    private val clock = FakeClock(Instant.ofEpochSecond(NOW))
    private val readings = InMemoryReadingsRepository()
    private val lookup = FakeRelayTransport()
    private val relay = FakeRelayTransport()
    private var lookupFirst = true
    private val nearbys = mutableListOf<FakeNearbyTransport>()

    private val wearer0 = CreateIdentity(ctx)(Profile("Ana", null), NOW).state
    private val created = CreateInvite(ctx)(wearer0, "Phone", NOW)

    private fun TestScope.engine() =
        FollowerEngine(
            crypto,
            FollowerStore(InMemorySecureStore(), crypto),
            { _, _ -> if (lookupFirst) lookup.also { lookupFirst = false } else relay },
            readings,
            InMemoryTreatmentsRepository(),
            clock,
            StandardTestDispatcher(testScheduler),
            nearbyTransports = { id -> FakeNearbyTransport(id).also { nearbys += it } },
        )

    private val received = mutableListOf<List<Reading>>()

    /** Joined, attached, and approved by the wearer (its events over Bluetooth only). */
    private fun TestScope.following(e: FollowerEngine): Pair<FakeNearbyTransport, Job> {
        lookup.stored += created.step.publish.first { it.kind == Wire.KIND_WRAP }
        var result: JoinResult? = null
        backgroundScope.launch { result = e.join(created.link.link, Profile("Bea", null)) }
        runCurrent()
        assertEquals(JoinResult.Joined, result)
        val job = backgroundScope.launch { e.readings.collect { received += it } }
        runCurrent()
        val nb = nearbys.single()
        assertTrue(nb.started)
        // before the first key the link knows the wearer but not the stream
        assertNull(nb.identity.sid)
        val request = relay.published.single { it.kind == Wire.KIND_WRAP }
        val handled = HandleInbox(ctx)(created.step.state, request, NOW)
        val step =
            ApproveRequest(ctx)(
                handled.step.state,
                handled.step.state.pending
                    .single()
                    .follower,
                NOW,
            )!!
        step.publish.forEach { nb.deliver(it) }
        runCurrent()
        wearer = step.state
        return nb to job
    }

    private var wearer = wearer0

    @Test fun theWearersEventsOverBluetoothAloneMakeAnActiveFollowWithReadings() =
        runTest {
            val e = engine()
            val (nb, _) = following(e)
            assertEquals(FollowStatus.Active, (e.state.value as FollowState.Following).status)
            // the first key: the link now proves who we are (HELLO again with the proof)
            assertNotNull(nb.identity.sid)
            assertEquals(wearer.sid, nb.identity.sid)

            val live =
                PublishReadings(ctx).onReadings(wearer, listOf(Reading(Instant.ofEpochSecond(NOW - 30), 123, 0.5, SourceId.Libre3)), NOW)
            live.filter { it.kind == Wire.KIND_LIVE }.forEach { nb.deliver(it) }
            runCurrent()
            assertEquals(listOf(123), received.flatten().map { it.mgdl })
            // one source for the follow stream: a copy over relays and Bluetooth is one reading
            assertEquals(SourceId.Relay, received.flatten().single().source)

            // the same event again over the relays: already seen
            live.filter { it.kind == Wire.KIND_LIVE }.forEach { relay.deliver(FollowerEngine.LIVE, it) }
            runCurrent()
            assertEquals(1, received.flatten().size)
        }

    @Test fun anotherWearersPhoneIsLeftAndStatusFollowsTheLink() =
        runTest {
            val e = engine()
            val (nb, job) = following(e)
            nb.info(BleMessages.Owner.Other, phone = "stranger")
            runCurrent()
            // that phone, for 10 minutes (B§6), even if the link has moved on to our wearer's phone since
            assertEquals(listOf("stranger" to 600L), nb.left)
            nb.info(BleMessages.Owner.Ours)
            runCurrent()
            assertEquals(NearbyStatus.Connected(1), e.nearbyStatus.value)

            // the service stops: Bluetooth stops with the relays
            job.cancel()
            runCurrent()
            assertTrue(nb.stopped)
            assertEquals(NearbyStatus.Stopped, e.nearbyStatus.value)
        }

    @Test fun findPhonesNearbyListsOffersAndJoiningSendsTheRequestOverBluetooth() =
        runTest {
            val e = engine()
            val wearerKey = wearer0.identity(crypto).public
            val (open, pairing) = openPairing()
            var search: NearbySearch? = null
            val finding = backgroundScope.launch { e.findNearby().collect { search = it } }
            runCurrent()
            val finder = nearbys.single()
            assertNull("the finder knows no identity: it writes nothing but HELLO", finder.identity.identity)
            finder.info(BleMessages.Owner.Unknown, phone = "ana", pairing = pairing)
            finder.info(BleMessages.Owner.Unknown, phone = "other")
            runCurrent()
            assertEquals(listOf(NearbyOffer("ana", pairing)), search!!.offers)
            // each phone is left so the scan moves on to the next, and looked at again soon, offering or not
            assertEquals(listOf("ana" to FollowerEngine.NEARBY_RECHECK_S, "other" to FollowerEngine.NEARBY_RECHECK_S), finder.left)
            // the next connection to the phone gets no answer (it stopped advertising): gone after that missed check
            finder.connecting("ana")
            pass(FollowerEngine.NEARBY_OFFER_DROP_S + 1)
            assertTrue(search!!.offers.isEmpty())
            // back in range, still offering
            finder.info(BleMessages.Owner.Unknown, phone = "ana", pairing = pairing)
            runCurrent()
            assertEquals(listOf(NearbyOffer("ana", pairing)), search!!.offers)
            // the offer closes on the phone: gone from the list at its next check
            finder.info(BleMessages.Owner.Unknown, phone = "ana")
            runCurrent()
            assertTrue(search!!.offers.isEmpty())
            finding.cancel()
            runCurrent()
            assertTrue(finder.stopped)

            assertEquals(JoinResult.Joined, e.joinNearby(NearbyOffer("ana", pairing), Profile("Bea", null)))
            assertTrue("no relay lookup", lookupFirst)
            backgroundScope.launch { e.readings.collect { received += it } }
            runCurrent()
            val nb = nearbys.last()
            assertEquals(wearerKey, nb.identity.identity)
            nb.sent.clear()
            // the phone's INFO names our wearer (`i` while pairing is open): the request goes over Bluetooth
            nb.info(BleMessages.Owner.Ours)
            runCurrent()
            val request = nb.sent.single { it.kind == Wire.KIND_WRAP }
            val handled = HandleInbox(ctx)(open.step.state, request, NOW)
            val pending =
                handled.step.state.pending
                    .single()
            assertEquals("Bea", pending.name)
            assertTrue(pending.nearby && !pending.allowFromNotification)
            // the wearer's phone opens its nonce to the claimer at once: both phones show the same 6 digits
            assertEquals(6, pending.code.length)
            handled.step.publish.forEach { nb.deliver(it) }
            runCurrent()
            assertEquals(PairingCheck(pending.code, confirmed = false), (e.state.value as FollowState.Following).pairing)
        }

    /**
     * #114: a re-check the scan-start budget holds back (Android's 5 starts per 30 s) never takes a listed offer away;
     * only a connection to its phone that gets no offer back does, or the phone not reached for a minute.
     */
    @Test fun aListedOfferWaitsForItsNextCheckWhileTheScanBudgetHoldsItBack() =
        runTest {
            val e = engine()
            val (_, pairing) = openPairing()
            val offer = NearbyOffer("ana", pairing)
            var search: NearbySearch? = null
            backgroundScope.launch { e.findNearby().collect { search = it } }
            runCurrent()
            val finder = nearbys.single()
            finder.info(BleMessages.Owner.Unknown, phone = "ana", pairing = pairing)
            runCurrent()
            // due again after 5 s, but no scan may start for up to the budget's 30 s window: still listed
            pass(FollowerEngine.NEARBY_RECHECK_S + 30)
            assertEquals(listOf(offer), search!!.offers)
            // the scan runs and connects: the phone's check starts now and it answers in time
            finder.connecting("ana")
            pass(FollowerEngine.NEARBY_OFFER_DROP_S)
            assertEquals(listOf(offer), search!!.offers)
            finder.info(BleMessages.Owner.Unknown, phone = "ana", pairing = pairing)
            runCurrent()
            assertEquals(listOf(offer), search!!.offers)
            // a connection to another phone isn't this offer's check
            finder.connecting("other")
            pass(FollowerEngine.NEARBY_OFFER_DROP_S + 1)
            assertEquals(listOf(offer), search!!.offers)
            // never reached again (gone out of range): it leaves the list after a minute
            pass(FollowerEngine.NEARBY_OFFER_LOST_S - FollowerEngine.NEARBY_OFFER_DROP_S)
            assertTrue(search!!.offers.isEmpty())
        }

    /** #114: a wearer who opens *Pair nearby* while the list shows is listed at the phone's next read, within ~10 s. */
    @Test fun aPhoneThatOpensPairNearbyWhileTheListShowsIsListedWithinTenSeconds() =
        runTest {
            val e = engine()
            val (_, pairing) = openPairing()
            var search: NearbySearch? = null
            backgroundScope.launch { e.findNearby().collect { search = it } }
            runCurrent()
            val finder = nearbys.single()
            // the wearer's phone before *Pair nearby* opens: read, then left for a moment
            finder.info(BleMessages.Owner.Unknown, phone = "ana")
            runCurrent()
            assertTrue(search!!.offers.isEmpty())
            val (phone, seconds) = finder.left.single()
            assertEquals("ana", phone)
            // pairing opens right after that read; the finder reads the phone again once it may, plus the connection
            pass(seconds)
            finder.connecting("ana")
            pass(CONNECTION_S)
            finder.info(BleMessages.Owner.Unknown, phone = "ana", pairing = pairing)
            runCurrent()
            assertEquals(listOf(NearbyOffer("ana", pairing)), search!!.offers)
            assertTrue("listed ${seconds + CONNECTION_S} s after pairing opened", seconds + CONNECTION_S <= 10)
        }

    /** What was sent is handled now, then [seconds] go by on the engine's clock and its timers. */
    private fun TestScope.pass(seconds: Long) {
        runCurrent()
        clock.advance(Duration.ofSeconds(seconds))
        advanceTimeBy(seconds * 1_000 + 100)
    }

    /**
     * #20 / #113: whoever answers (the real wearer or a phone posing as it), its key is not used until the person said
     * the wearer's phone shows the same code; then the follow starts. A different code ends the join with nothing sent.
     */
    @Test fun aNearbyKeyIsUsedOnlyAfterThePersonConfirmedTheCode() =
        runTest {
            val e = engine()
            val (open, pairing) = openPairing()
            assertEquals(JoinResult.Joined, e.joinNearby(NearbyOffer("ana", pairing), Profile("Bea", null)))
            backgroundScope.launch { e.readings.collect { received += it } }
            runCurrent()
            val nb = nearbys.last()
            nb.info(BleMessages.Owner.Ours)
            runCurrent()
            val handled = HandleInbox(ctx)(open.step.state, nb.sent.first { it.kind == Wire.KIND_WRAP }, NOW)
            val follower =
                handled.step.state.pending
                    .single()
                    .follower
            val approved = ApproveRequest(ctx)(handled.step.state, follower, NOW)!!
            // the key arrives before the code was compared (and before the code itself): held, not used
            approved.publish.forEach { nb.deliver(it) }
            runCurrent()
            var state = e.state.value as FollowState.Following
            assertEquals(FollowStatus.Requested, state.status)
            assertEquals(PairingCheck(null, confirmed = false), state.pairing)
            assertNull(nb.identity.sid)
            assertFalse("nothing to confirm without a code", e.confirmNearby(same = true))
            handled.step.publish.forEach { nb.deliver(it) }
            runCurrent()
            assertTrue(e.confirmNearby(same = true))
            runCurrent()
            state = e.state.value as FollowState.Following
            assertEquals(FollowStatus.Active, state.status)
            assertNull(state.pairing)
            assertEquals(approved.state.sid, nb.identity.sid)
        }

    /** A key held for the comparison survives a restart (the record keeps the whole pairing). */
    @Test fun theNearbyPairingIsKeptInTheRecord() {
        val f = crypto.generate()
        val key = Rumor.KeyDelivery("ab".repeat(32), 1, crypto.nsec(crypto.generate().secret), listOf("cd".repeat(32)), emptyList())
        val pair = NearbyPair("tok", "c".repeat(64), "a".repeat(64), "012345", confirmed = false, held = HeldKey(key, NOW))
        val record =
            FollowerRecord(
                stream = FollowerState(f, wearer0.identity(crypto).public, FollowStatus.Requested, pair = pair),
                token = "tok",
                code = "1234",
                me = null,
                wearer = null,
                joinedAt = NOW,
            )
        assertEquals(pair, FollowerCodec.decode(crypto, FollowerCodec.encode(record))!!.stream.pair)
    }

    @Test fun aDifferentCodeEndsTheJoin() =
        runTest {
            val e = engine()
            val (open, pairing) = openPairing()
            assertEquals(JoinResult.Joined, e.joinNearby(NearbyOffer("ana", pairing), null))
            backgroundScope.launch { e.readings.collect { received += it } }
            runCurrent()
            val nb = nearbys.last()
            nb.info(BleMessages.Owner.Ours)
            runCurrent()
            val handled = HandleInbox(ctx)(open.step.state, nb.sent.first { it.kind == Wire.KIND_WRAP }, NOW)
            handled.step.publish.forEach { nb.deliver(it) }
            runCurrent()
            val sent = nb.sent.size
            assertTrue(e.confirmNearby(same = false))
            runCurrent()
            assertEquals(FollowState.NotFollowing, e.state.value)
            assertEquals("nothing is sent", sent, nb.sent.size)
        }

    @Test fun anExpiredOfferIsNotJoined() =
        runTest {
            val e = engine()
            val pairing = openPairing().second.copy(expiry = NOW)
            assertEquals(JoinResult.Expired, e.joinNearby(NearbyOffer("ana", pairing), null))
        }

    /** *Pair nearby* opened on the wearer's phone, and the offer its INFO carries. */
    private fun openPairing(): Pair<OpenNearbyPairing.Opened, BleMessages.Pairing> {
        val open = OpenNearbyPairing(ctx)(wearer0, NOW)
        val nonce =
            open.step.state.invites
                .single { it.nearby }
                .pairNonce!!
        val pairing =
            BleMessages.Pairing(
                wearer0.identity(crypto).public,
                emptyList(),
                open.token,
                open.expiry,
                Profile("Ana", null),
                NearbyPairing.commit(nonce),
            )
        return open to pairing
    }

    @Test fun theAccessRequestGoesToTheRelaysAndOverBluetooth() =
        runTest {
            val e = engine()
            val (nb, _) = following(e)
            val request = relay.published.first { it.kind == Wire.KIND_WRAP }
            // the link itself refuses to write it until the phone proved it's the wearer's (NearbyLinkTest)
            assertTrue(request in nb.sent)
        }

    private companion object {
        /** A connection to a phone and its INFO (connect, MTU, services, notifications, HELLO): a couple of seconds. */
        const val CONNECTION_S = 3L
    }
}
