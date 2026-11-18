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
import app.mauri.sukrara.core.model.Link
import app.mauri.sukrara.core.model.LinkState
import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.SourceKind
import app.mauri.sukrara.core.model.SourceSpec
import app.mauri.sukrara.core.model.SourceStatus
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.core.testing.InMemorySettingsRepository
import app.mauri.sukrara.core.testing.InMemoryStateStore
import app.mauri.sukrara.feature.sharing.setup.AlertSettingsAccess
import app.mauri.sukrara.feature.sharing.setup.JoinUi
import app.mauri.sukrara.feature.sharing.setup.LluConnectOutcome
import app.mauri.sukrara.feature.sharing.setup.LluConnection
import app.mauri.sukrara.feature.sharing.setup.LluSourcePort
import app.mauri.sukrara.feature.sharing.setup.RestoreUi
import app.mauri.sukrara.feature.sharing.setup.SetupCard
import app.mauri.sukrara.feature.sharing.setup.SetupRecord
import app.mauri.sukrara.feature.sharing.setup.SetupStep
import app.mauri.sukrara.feature.sharing.setup.SetupViewModel
import app.mauri.sukrara.feature.sharing.setup.SourceChange
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
            systemAccess = FakeSystemAccess(),
            llu = llu,
            link = link,
        )
    }

    /** The link's leave: records what was left, with which answers, for which next source. */
    private val link =
        object : LinkPort {
            override suspend fun facts(next: SourceSpec?) =
                LeaveFacts(
                    "Ana",
                    yours = false,
                    told = true,
                    Link.leave(true, Link.Theirs.Differ, true, next?.person == Person.Yours),
                    null,
                )

            override suspend fun leave(
                answers: LeaveAnswers,
                next: SourceSpec?,
            ): Boolean {
                events += "leave:${answers.keepSettings}:${answers.keepHistory}:${next?.kind?.id}"
                return true
            }
        }

    /** LibreLinkUp's Connect step: records each *Try again* (whom the account follows, read again). */
    private val llu =
        object : LluSourcePort {
            override val connection = MutableStateFlow(LluConnection())

            override suspend fun connect(
                email: String,
                password: String,
            ) = LluConnectOutcome.Unreachable

            override suspend fun connectWithLibreView() = LluConnectOutcome.Unreachable

            override suspend fun recheck(): LluConnectOutcome {
                events += "llu:recheck"
                return LluConnectOutcome.Connected
            }

            override suspend fun choose(patient: String) = Unit

            override fun keepPerson() = Unit
        }

    private fun vm(
        startAt: SetupStep? = null,
        saved: SavedStateHandle = SavedStateHandle(),
        review: SourceSpec? = null,
    ) = SetupViewModel(module, startAt, saved, review)

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
        }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `whose glucose, then the source - stored at once the first time, the alert runtime reset first`() =
        runTest {
            val vm = vm()
            vm.ui.launchIn(backgroundScope)
            vm.continueWelcome()
            assertEquals(SetupStep.Person, vm.ui.first { it != null }!!.step)
            vm.choosePerson(Person.Yours)
            assertEquals(Person.Yours, vm.ui.first { it?.step == SetupStep.Source }!!.person)
            assertTrue("nothing stored before the source", events.isEmpty())
            vm.reviewSource(SourceSpec.YourSensor)
            assertEquals(SetupStep.Permissions, vm.ui.first { it?.step == SetupStep.Permissions }!!.step)
            assertEquals(listOf("reset", "source:yours:sensor", "start"), events)
            assertEquals(null, vm.ui.value!!.sourceChange)
        }

    /**
     * One person, one link (link.json `pick`): another source picked while following asks *Keep following?* first;
     * *Keep following* changes nothing; *Stop following* shows the leave questions, and their *Stop following* leaves
     * the link, then stores the source picked.
     */
    @Test
    fun `another source picked while following asks to keep following, then the leave questions`() =
        runTest {
            roles.role.value = SourceSpec.TheirSukrara
            follower.state.value = FollowState.Following(FollowStatus.Active, Profile("Ana", null), "0420", false, null, emptyMap())
            val vm = vm(startAt = SetupStep.Source)
            vm.ui.launchIn(backgroundScope)
            vm.ui.first { it?.step == SetupStep.Source }
            val llu = SourceSpec(Person.Theirs, SourceKind.LibreLinkUp)

            vm.reviewSource(llu)
            val asked = vm.ui.first { it?.sourceChange != null }!!.sourceChange!!
            assertTrue(asked.link!!.keep)
            assertEquals("Ana", asked.link.facts.name)
            vm.cancelSource()
            vm.ui.first { it?.sourceChange == null }
            assertEquals(SourceSpec.TheirSukrara, roles.role.value)
            assertTrue(events.isEmpty())

            vm.reviewSource(llu)
            vm.ui.first { it?.sourceChange?.link?.keep == true }
            vm.stopFollowingFirst()
            assertEquals(
                listOf(Link.Question.Settings, Link.Question.History),
                vm.ui
                    .first {
                        it?.sourceChange?.link?.keep == false
                    }!!
                    .sourceChange!!
                    .link!!
                    .facts.plan.questions,
            )
            vm.leaveAndChoose(LeaveAnswers(keepSettings = false, keepHistory = true))
            vm.ui.first { it?.sourceChange == null }
            assertEquals(listOf("leave:false:true:libreLinkUp", "reset", "source:theirs:libreLinkUp", "start"), events)
        }

    @Test
    fun `a follow that ended goes straight to the leave questions, and the link again asks nothing`() =
        runTest {
            roles.role.value = SourceSpec.TheirSukrara
            follower.state.value = FollowState.Following(FollowStatus.Revoked, Profile("Ana", null), "0420", false, null, emptyMap())
            val vm = vm(startAt = SetupStep.Source)
            vm.ui.launchIn(backgroundScope)
            vm.ui.first { it?.step == SetupStep.Source }
            vm.reviewSource(SourceSpec(Person.Theirs, SourceKind.Nightscout))
            assertEquals(
                false,
                vm.ui
                    .first { it?.sourceChange != null }!!
                    .sourceChange!!
                    .link!!
                    .keep,
            )
            vm.cancelSource()
            vm.ui.first { it?.sourceChange == null }
            vm.reviewSource(SourceSpec.TheirSukrara)
            assertNull(
                vm.ui
                    .first { it != null }!!
                    .sourceChange
                    ?.link,
            )
        }

    @Test
    fun `Back from the source goes to whose glucose and changes nothing`() =
        runTest {
            val vm = vm()
            vm.ui.launchIn(backgroundScope)
            vm.continueWelcome()
            vm.ui.first { it?.step == SetupStep.Person }
            vm.choosePerson(Person.Theirs)
            assertTrue(vm.ui.first { it?.step == SetupStep.Source }!!.canBack)
            vm.back {}
            assertEquals(SetupStep.Person, vm.ui.first { it?.step == SetupStep.Person }!!.step)
            assertTrue(events.isEmpty())
        }

    @Test
    fun `a change of whose glucose that deletes history asks first, and Cancel changes nothing`() =
        runTest {
            val vm = vm()
            vm.ui.launchIn(backgroundScope)
            vm.ui.first { it != null }
            vm.chooseSource(SourceSpec.TheirSukrara)
            vm.ui.first { it?.step == SetupStep.Join }
            events.clear()
            // the join step's Back to the source, with the followed person's history on the phone by then
            val loss = HistoryLoss(4 * 1440, 12, Instant.parse("2026-10-03T08:00:00Z"), Instant.parse("2026-11-10T07:00:00Z"))
            roles.loss = loss
            vm.back {}
            vm.ui.first { it?.step == SetupStep.Source }

            vm.reviewSource(SourceSpec.YourSensor)
            assertEquals(
                SourceChange(SourceSpec.YourSensor, loss, replaces = SourceSpec.TheirSukrara),
                vm.ui.first { it?.sourceChange != null }!!.sourceChange,
            )
            vm.cancelSource()
            val after = vm.ui.first { it?.sourceChange == null }!!
            assertEquals(SetupStep.Source, after.step)
            assertEquals(SourceSpec.TheirSukrara, roles.role.value)
            assertTrue(events.isEmpty())

            // Delete and continue
            vm.reviewSource(SourceSpec.YourSensor)
            vm.ui.first { it?.sourceChange != null }
            vm.chooseSource(SourceSpec.YourSensor)
            assertEquals(listOf("reset", "source:yours:sensor", "start"), events)
            assertEquals(null, vm.ui.first { it?.step != SetupStep.Source }!!.sourceChange)
        }

    @Test
    fun `an install updated from before the question is asked once more, preselected, and keeps its history`() =
        runTest {
            roles.inferred.value = SourceSpec.YourSensor
            record.update { SetupRecord(completed = SetupStep.entries.toSet() - SetupStep.Person - SetupStep.Source) }
            val vm = vm()
            vm.ui.launchIn(backgroundScope)
            val u = vm.ui.first { it != null }!!
            assertEquals(SetupStep.Person, u.step)
            assertEquals(SourceSpec.YourSensor, u.facts.running)
            vm.choosePerson(Person.Yours)
            vm.ui.first { it?.step == SetupStep.Source }
            // the same person: nothing to delete, no confirmation, the alerts keep running
            vm.reviewSource(SourceSpec.YourSensor)
            vm.ui.first { it?.step != SetupStep.Source }
            assertEquals(listOf("source:yours:sensor", "start"), events)
        }

    @Test
    fun `the checklist follows the live readiness, not a copy taken on resume`() =
        runTest {
            roles.role.value = SourceSpec.YourSensor
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
            vm.chooseSource(SourceSpec.TheirSukrara)
            assertEquals(listOf("reset", "source:theirs:sukrara", "start"), events)
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
            vm.chooseSource(SourceSpec.TheirSukrara)
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
            roles.role.value = SourceSpec.YourSensor
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
            roles.role.value = SourceSpec.YourSensor
            val vm = vm()
            vm.ui.launchIn(backgroundScope)
            vm.ui.first { it != null }
            vm.restore("nsec1abc", null)
            assertTrue("restore" !in sharing.calls)
        }

    @Test
    fun `with a live reading the restore runs, and a wrong password stays on the step`() =
        runTest {
            roles.role.value = SourceSpec.YourSensor
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
            roles.role.value = SourceSpec.YourSensor
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
            roles.role.value = SourceSpec.YourSensor
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
        roles.role.value = SourceSpec.YourSensor
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
            roles.role.value = SourceSpec.YourSensor
            record.update { SetupRecord(completed = setOf(SetupStep.Welcome, SetupStep.Permissions), skipped = setOf(SetupStep.Sensor)) }
            assertEquals(SetupCard.Resume(SetupStep.Alerts), module.card.first())
            val vm = vm()
            vm.ui.launchIn(backgroundScope)
            val u = vm.ui.first { it != null }!!
            assertEquals(SetupStep.Alerts, u.step)
            assertEquals(6, u.index)
            assertTrue(!u.single && u.canBack)
        }

    @Test
    fun `Back on the walk's first step after the role leaves setup and keeps every step for the banner`() =
        runTest {
            val vm = vm()
            vm.ui.launchIn(backgroundScope)
            vm.continueWelcome()
            vm.ui.first { it?.step == SetupStep.Person }
            vm.chooseSource(SourceSpec.YourSensor)
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
            roles.role.value = SourceSpec.YourSensor
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
    fun `Settings › Source's Whose glucose goes on to the source, Back returns to the cards, no counter`() =
        runTest {
            roles.role.value = SourceSpec.YourSensor
            record.update { SetupRecord(completed = SetupStep.entries.toSet()) }
            val vm = vm(startAt = SetupStep.Person)
            vm.ui.launchIn(backgroundScope)
            val person = vm.ui.first { it?.step == SetupStep.Person }!!
            assertTrue(person.single && person.visit)
            // a step opened from Settings is no walk: no "Step 2 of 6"
            assertEquals(0, person.count)
            vm.choosePerson(Person.Theirs)
            assertEquals(Person.Theirs, vm.ui.first { it?.step == SetupStep.Source }!!.person)
            var left = false
            vm.back { left = true }
            assertFalse(left)
            // the cards again, the pick kept; nothing stored
            assertEquals(Person.Theirs, vm.ui.first { it?.step == SetupStep.Person }!!.person)
            assertEquals(SourceSpec.YourSensor, roles.role.value)
            vm.back { left = true }
            assertTrue(left)
        }

    @Test
    fun `Nightscout to Juggluco goes on to Connect Juggluco, the site's last reading proves nothing, Appendix G finding 19`() =
        runTest {
            val site = SourceSpec(Person.Yours, SourceKind.Nightscout)
            roles.role.value = site
            record.update { SetupRecord(completed = SetupStep.entries.toSet()) }
            hub.state.value =
                hub.state.value.copy(
                    spec = site,
                    latest = Reading(clock.now(), 120, null, SourceId.Nightscout),
                    source = SourceStatus.Live(clock.now()),
                )
            val vm = vm(startAt = SetupStep.Source)
            vm.ui.launchIn(backgroundScope)
            vm.ui.first { it?.step == SetupStep.Source }
            val juggluco = SourceSpec(Person.Yours, SourceKind.PhoneApp)
            vm.chooseSource(juggluco)
            assertEquals(SetupStep.Sensor, vm.ui.first { it?.step != SetupStep.Source }!!.step)
        }

    @Test
    fun `a change of whose glucose on LibreLinkUp picks again and confirms on the Connect step, Appendix G finding 4`() =
        runTest {
            val theirs = SourceSpec(Person.Theirs, SourceKind.LibreLinkUp)
            roles.role.value = theirs
            record.update { SetupRecord(completed = SetupStep.entries.toSet()) }
            // Ana's glucose arrives live from LibreLinkUp
            hub.state.value =
                hub.state.value.copy(
                    spec = theirs,
                    latest = Reading(clock.now(), 120, null, SourceId.LibreLinkUp),
                    source = SourceStatus.Live(clock.now()),
                )
            val vm = vm(startAt = SetupStep.Person)
            vm.ui.launchIn(backgroundScope)
            vm.ui.first { it?.step == SetupStep.Person }
            vm.choosePerson(Person.Yours)
            vm.ui.first { it?.step == SetupStep.Source }
            val yours = SourceSpec(Person.Yours, SourceKind.LibreLinkUp)
            vm.chooseSource(yours)
            // never Ana's live reading taken as yours: the Connect step shows, and LibreLinkUp picks again
            assertEquals(SetupStep.Sensor, vm.ui.first { it?.step != SetupStep.Source }!!.step)
            assertEquals(listOf("reset", "source:yours:libreLinkUp", "start", "llu:recheck"), events.take(4))
        }

    @Test
    fun `pairing a sensor while someone else's glucose shows asks to delete their history first, Appendix G finding 11`() =
        runTest {
            val theirs = SourceSpec(Person.Theirs, SourceKind.PhoneApp)
            roles.role.value = theirs
            record.update { SetupRecord(completed = SetupStep.entries.toSet()) }
            val loss = HistoryLoss(1440, 4, Instant.parse("2026-11-17T08:00:00Z"), Instant.parse("2026-11-18T07:00:00Z"))
            roles.loss = loss
            val saved = SavedStateHandle()
            val vm = vm(startAt = SetupStep.Source, saved = saved, review = SourceSpec.YourSensor)
            vm.ui.launchIn(backgroundScope)
            val asked = vm.ui.first { it?.sourceChange != null }!!
            assertEquals(SourceChange(SourceSpec.YourSensor, loss, replaces = theirs), asked.sourceChange)
            assertEquals(Person.Yours, asked.person)
            // Cancel: still theirs, nothing deleted; a rotation doesn't ask again by itself
            vm.cancelSource()
            vm.ui.first { it?.sourceChange == null }
            assertEquals(theirs, roles.role.value)
            assertTrue(events.isEmpty())
            val again = vm(startAt = SetupStep.Source, saved = saved.restored(), review = SourceSpec.YourSensor)
            again.ui.launchIn(backgroundScope)
            assertNull(again.ui.first { it != null }!!.sourceChange)
            // Delete and continue: yours, then the sensor step (the pairing)
            vm.reviewSource(SourceSpec.YourSensor)
            vm.ui.first { it?.sourceChange != null }
            vm.chooseSource(SourceSpec.YourSensor)
            assertEquals(SourceSpec.YourSensor, roles.role.value)
            assertEquals(SetupStep.Sensor, vm.ui.first { it?.step == SetupStep.Sensor }!!.step)
        }

    @Test
    fun `Settings' Set up sharing shows the identity step alone, and Back changes nothing`() =
        runTest {
            roles.role.value = SourceSpec.YourSensor
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
