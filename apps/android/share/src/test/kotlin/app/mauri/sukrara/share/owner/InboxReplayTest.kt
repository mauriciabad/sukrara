// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.share.owner

import app.mauri.sukrara.core.model.storage.SecureStore
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.core.testing.InMemorySecureStore
import app.mauri.sukrara.core.testing.InMemoryTreatmentsRepository
import app.mauri.sukrara.protocol.KeyPair
import app.mauri.sukrara.protocol.SignedEvent
import app.mauri.sukrara.protocol.event.Profile
import app.mauri.sukrara.protocol.event.Rumor
import app.mauri.sukrara.share.FakeHub
import app.mauri.sukrara.share.FakeRelayTransport
import app.mauri.sukrara.share.OwnerFixtures
import app.mauri.sukrara.share.OwnerFixtures.NOW
import app.mauri.sukrara.share.OwnerFixtures.crypto
import app.mauri.sukrara.share.owner.usecase.CreateIdentity
import app.mauri.sukrara.share.owner.usecase.CreateInvite
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant

/**
 * #78: the wearer's inbox after a restart. The relays keep the gift wraps sent to the identity (requests, profile
 * updates, rekeys, history requests: up to 7 days) and the inbox subscription has no `since`, so every start gets them
 * all again; the seen-wraps ledger keeps an old one from being handled twice. The ledger used to come back empty at
 * every start (pruned with a milliseconds cutoff over epoch seconds): Ana got her key again, Cleo (removed) her revoked
 * notice again, and Ana's older rename could replace her newer one.
 */
class InboxReplayTest {
    private val ctx = OwnerFixtures.context()
    private val relay = FakeRelayTransport()
    private val secure = FailingSecureStore()
    private val store = OwnerIdentityStore(secure)
    private val ana = crypto.generate()
    private val bob = crypto.generate()
    private val cleo = crypto.generate()

    /** The Room ledger's behaviour, kept across restarts unless a test starts with a new one. */
    private class Ledger : SeenWraps {
        val ids = mutableMapOf<String, Long>()

        override suspend fun seen(id: String) = id in ids

        override suspend fun mark(
            id: String,
            at: Long,
        ) {
            ids.putIfAbsent(id, at)
        }
    }

    /** A Keystore that refuses writes while [failing] (the handling of a wrap fails). */
    private class FailingSecureStore : SecureStore {
        private val inner = InMemorySecureStore()
        var failing = false

        override suspend fun read(name: String) = inner.read(name)

        override suspend fun write(
            name: String,
            bytes: ByteArray,
        ) = if (failing) throw IOException("keystore busy") else inner.write(name, bytes)

        override suspend fun delete(name: String) = inner.delete(name)
    }

    private fun TestScope.engine(
        ledger: SeenWraps,
        at: Long,
    ) = OwnerEngine(
        ctx,
        store,
        relay,
        FakeHub(),
        ledger,
        InMemoryTreatmentsRepository(),
        FakeClock(Instant.ofEpochSecond(at)),
        StandardTestDispatcher(testScheduler),
    )

    private suspend fun saved() = (store.load() as OwnerIdentityStore.Load.Loaded).state

    private fun sentTo(follower: KeyPair) = relay.published.mapNotNull { OwnerFixtures.open(follower, it) }

    private fun OwnerState.nameOf(follower: KeyPair) = followers.single { it.key == follower.public.hex }.name

    /** An identity with an invite for Ana, Bob and Cleo, saved; returns it and the invites' tokens. */
    private suspend fun invites(): Pair<OwnerState, List<String>> {
        var s = CreateIdentity(ctx)(null, NOW).state
        val tokens =
            listOf("Ana", "Bob", "Cleo").map { label ->
                CreateInvite(ctx)(s, label, NOW).also { s = it.step.state }.link.token
            }
        store.save(s.copy(rosterPublishedAt = NOW, backupPublishedAt = NOW))
        return s to tokens
    }

    /**
     * The first run: Ana asks and is allowed, Bob asks and is denied, Cleo asks, is allowed, then removed and asks for
     * a key again; Ana renames herself twice. Every answer reached a relay. Returns every wrap the relays keep, in the
     * order they come back (newest first by their randomised created_at: any order).
     */
    private suspend fun TestScope.firstRun(ledger: SeenWraps): List<SignedEvent> {
        val (s, tokens) = invites()
        val e = engine(ledger, NOW + 60)
        e.start(backgroundScope)
        runCurrent()
        val requests =
            listOf(ana, bob, cleo).zip(tokens).map { (f, token) -> OwnerFixtures.request(f, s, token, "x", NOW + 10) }
        requests.forEach { relay.deliver("inbox", it) }
        runCurrent()
        assertEquals(ApproveResult.Allowed, e.approve(ana.public.hex))
        e.deny(bob.public.hex)
        assertEquals(ApproveResult.Allowed, e.approve(cleo.public.hex))
        e.remove(cleo.public.hex)
        val older = OwnerFixtures.toOwner(ana, s, Rumor.ProfileUpdate(Profile("Ana old", null)), NOW + 20)
        val newer = OwnerFixtures.toOwner(ana, s, Rumor.ProfileUpdate(Profile("Ana new", null)), NOW + 30)
        val rekey = OwnerFixtures.toOwner(cleo, s, Rumor.Rekey("x"), NOW + 40)
        listOf(older, newer, rekey).forEach { relay.deliver("inbox", it) }
        runCurrent()
        // every answer reached a relay: nothing waits in pendingControl for the next start
        relay.published.forEach { relay.ok(it.id) }
        runCurrent()
        e.stop()
        assertEquals("Ana new", saved().nameOf(ana))
        return requests + listOf(rekey, newer, older)
    }

    /** The app starts again (a new engine over the saved state) and the relays send [wraps] again. */
    private suspend fun TestScope.restart(
        ledger: SeenWraps,
        wraps: List<SignedEvent>,
    ): Pair<OwnerState, OwnerState> {
        val before = saved()
        relay.published.clear()
        val e = engine(ledger, NOW + 3_600)
        e.start(backgroundScope)
        runCurrent()
        wraps.forEach { relay.deliver("inbox", it) }
        runCurrent()
        e.stop()
        return before to saved()
    }

    @Test fun aRestartHandlesNoWrapAgain() =
        runTest {
            val ledger = Ledger()
            val wraps = firstRun(ledger)
            val (before, after) = restart(ledger, wraps)
            assertEquals("no key again", emptyList<Rumor>(), sentTo(ana))
            assertEquals(emptyList<Rumor>(), sentTo(bob))
            assertEquals("no revoked notice again", emptyList<Rumor>(), sentTo(cleo))
            assertEquals(before.followers, after.followers)
            assertEquals(before.pending, after.pending)
            assertEquals(before.invites, after.invites)
            assertEquals(before.pendingControl, after.pendingControl)
        }

    /**
     * A wrap handled a second time anyway (a relay that ignores the expiration and outlives the ledger's 14 days, a
     * ledger lost with the app's data) changes nothing: the answer goes out again (Ana's key, Cleo's revoked notice:
     * what a follower asking again gets), Bob stays denied, and an older rename never replaces a newer one.
     */
    @Test fun anOldWrapHandledAgainChangesNothing() =
        runTest {
            val wraps = firstRun(Ledger())
            val (before, after) = restart(Ledger(), wraps)
            assertEquals(listOf(true), sentTo(ana).map { it is Rumor.KeyDelivery })
            assertEquals(emptyList<Rumor>(), sentTo(bob))
            assertEquals(listOf(Rumor.Revoked), sentTo(cleo))
            assertEquals(before.followers, after.followers)
            assertEquals(before.pending, after.pending)
            assertEquals(before.invites, after.invites)
            assertEquals("Ana new", after.nameOf(ana))
        }

    /** Two renames that come in together (the wearer's phone was offline) leave the newer name, whatever their order. */
    @Test fun anOlderRenameArrivingLaterIsIgnored() =
        runTest {
            val ledger = Ledger()
            val (s, tokens) = invites()
            val e = engine(ledger, NOW + 60)
            e.start(backgroundScope)
            runCurrent()
            relay.deliver("inbox", OwnerFixtures.request(ana, s, tokens[0], "Ana", NOW + 10))
            runCurrent()
            assertEquals(ApproveResult.Allowed, e.approve(ana.public.hex))
            relay.deliver("inbox", OwnerFixtures.toOwner(ana, s, Rumor.ProfileUpdate(Profile("Ana new", null)), NOW + 30))
            relay.deliver("inbox", OwnerFixtures.toOwner(ana, s, Rumor.ProfileUpdate(Profile("Ana old", null)), NOW + 20))
            runCurrent()
            assertEquals("Ana new", saved().nameOf(ana))
            e.stop()
        }

    /**
     * A wrap is marked seen only once its handling is saved: Ana's request, whose handling failed (the Keystore refused
     * the write, the process went down), is handled when the relays send it again after the restart.
     */
    @Test fun aWrapWhoseHandlingFailedIsHandledWhenItComesAgain() =
        runTest {
            val ledger = Ledger()
            val (s, tokens) = invites()
            val request = OwnerFixtures.request(ana, s, tokens[0], "Ana", NOW + 10)
            // the failure ends the run (as an uncaught exception ends the process): its scope swallows it here
            val failed = CoroutineScope(SupervisorJob() + CoroutineExceptionHandler { _, _ -> })
            val e = engine(ledger, NOW + 60)
            e.start(failed)
            runCurrent()
            secure.failing = true
            relay.deliver("inbox", request)
            runCurrent()
            secure.failing = false
            e.stop()
            failed.cancel()
            assertTrue("nothing saved", saved().pending.isEmpty())

            restart(ledger, listOf(request))
            assertEquals(listOf(ana.public.hex), saved().pending.map { it.follower })
        }
}
