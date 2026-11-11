// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.share.owner

import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.core.model.storage.SecureStore
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.core.testing.InMemorySecureStore
import app.mauri.sukrara.core.testing.InMemoryTreatmentsRepository
import app.mauri.sukrara.protocol.event.Rumor
import app.mauri.sukrara.share.FakeHub
import app.mauri.sukrara.share.FakeRelayTransport
import app.mauri.sukrara.share.OwnerFixtures
import app.mauri.sukrara.share.OwnerFixtures.NOW
import app.mauri.sukrara.share.OwnerFixtures.crypto
import app.mauri.sukrara.share.owner.usecase.CreateIdentity
import app.mauri.sukrara.share.owner.usecase.CreateInvite
import app.mauri.sukrara.share.owner.usecase.HandleInbox
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * #167: *Allow* / *Deny* on the access-request notification in a process the tap just started (Android had stopped
 * the app). The answer goes through the sharing the role starts (AppGraph's `followRole`) and never starts or stops it:
 * the answer used to start the engine itself and stop it again when done, which could leave the wearer's sharing off
 * until the next restart (the role's start had found it running), so followers stopped getting readings.
 */
class NotificationAnswerTest {
    private val ctx = OwnerFixtures.context()
    private val relay = FakeRelayTransport()
    private val secure = GatedSecureStore()
    private val store = OwnerIdentityStore(secure)
    private val ana = crypto.generate()

    /** The wearer's saved sharing: an identity, and Ana's request (with the invite made for her) waiting. */
    private suspend fun saveAnasRequest() {
        val created = CreateInvite(ctx)(CreateIdentity(ctx)(null, NOW).state, "Ana", NOW)
        val handled = HandleInbox(ctx)(created.step.state, OwnerFixtures.request(ana, created.step.state, created.link.token, "Ana"), NOW)
        assertEquals(
            listOf(ana.public.hex),
            handled.step.state.pending
                .map { it.follower },
        )
        store.save(handled.step.state.copy(rosterPublishedAt = NOW, backupPublishedAt = NOW))
    }

    /** A new process: a new engine over the saved state. */
    private fun TestScope.engine() =
        OwnerEngine(
            ctx,
            store,
            relay,
            FakeHub(),
            { _, _ -> true },
            InMemoryTreatmentsRepository(),
            FakeClock(Instant.ofEpochSecond(NOW + 60)),
            StandardTestDispatcher(testScheduler),
        )

    private suspend fun saved() = (store.load() as OwnerIdentityStore.Load.Loaded).state

    private fun anaGotHerKey() = relay.published.mapNotNull { OwnerFixtures.open(ana, it) }.any { it is Rumor.KeyDelivery }

    private fun anaWasDeclined() = relay.published.mapNotNull { OwnerFixtures.open(ana, it) }.any { it == Rumor.Declined }

    /** Sharing runs and keeps running: the engine, its relays, long after the answer. */
    private fun TestScope.assertSharingStaysOn(e: OwnerEngine) {
        assertTrue("sharing runs", e.running)
        assertTrue("relays connected", relay.started)
        advanceTimeBy(WAIT_MS * 3)
        runCurrent()
        assertTrue("sharing still runs", e.running)
        assertTrue("relays still connected", relay.started)
    }

    @Test fun anAllowAtTheRolesStartLeavesSharingOnAndGivesAccess() =
        runTest {
            saveAnasRequest()
            val e = engine()
            // the tap's broadcast and the graph's start of the wearer's sharing, in the same new process
            val answer = async { e.answerFromNotification(ana.public.hex, allow = true, WAIT_MS) }
            backgroundScope.launch { e.followRole(flowOf(Role.Wearer), backgroundScope) }
            runCurrent()
            assertTrue("allowed", answer.await())
            assertEquals(listOf(ana.public.hex), saved().activeFollowers().map { it.key })
            assertTrue(saved().pending.isEmpty())
            assertTrue("her key went out", anaGotHerKey())
            assertSharingStaysOn(e)
            e.stop()
        }

    @Test fun anAllowWhileTheRolesStartLoadsTheIdentityWaitsForIt() =
        runTest {
            saveAnasRequest()
            val e = engine()
            // the Keystore is slow: the role's start holds the engine while it reads the identity
            secure.gate = CompletableDeferred()
            backgroundScope.launch { e.followRole(flowOf(Role.Wearer), backgroundScope) }
            runCurrent()
            val answer = async { e.answerFromNotification(ana.public.hex, allow = true, WAIT_MS) }
            runCurrent()
            secure.gate!!.complete(Unit)
            runCurrent()
            assertTrue("allowed", answer.await())
            assertEquals(listOf(ana.public.hex), saved().activeFollowers().map { it.key })
            assertTrue("her key went out", anaGotHerKey())
            assertSharingStaysOn(e)
            e.stop()
        }

    @Test fun anAllowBeforeTheRoleIsReadWaitsForTheRolesStart() =
        runTest {
            saveAnasRequest()
            val e = engine()
            val answer = async { e.answerFromNotification(ana.public.hex, allow = true, WAIT_MS) }
            runCurrent()
            assertFalse("the answer never starts sharing itself", e.running)
            // the role store answers a moment later
            advanceTimeBy(WAIT_MS / 2)
            backgroundScope.launch { e.followRole(flowOf(Role.Wearer), backgroundScope) }
            runCurrent()
            assertTrue("allowed", answer.await())
            assertTrue("her key went out", anaGotHerKey())
            assertSharingStaysOn(e)
            e.stop()
        }

    @Test fun aDenyAtTheRolesStartLeavesSharingOnAndDeclines() =
        runTest {
            saveAnasRequest()
            val e = engine()
            val answer = async { e.answerFromNotification(ana.public.hex, allow = false, WAIT_MS) }
            backgroundScope.launch { e.followRole(flowOf(Role.Wearer), backgroundScope) }
            runCurrent()
            assertTrue("denied", answer.await())
            assertTrue(saved().pending.isEmpty())
            assertTrue(saved().activeFollowers().isEmpty())
            assertTrue("she was told", anaWasDeclined())
            assertFalse("no key for her", anaGotHerKey())
            assertSharingStaysOn(e)
            e.stop()
        }

    @Test fun onAPhoneThatIsntTheWearersTheAnswerChangesNothing() =
        runTest {
            saveAnasRequest()
            val e = engine()
            val answer = async { e.answerFromNotification(ana.public.hex, allow = true, WAIT_MS) }
            backgroundScope.launch { e.followRole(flowOf(Role.Follower), backgroundScope) }
            advanceTimeBy(WAIT_MS + 1)
            runCurrent()
            assertFalse("not allowed: sharing doesn't run here", answer.await())
            assertFalse(e.running)
            assertFalse(relay.started)
            assertEquals(listOf(ana.public.hex), saved().pending.map { it.follower })
            assertTrue(saved().activeFollowers().isEmpty())
            assertTrue(relay.published.isEmpty())
        }

    /** A secure store whose reads wait for [gate] (a slow Keystore), when one is set. */
    private class GatedSecureStore : SecureStore {
        private val inner = InMemorySecureStore()
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun read(name: String): ByteArray? {
            gate?.await()
            return inner.read(name)
        }

        override suspend fun write(
            name: String,
            bytes: ByteArray,
        ) = inner.write(name, bytes)

        override suspend fun delete(name: String) = inner.delete(name)
    }

    private companion object {
        const val WAIT_MS = 4_000L
    }
}
