// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.share

import app.mauri.sukrara.core.model.AlertUiState
import app.mauri.sukrara.core.model.GlucoseHub
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.LinkState
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.SourceStatus
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.core.testing.InMemoryReadingsRepository
import app.mauri.sukrara.core.testing.InMemorySecureStore
import app.mauri.sukrara.core.testing.InMemoryTreatmentsRepository
import app.mauri.sukrara.protocol.event.Profile
import app.mauri.sukrara.protocol.follow.FollowStatus
import app.mauri.sukrara.protocol.invite.InviteLink
import app.mauri.sukrara.share.OwnerFixtures.NOW
import app.mauri.sukrara.share.OwnerFixtures.crypto
import app.mauri.sukrara.share.follower.FollowState
import app.mauri.sukrara.share.follower.FollowerEngine
import app.mauri.sukrara.share.follower.FollowerNotice
import app.mauri.sukrara.share.follower.FollowerStore
import app.mauri.sukrara.share.follower.InviteLookup
import app.mauri.sukrara.share.follower.JoinResult
import app.mauri.sukrara.share.owner.ApproveResult
import app.mauri.sukrara.share.owner.OwnerEngine
import app.mauri.sukrara.share.owner.OwnerIdentityStore
import app.mauri.sukrara.share.owner.OwnerShareState
import app.mauri.sukrara.share.owner.PendingRequest
import app.mauri.sukrara.share.owner.usecase.CreateIdentity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * #174: both sides of an invite move on by themselves, over one in-memory relay (never a real one). The wearer's engine
 * and a follower's engine run as the app runs them: the follower's request reaches the wearer's open subscription and
 * shows as waiting; Allow reaches the follower, which follows at once, while the wearer lists it; Deny and a second
 * device on the same link arrive the same way. The words the screens show for each state are pinned by the UI tests.
 */
class PairingLiveTest {
    private val ctx = OwnerFixtures.context()
    private val clock = FakeClock(Instant.ofEpochSecond(NOW))
    private val bus = RelayBus()
    private val ownerStore = OwnerIdentityStore(InMemorySecureStore())
    private val requests = mutableListOf<PendingRequest>()

    private suspend fun TestScope.wearer(): OwnerEngine {
        ownerStore.save(
            CreateIdentity(ctx)(Profile("Ana", null), NOW).state.copy(rosterPublishedAt = NOW, backupPublishedAt = NOW),
        )
        return OwnerEngine(
            ctx,
            ownerStore,
            bus.link(),
            NoHub,
            { _, _ -> true },
            InMemoryTreatmentsRepository(),
            clock,
            StandardTestDispatcher(testScheduler),
            onRequest = { requests += it },
        ).also { it.start(backgroundScope) }
    }

    /** A follower device: its own key store and data, connected to the same relay while monitoring runs. */
    private inner class Follower(scope: TestScope) {
        val notices = mutableListOf<FollowerNotice>()
        val engine =
            FollowerEngine(
                crypto,
                FollowerStore(InMemorySecureStore(), crypto),
                { _, _ -> bus.link() },
                InMemoryReadingsRepository(),
                InMemoryTreatmentsRepository(),
                clock,
                StandardTestDispatcher(scope.testScheduler),
                onNotice = { n, _ -> notices += n },
            )

        val following: FollowState.Following get() = engine.state.value as FollowState.Following

        /** Opens the link, sees who invites, taps Follow as [name]; monitoring then connects and sends the request. */
        fun TestScope.follow(link: String, name: String) {
            var found: InviteLookup? = null
            backgroundScope.launch { found = engine.lookUp(link) }
            runCurrent()
            val invite = (found as InviteLookup.Found).invite
            assertEquals("Ana", invite.profile?.name)
            var joined: JoinResult? = null
            backgroundScope.launch { joined = engine.join(invite, Profile(name, null)) }
            runCurrent()
            assertEquals(JoinResult.Joined, joined)
            backgroundScope.launch { engine.readings.collect {} }
            runCurrent()
        }
    }

    private fun OwnerEngine.sharing() = state.value as OwnerShareState.Sharing

    private fun TestScope.invite(w: OwnerEngine, label: String): String {
        var link: String? = null
        backgroundScope.launch { link = w.createInvite(label) }
        runCurrent()
        return link!!
    }

    private fun TestScope.approve(w: OwnerEngine, follower: String, code: String? = null): ApproveResult {
        var result: ApproveResult? = null
        backgroundScope.launch { result = w.approve(follower, code) }
        runCurrent()
        return result!!
    }

    @Test fun allowReachesTheFollowerAtOnceAndTheWearerListsIt() =
        runTest {
            val w = wearer()
            runCurrent()
            val link = invite(w, "Bea's MacBook")
            val token = InviteLink.parse(link)!!.token
            val bea = Follower(this)
            with(bea) { follow(link, "Bea") }

            // the request arrived on the wearer's open subscription: waiting, with the label, no code to type
            val request = w.sharing().pending.single()
            assertEquals("Bea", request.name)
            assertEquals("Bea's MacBook", request.label)
            assertEquals(0, request.others)
            assertFalse(request.needsTypedCode)
            assertTrue(request.allowFromNotification)
            assertEquals(listOf(request), requests)
            assertEquals(
                request.follower,
                w
                    .sharing()
                    .invites
                    .single { it.token == token }
                    .claimedBy,
            )
            assertEquals(FollowStatus.Requested, bea.following.status)
            // the follower's match code is the one the wearer would type for a leaked link
            assertEquals(request.code, bea.following.code)

            assertEquals(ApproveResult.Allowed, approve(w, request.follower))
            // no reconnect, no restart: the key delivery reached the follower's inbox and it follows now
            assertEquals(FollowStatus.Active, bea.following.status)
            assertTrue(w.sharing().pending.isEmpty())
            assertEquals(
                listOf("Bea"),
                w
                    .sharing()
                    .followers
                    .filter { it.active }
                    .map { it.name },
            )
            val used = w.sharing().invites.single { it.token == token }
            assertTrue(used.done)
            assertEquals(request.follower, used.claimedBy)
            w.stop()
        }

    @Test fun denyReachesTheFollowerAtOnce() =
        runTest {
            val w = wearer()
            runCurrent()
            val link = invite(w, "Bea")
            val bea = Follower(this)
            with(bea) { follow(link, "Bea") }
            val request = w.sharing().pending.single()

            backgroundScope.launch { w.deny(request.follower) }
            runCurrent()
            assertEquals(FollowStatus.Declined, bea.following.status)
            assertEquals(listOf(FollowerNotice.Declined), bea.notices)
            assertTrue(w.sharing().pending.isEmpty())
            assertTrue(w.sharing().followers.isEmpty())
            assertTrue(
                w
                    .sharing()
                    .invites
                    .single()
                    .done,
            )
            w.stop()
        }

    /** A leaked link: the second device hears at once that the invite is used; the wearer types the first one's code. */
    @Test fun aSecondDeviceOnTheSameLinkIsToldAndTheWearerTypesTheCode() =
        runTest {
            val w = wearer()
            runCurrent()
            val link = invite(w, "Bea")
            val bea = Follower(this)
            with(bea) { follow(link, "Bea") }
            val eve = Follower(this)
            with(eve) { follow(link, "Bea") }

            assertEquals(FollowStatus.InviteUsed, eve.following.status)
            assertEquals(listOf(FollowerNotice.InviteUsed), eve.notices)
            val request = w.sharing().pending.single()
            assertEquals(1, request.others)
            assertTrue(request.needsTypedCode)
            assertFalse(request.allowFromNotification)
            assertEquals(ApproveResult.NeedsCode, approve(w, request.follower))
            assertEquals(ApproveResult.NeedsCode, approve(w, request.follower, eve.following.code))
            assertEquals(FollowStatus.Requested, bea.following.status)

            assertEquals(ApproveResult.Allowed, approve(w, request.follower, bea.following.code))
            assertEquals(FollowStatus.Active, bea.following.status)
            assertEquals(FollowStatus.InviteUsed, eve.following.status)
            w.stop()
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
