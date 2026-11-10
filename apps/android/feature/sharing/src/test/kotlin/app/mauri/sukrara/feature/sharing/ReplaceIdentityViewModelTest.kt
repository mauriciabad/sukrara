// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing

import app.mauri.sukrara.core.model.AlertUiState
import app.mauri.sukrara.core.model.GlucoseHub
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.LinkState
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.SourceId
import app.mauri.sukrara.core.model.SourceStatus
import app.mauri.sukrara.core.testing.FakeClock
import app.mauri.sukrara.feature.sharing.backup.FreshUi
import app.mauri.sukrara.feature.sharing.backup.ReplaceIdentityViewModel
import app.mauri.sukrara.feature.sharing.backup.ReplaceStep
import app.mauri.sukrara.feature.sharing.setup.RestoreUi
import app.mauri.sukrara.protocol.event.Profile
import app.mauri.sukrara.share.owner.EpochKey
import app.mauri.sukrara.share.owner.OwnerShareState
import app.mauri.sukrara.share.owner.OwnerState
import app.mauri.sukrara.share.owner.TellingFollowers
import app.mauri.sukrara.share.owner.usecase.RestoreIdentity
import app.mauri.sukrara.share.owner.usecase.Step
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Backup › Replace this identity: each step's exits, the gate, and that only a confirmed replace reaches the engine. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReplaceIdentityViewModelTest {
    private val clock = FakeClock()
    private val hub = Hub()
    private val sharing =
        FakeOwnerSharing(
            OwnerShareState.Sharing(1, emptyList(), emptyList(), emptyMap(), 0, 0, profile = Profile("Ana", null)),
        )

    private fun vm() = ReplaceIdentityViewModel(sharing, hub, clock)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the restore form goes back to the choice and the choice leaves the page`() =
        runTest {
            val vm = vm()
            vm.ui.launchIn(backgroundScope)
            vm.chooseRestore()
            assertEquals(ReplaceStep.Restore, vm.ui.first { it.step == ReplaceStep.Restore }.step)
            var left = 0
            vm.back { left++ }
            assertEquals(ReplaceStep.Choose, vm.ui.first { it.step == ReplaceStep.Choose }.step)
            assertEquals(0, left)
            vm.back { left++ }
            assertEquals(1, left)
            assertTrue(sharing.calls.isEmpty())
        }

    @Test
    fun `restore waits for this phone's own live sensor, then replaces`() =
        runTest {
            val vm = vm()
            vm.ui.launchIn(backgroundScope)
            vm.chooseRestore()
            vm.restore("nsec1code", null)
            assertTrue(sharing.calls.isEmpty())
            assertFalse(vm.ui.value.gate.open)

            hub.live(clock)
            assertTrue(
                vm.ui
                    .first { it.gate.open }
                    .gate.open,
            )
            vm.restore("nsec1code", null)
            assertEquals(listOf("restore:replace"), sharing.calls)
            assertEquals(RestoreUi.Failed(RestoreIdentity.Result.WrongPassword), vm.ui.first { it.restore is RestoreUi.Failed }.restore)
            // a failure keeps the form; editing clears it
            vm.clearRestoreError()
            assertEquals(RestoreUi.Idle, vm.ui.first { it.restore == RestoreUi.Idle }.restore)

            sharing.restoreResult = RestoreIdentity.Result.SameIdentity
            vm.restore("nsec1code", null)
            assertEquals(RestoreUi.Failed(RestoreIdentity.Result.SameIdentity), vm.ui.first { it.restore is RestoreUi.Failed }.restore)
        }

    @Test
    fun `start fresh keeps how the wearer appears and ends on Done`() =
        runTest {
            val vm = vm()
            vm.ui.launchIn(backgroundScope)
            sharing.startFreshAccepted = false
            vm.startFresh()
            assertEquals(FreshUi.Failed, vm.ui.first { it.fresh == FreshUi.Failed }.fresh)
            sharing.startFreshAccepted = true
            vm.startFresh()
            assertEquals(listOf("startFresh:Ana:replace", "startFresh:Ana:replace"), sharing.calls)
            val done = vm.ui.first { it.done }
            assertEquals(FreshUi.Done, done.fresh)
            // once replaced, Back leaves
            var left = false
            vm.back { left = true }
            assertTrue(left)
        }

    /** #161: the followers are told first (*Telling your followers…* with Cancel); Cancel keeps the identity. */
    @Test
    fun `telling the followers shows progress, Cancel keeps the identity and the page says so`() =
        runTest {
            val vm = vm()
            vm.ui.launchIn(backgroundScope)
            sharing.followersToTell = 2
            vm.startFresh()
            assertEquals(TellingFollowers(2, 0), vm.ui.first { it.telling != null }.telling)
            assertTrue(vm.ui.value.busy)
            vm.cancel()
            val kept = vm.ui.first { it.cancelled }
            assertEquals(FreshUi.Idle, kept.fresh)
            assertEquals(null, kept.telling)
            assertFalse(kept.done)
            assertEquals(listOf("startFresh:Ana:replace", "cancelReplace"), sharing.calls)

            // the restore path: a cancelled replace returns to the form, nothing failed
            hub.live(clock)
            vm.ui.first { it.gate.open }
            vm.chooseRestore()
            assertFalse(vm.ui.first { it.step == ReplaceStep.Restore }.cancelled)
            sharing.restoreResult = RestoreIdentity.Result.Restored(Step(OwnerState("i", "d", "s", listOf(EpochKey(1, "g")))), 2)
            vm.restore("nsec1code", null)
            vm.ui.first { it.telling != null }
            vm.cancel()
            val back = vm.ui.first { it.cancelled }
            assertEquals(RestoreUi.Idle, back.restore)
            assertEquals(ReplaceStep.Restore, back.step)

            // told without Cancel: replaced
            vm.restore("nsec1code", null)
            vm.ui.first { it.telling != null }
            sharing.finishTelling()
            assertEquals(RestoreUi.Restored(2), vm.ui.first { it.done }.restore)
        }

    private class Hub : GlucoseHub {
        override val state = MutableStateFlow(GlucoseState.Initial)

        fun live(clock: FakeClock) {
            state.value =
                state.value.copy(latest = Reading(clock.now(), 120, null, SourceId.Libre3), source = SourceStatus.Live(clock.now()))
        }

        override suspend fun ingest(
            readings: List<Reading>,
            from: SourceId,
        ) = Unit

        override fun setSource(status: SourceStatus) = Unit

        override fun setSensor(sensor: SensorState) = Unit

        override fun setAlerts(alerts: AlertUiState) = Unit

        override fun setLink(link: LinkState?) = Unit
    }
}
