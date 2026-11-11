// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing

import app.mauri.sukrara.feature.sharing.share.InviteProgress
import app.mauri.sukrara.feature.sharing.share.InviteUi
import app.mauri.sukrara.feature.sharing.share.InviteWatch
import app.mauri.sukrara.feature.sharing.share.SharingViewModel
import app.mauri.sukrara.share.owner.FollowerRecord
import app.mauri.sukrara.share.owner.InviteRecord
import app.mauri.sukrara.share.owner.OwnerShareState
import app.mauri.sukrara.share.owner.PendingRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** #174: the share screen follows its invite by itself: asked, allowed, denied, expired. */
@OptIn(ExperimentalCoroutinesApi::class)
class InviteProgressTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val token = "tok"
    private val expiry = 1_000L
    private val request = PendingRequest("bea", "Bea", token, "4821", at = 100, label = "Bea's Mac")

    private fun sharing(
        invite: InviteRecord? = InviteRecord(token, expiry, "Bea's Mac", sentAt = 50),
        pending: List<PendingRequest> = emptyList(),
        followers: List<FollowerRecord> = emptyList(),
    ) = OwnerShareState.Sharing(1, followers, pending, emptyMap(), 0, 0, invites = listOfNotNull(invite))

    @Test
    fun `open, then asked, then following once allowed`() {
        val watch = InviteWatch(token, expiry)
        assertEquals(InviteProgress.Open, watch.progress(sharing(), 60))
        val claimed = InviteRecord(token, expiry, "Bea's Mac", sentAt = 50, claimedBy = "bea")
        assertEquals(InviteProgress.Asked(request), watch.progress(sharing(claimed, pending = listOf(request)), 100))
        val bea = FollowerRecord("bea", "Bea", since = 110)
        assertEquals(InviteProgress.Following(bea), watch.progress(sharing(claimed.copy(done = true), followers = listOf(bea)), 110))
    }

    @Test
    fun `denied keeps the name of who asked`() {
        val watch = InviteWatch(token, expiry)
        val claimed = InviteRecord(token, expiry, sentAt = 50, claimedBy = "bea")
        watch.progress(sharing(claimed, pending = listOf(request)), 100)
        assertEquals(InviteProgress.Denied("Bea"), watch.progress(sharing(claimed.copy(done = true)), 110))
        // a denial this screen never saw asked still says it was denied
        assertEquals(InviteProgress.Denied(null), InviteWatch(token, expiry).progress(sharing(claimed.copy(done = true)), 110))
    }

    @Test
    fun `expired unused, or an Allow that came after the expiry`() {
        assertEquals(InviteProgress.Expired, InviteWatch(token, expiry).progress(sharing(), expiry))
        // the record pruned with nobody asking
        assertEquals(InviteProgress.Expired, InviteWatch(token, expiry).progress(sharing(invite = null), expiry + 1))
        // claimed, then Allow refused past the expiry: the request is gone and nobody follows
        val watch = InviteWatch(token, expiry)
        val claimed = InviteRecord(token, expiry, sentAt = 50, claimedBy = "bea")
        watch.progress(sharing(claimed, pending = listOf(request)), 900)
        assertEquals(InviteProgress.Expired, watch.progress(sharing(claimed), expiry + 5))
    }

    @Test
    fun `another invite's request or a paused engine changes nothing`() {
        val watch = InviteWatch(token, expiry)
        val other = request.copy(follower = "eve", token = "other")
        assertEquals(InviteProgress.Open, watch.progress(sharing(pending = listOf(other)), 100))
        assertEquals(InviteProgress.Open, watch.progress(OwnerShareState.Loading, 100))
    }

    @Test
    fun `the view model follows the invite shown as the engine changes`() =
        runTest {
            val fake = FakeOwnerSharing(sharing(), invite = Invite("https://sukrara.mauri.app/join#x", "Bea's Mac", expiry, token))
            val vm = SharingViewModel(fake, now = { 100 })
            val seen = mutableListOf<InviteProgress>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.progress.collect { seen += it } }
            assertEquals(InviteProgress.Open, vm.progress.value)
            vm.createInvite("Bea's Mac")
            assertEquals(InviteUi.Ready(fake.invite!!), vm.invite.value)

            val claimed = InviteRecord(token, expiry, "Bea's Mac", sentAt = 50, claimedBy = "bea")
            fake.state.value = sharing(claimed, pending = listOf(request))
            assertEquals(InviteProgress.Asked(request), vm.progress.value)
            val bea = FollowerRecord("bea", "Bea", since = 110)
            fake.state.value = sharing(claimed.copy(done = true), followers = listOf(bea))
            assertEquals(InviteProgress.Following(bea), vm.progress.value)
            assertEquals(listOf(InviteProgress.Open, InviteProgress.Asked(request), InviteProgress.Following(bea)), seen)

            // Invite someone else: nothing shown, nothing followed
            vm.newInvite()
            assertEquals(InviteProgress.Open, vm.progress.value)
        }
}
