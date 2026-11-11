// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.core.model.AlertReadiness
import app.mauri.sukrara.core.model.AlertReadinessSource
import app.mauri.sukrara.core.model.AlertRequirement
import app.mauri.sukrara.core.model.AlertUiState
import app.mauri.sukrara.core.model.GlucoseHub
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.LinkState
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.SourceStatus
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.core.testing.InMemorySettingsRepository
import app.mauri.sukrara.core.testing.InMemoryStateStore
import app.mauri.sukrara.feature.sharing.setup.AlertSettingsAccess
import app.mauri.sukrara.feature.sharing.setup.JoinUi
import app.mauri.sukrara.feature.sharing.setup.RestoreUi
import app.mauri.sukrara.feature.sharing.setup.RoleChange
import app.mauri.sukrara.feature.sharing.setup.SetupCard
import app.mauri.sukrara.feature.sharing.setup.SetupRecord
import app.mauri.sukrara.feature.sharing.setup.SetupStep
import app.mauri.sukrara.feature.sharing.setup.SetupViewModel
import app.mauri.sukrara.protocol.PublicKeyHex
import app.mauri.sukrara.protocol.event.Profile
import app.mauri.sukrara.protocol.follow.FollowStatus
import app.mauri.sukrara.protocol.invite.Invite
import app.mauri.sukrara.share.follower.FollowState
import app.mauri.sukrara.share.follower.InviteLookup
import app.mauri.sukrara.share.follower.JoinResult
import app.mauri.sukrara.share.owner.OwnerShareState
import app.mauri.sukrara.share.owner.usecase.RestoreIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SetupViewModelTest {
    private val clock = FakeClock()
    private val hub = FakeHub()
    private val events = mutableListOf<String>()
    private val roles = FakeRoles(events)
    private val sharing = FakeOwnerSharing()
    private val alerts = FakeAlerts()
    private val record = InMemoryStateStore(SetupRecord())

    /** What is stored now (the in-memory store keeps a StateFlow): for checks inside a non-suspending callback. */
    private fun recorded() = (record.data as kotlinx.coroutines.flow.StateFlow<SetupRecord>).value

    private val settings = InMemorySettingsRepository()
    private val readiness = FakeReadiness()

    private val module by lazy {
        SetupModule(
            ApplicationProvider.getApplicationContext<Application>(),
            record,
            roles,
            hub,
            sharing,
            alerts,
            settings,
            resetAlerts = { events += "reset" },
            startMonitoring = { events += "start" },
            readiness = readiness,
            clock = clock,
            follower = follower,
        )
    }

    private fun vm(
        startAt: SetupStep? = null,
        saved: SavedStateHandle = SavedStateHandle(),
    ) = SetupViewModel(module, startAt, saved)

    /** The same screen after a process death: a new view model over what the old one saved. */
    private fun SavedStateHandle.restored() = SavedStateHandle(keys().associateWith { get<Any?>(it) })

    private var joinedAs: Profile? = null
    private val lookups = mutableListOf<String>()

    private val follower =
        object : FollowerSharing {
            override val state = MutableStateFlow<FollowState>(FollowState.NotFollowing)

            override suspend fun lookUp(link: String): InviteLookup {
                lookups += link
                return if (link == LINK) InviteLookup.Found(INVITE) else InviteLookup.Failed(JoinResult.InvalidLink)
            }

            override suspend fun join(
                invite: Invite,
                me: Profile?,
            ): JoinResult {
                joinedAs = me
                state.value = FollowState.Following(FollowStatus.Requested, Profile("Ana", null), "0420", false, null, emptyMap())
                return JoinResult.Joined
            }

            override suspend fun stopFollowing() = true
        }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `choosing the role resets the alert runtime before the role is stored`() =
        runTest {
            val vm = vm()
            vm.ui.launchIn(backgroundScope)
            vm.continueWelcome()
            assertEquals(SetupStep.Role, vm.ui.first { it != null }!!.step)
            vm.chooseRole(Role.Wearer)
            assertEquals(listOf("reset", "role:Wearer", "start"), events)
            assertEquals(SetupStep.Permissions, vm.ui.first { it?.step == SetupStep.Permissions }!!.step)
        }

    @Test
    fun `with nothing to delete, Continue shows the plain role confirmation`() =
        runTest {
            val vm = vm()
            vm.ui.launchIn(backgroundScope)
            vm.continueWelcome()
            vm.ui.first { it?.step == SetupStep.Role }
            vm.reviewRole(Role.Wearer)
            assertEquals(RoleChange(Role.Wearer, null), vm.ui.first { it?.roleChange != null }!!.roleChange)
            assertTrue(events.isEmpty())
            vm.chooseRole(Role.Wearer)
            assertEquals(listOf("reset", "role:Wearer", "start"), events)
            assertEquals(null, vm.ui.first { it?.step == SetupStep.Permissions }!!.roleChange)
        }

    @Test
    fun `a role change that deletes history asks first, and Cancel changes nothing`() =
        runTest {
            val vm = vm()
            vm.ui.launchIn(backgroundScope)
            vm.ui.first { it != null }
            vm.chooseRole(Role.Follower)
            vm.ui.first { it?.step == SetupStep.Join }
            events.clear()
            // the join step's Back to the role, with the followed person's history on the phone by then
            val loss = RoleLoss(1204, 12, Instant.parse("2026-10-03T08:00:00Z"), Instant.parse("2026-11-10T07:00:00Z"))
            roles.loss = loss
            vm.back {}
            vm.ui.first { it?.step == SetupStep.Role }

            vm.reviewRole(Role.Wearer)
            assertEquals(RoleChange(Role.Wearer, loss), vm.ui.first { it?.roleChange != null }!!.roleChange)
            vm.cancelRole()
            val after = vm.ui.first { it?.roleChange == null }!!
            assertEquals(SetupStep.Role, after.step)
            assertEquals(Role.Follower, roles.role.value)
            assertTrue(events.isEmpty())

            // Delete and continue
            vm.reviewRole(Role.Wearer)
            vm.ui.first { it?.roleChange != null }
            vm.chooseRole(Role.Wearer)
            assertEquals(listOf("reset", "role:Wearer", "start"), events)
            assertEquals(null, vm.ui.first { it?.step != SetupStep.Role }!!.roleChange)
        }

    @Test
    fun `the checklist follows the live readiness, not a copy taken on resume`() =
        runTest {
            roles.role.value = Role.Wearer
            val vm = vm()
            vm.ui.launchIn(backgroundScope)
            assertTrue(
                vm.ui
                    .first { it != null }!!
                    .readiness.ready,
            )
            // the monitor answers later (its own dispatcher): setup must show it without another resume
            readiness.readiness.value = AlertReadiness(setOf(AlertRequirement.Bluetooth))
            assertEquals(
                setOf(AlertRequirement.Bluetooth),
                vm.ui
                    .first { it?.readiness?.ready == false }!!
                    .readiness.missing,
            )
        }

    @Test
    fun `a follower joins, sees the waiting code, and moves on with Continue`() =
        runTest {
            val vm = vm()
            vm.ui.launchIn(backgroundScope)
            vm.ui.first { it != null }
            vm.chooseRole(Role.Follower)
            assertEquals(listOf("reset", "role:Follower", "start"), events)
            assertEquals(SetupStep.Join, vm.ui.first { it?.step == SetupStep.Join }!!.step)
            vm.lookUp("https://sukrara.mauri.app/join#bad")
            assertEquals(JoinUi.Failed(JoinResult.InvalidLink), vm.ui.first { it?.join is JoinUi.Failed }!!.join)
            vm.lookUp(LINK)
            // who is inviting, before anything is sent
            assertEquals(JoinUi.Confirm(INVITE), vm.ui.first { it?.join is JoinUi.Confirm }!!.join)
            assertNull(joinedAs)
            assertEquals(FollowState.NotFollowing, follower.state.value)
            vm.join("  Bea ")
            val waiting = vm.ui.first { (it?.follow as? FollowState.Following)?.status == FollowStatus.Requested }!!
            assertEquals(SetupStep.Join, waiting.step)
            assertEquals("Bea", joinedAs?.name)
            vm.completeJoin()
            assertEquals(SetupStep.Permissions, vm.ui.first { it?.step == SetupStep.Permissions }!!.step)
        }

    @Test
    fun `an opened invite is looked up once by itself and Cancel or Back sends nothing`() =
        runTest {
            val vm = vm()
            vm.ui.launchIn(backgroundScope)
            vm.ui.first { it != null }
            vm.chooseRole(Role.Follower)
            assertEquals(SetupStep.Join, vm.ui.first { it?.step == SetupStep.Join }!!.step)
            vm.lookUp(LINK, opened = true)
            // a rotation draws the step again: the same opened link isn't looked up twice
            vm.lookUp(LINK, opened = true)
            assertEquals(listOf(LINK), lookups)
            assertEquals(JoinUi.Confirm(INVITE), vm.ui.first { it?.join is JoinUi.Confirm }!!.join)
            vm.cancelInvite()
            assertEquals(JoinUi.Idle, vm.ui.first { it?.join == JoinUi.Idle }!!.join)
            assertNull(joinedAs)
            // looked up again (by hand): Back is Cancel and the step stays
            vm.lookUp(LINK)
            vm.ui.first { it?.join is JoinUi.Confirm }
            var left = false
            vm.back { left = true }
            assertEquals(JoinUi.Idle, vm.ui.first { it?.join == JoinUi.Idle }!!.join)
            assertEquals(SetupStep.Join, vm.ui.value!!.step)
            assertFalse(left)
            assertNull(joinedAs)
            // Follow without an invite shown does nothing
            vm.join("Bea")
            assertNull(joinedAs)
        }

    @Test
    fun `confirm writes exactly the shown thresholds to the runtime's store`() =
        runTest {
            roles.role.value = Role.Wearer
            val vm = vm()
            vm.ui.launchIn(backgroundScope)
            vm.ui.first { it != null }
            vm.editThreshold(AlertKind.LOW, 75.0)
            assertEquals(
                70.0,
                alerts.settings.value
                    .rule(AlertKind.LOW)
                    .threshold,
                0.0,
            )
            vm.confirmAlerts()
            assertEquals(
                75.0,
                alerts.settings.value
                    .rule(AlertKind.LOW)
                    .threshold,
                0.0,
            )
            assertTrue(SetupStep.Alerts in record.data.first().completed)
            // the confirmation is recorded where Settings › Alerts reads it
            assertTrue(
                vm.ui
                    .first { it?.facts?.alertsConfirmed == true }!!
                    .facts.alertsConfirmed,
            )
        }

    @Test
    fun `restore is refused while the gate is closed`() =
        runTest {
            roles.role.value = Role.Wearer
            val vm = vm()
            vm.ui.launchIn(backgroundScope)
            vm.ui.first { it != null }
            vm.restore("nsec1abc", null)
            assertTrue("restore" !in sharing.calls)
        }

    @Test
    fun `with a live reading the restore runs, and a wrong password stays on the step`() =
        runTest {
            roles.role.value = Role.Wearer
            hub.live(clock)
            val vm = vm()
            vm.ui.launchIn(backgroundScope)
            vm.ui.first { it != null }
            vm.restore("ncryptsec1abc", "wrong")
            assertEquals(listOf("restore"), sharing.calls)
            assertEquals(RestoreUi.Failed(RestoreIdentity.Result.WrongPassword), vm.ui.first { it?.restore is RestoreUi.Failed }!!.restore)
            assertTrue(SetupStep.Identity !in record.data.first().completed)
            assertTrue(sharing.calls.none { it.startsWith("startFresh") })
        }

    @Test
    fun `opened from Settings › Sharing, setup shows the identity step alone, then leaves`() =
        runTest {
            roles.role.value = Role.Wearer
            // setup finished with the alerts still to confirm and the identity skipped
            record.update {
                SetupRecord(
                    completed = setOf(SetupStep.Welcome, SetupStep.Permissions, SetupStep.Sensor),
                    skipped = setOf(SetupStep.Identity),
                )
            }
            val vm = vm(startAt = SetupStep.Identity)
            vm.ui.launchIn(backgroundScope)
            val u = vm.ui.first { it != null }!!
            assertEquals(SetupStep.Identity, u.step)
            assertTrue(u.single)
            vm.startFresh(Profile("Alex", null))
            assertEquals(null, vm.ui.first { it?.step != SetupStep.Identity }!!.step)
        }

    @Test
    fun `bring your history back records the step before Import history opens, and Back skips it`() =
        runTest {
            roles.role.value = Role.Wearer
            val before = setOf(SetupStep.Welcome, SetupStep.Permissions, SetupStep.Sensor, SetupStep.Alerts, SetupStep.Identity)
            record.update { SetupRecord(completed = before) }
            val vm = vm(startAt = SetupStep.History)
            vm.ui.launchIn(backgroundScope)
            assertEquals(SetupStep.History, vm.ui.first { it?.step != null }!!.step)
            var opened = false
            vm.openHistoryImport { opened = SetupStep.History in recorded().completed }
            vm.ui.first { it?.step == null }
            assertTrue("recorded first", opened)

            record.update { SetupRecord(completed = before) }
            val again = vm(startAt = SetupStep.History)
            again.ui.launchIn(backgroundScope)
            again.ui.first { it?.step == SetupStep.History }
            var left = false
            again.back { left = true }
            record.data.first { SetupStep.History in it.skipped }
            assertTrue(left)
        }

    /** Setup finished with the sensor and the alerts skipped (home's banner: *Alerts not confirmed*). */
    private suspend fun finishedWithTwoSkipped(): Set<SetupStep> {
        roles.role.value = Role.Wearer
        sharing.state.value = OwnerShareState.Sharing(1, emptyList(), emptyList(), emptyMap(), 0, 0)
        val skipped = setOf(SetupStep.Sensor, SetupStep.Alerts)
        record.update {
            SetupRecord(
                completed = setOf(SetupStep.Welcome, SetupStep.Permissions, SetupStep.Identity, SetupStep.History),
                skipped = skipped,
            )
        }
        return skipped
    }

    @Test
    fun `Finish setup walks every skipped step, alerts first, and Later keeps each on the card`() =
        runTest {
            val skipped = finishedWithTwoSkipped()
            val vm = vm()
            vm.ui.launchIn(backgroundScope)
            val first = vm.ui.first { it != null }!!
            assertEquals(SetupStep.Alerts, first.step)
            assertTrue(first.single && !first.canBack)
            // shown alone, the step is still recorded as skipped: leaving now can't drop it from the card
            assertEquals(skipped, recorded().skipped)
            vm.skip(SetupStep.Alerts)
            assertEquals(SetupStep.Sensor, vm.ui.first { it?.step != SetupStep.Alerts }!!.step)
            // Back on a step shown alone leaves it skipped and opens nothing else
            var left = false
            vm.back { left = true }
            assertTrue(left)
            assertEquals(skipped, recorded().skipped)
        }

    @Test
    fun `Finish setup resumes on the same step after a rotation or a process death, then moves on`() =
        runTest {
            finishedWithTwoSkipped()
            val saved = SavedStateHandle()
            val before = vm(saved = saved)
            before.ui.launchIn(backgroundScope)
            before.ui.first { it?.step == SetupStep.Alerts }
            before.skip(SetupStep.Alerts)
            before.ui.first { it?.step == SetupStep.Sensor }

            val after = vm(saved = saved.restored())
            after.ui.launchIn(backgroundScope)
            val u = after.ui.first { it != null }!!
            assertEquals(SetupStep.Sensor, u.step)
            assertTrue(u.single)
            // Later on the last one: home (the alerts step, already shown in this visit, doesn't come back)
            after.skip(SetupStep.Sensor)
            assertEquals(null, after.ui.first { it?.step != SetupStep.Sensor }!!.step)
        }

    @Test
    fun `opened from home with the walk left half-way, setup resumes the walk exactly there`() =
        runTest {
            roles.role.value = Role.Wearer
            record.update { SetupRecord(completed = setOf(SetupStep.Welcome, SetupStep.Permissions), skipped = setOf(SetupStep.Sensor)) }
            assertEquals(SetupCard.Resume(SetupStep.Alerts), module.card.first())
            val vm = vm()
            vm.ui.launchIn(backgroundScope)
            val u = vm.ui.first { it != null }!!
            assertEquals(SetupStep.Alerts, u.step)
            assertEquals(5, u.index)
            assertTrue(!u.single && u.canBack)
        }

    @Test
    fun `Back on the walk's first step after the role leaves setup and keeps every step for the banner`() =
        runTest {
            val vm = vm()
            vm.ui.launchIn(backgroundScope)
            vm.continueWelcome()
            vm.ui.first { it?.step == SetupStep.Role }
            vm.chooseRole(Role.Wearer)
            val u = vm.ui.first { it?.step == SetupStep.Permissions }!!
            assertFalse(u.canBack)
            var left = false
            vm.back { left = true }
            assertTrue(left)
            assertEquals(SetupRecord(completed = setOf(SetupStep.Welcome)), recorded())
            assertEquals(SetupCard.Resume(SetupStep.Permissions), module.card.first())
            // nothing pops up by itself meanwhile: history waits for its place at the end of the walk
            assertFalse(module.historyDue.first())
        }

    @Test
    fun `a step reached with Back is where setup resumes after a process death`() =
        runTest {
            roles.role.value = Role.Wearer
            val saved = SavedStateHandle()
            val before = vm(saved = saved)
            before.ui.launchIn(backgroundScope)
            before.ui.first { it?.step == SetupStep.Permissions }
            before.completePermissions()
            before.ui.first { it?.step == SetupStep.Sensor }
            before.back {}
            before.ui.first { it?.step == SetupStep.Permissions }

            val after = vm(saved = saved.restored())
            after.ui.launchIn(backgroundScope)
            assertEquals(SetupStep.Permissions, after.ui.first { it != null }!!.step)
            // its answer is kept: Continue goes on to the first unanswered step
            after.completePermissions()
            assertEquals(SetupStep.Sensor, after.ui.first { it?.step != SetupStep.Permissions }!!.step)
        }

    @Test
    fun `Settings' Set up sharing shows the identity step alone, and Back changes nothing`() =
        runTest {
            roles.role.value = Role.Wearer
            // a walk left on the alert step: the identity step was never reached
            val walk = SetupRecord(completed = setOf(SetupStep.Welcome, SetupStep.Permissions, SetupStep.Sensor))
            record.update { walk }
            val vm = vm(startAt = SetupStep.Identity)
            vm.ui.launchIn(backgroundScope)
            val u = vm.ui.first { it != null }!!
            assertEquals(SetupStep.Identity, u.step)
            assertTrue(u.single)
            var left = false
            vm.back { left = true }
            assertTrue(left)
            assertEquals(walk, recorded())
            assertEquals(SetupCard.Resume(SetupStep.Alerts), module.card.first())
        }
}

private const val LINK = "https://sukrara.mauri.app/join#AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
private val INVITE = Invite(PublicKeyHex("ab".repeat(32)), "t".repeat(22), Long.MAX_VALUE, emptyList(), Profile("Ana", null))
