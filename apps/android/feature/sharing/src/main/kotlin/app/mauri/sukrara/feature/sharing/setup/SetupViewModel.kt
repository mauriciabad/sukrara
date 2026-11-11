// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.setup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.core.model.AlertReadiness
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.feature.sharing.RoleLoss
import app.mauri.sukrara.feature.sharing.SetupModule
import app.mauri.sukrara.feature.sharing.following
import app.mauri.sukrara.protocol.event.Avatar
import app.mauri.sukrara.protocol.event.Profile
import app.mauri.sukrara.protocol.invite.Invite
import app.mauri.sukrara.share.ble.NearbyStatus
import app.mauri.sukrara.share.follower.FollowState
import app.mauri.sukrara.share.follower.InviteLookup
import app.mauri.sukrara.share.follower.JoinResult
import app.mauri.sukrara.share.follower.NearbyOffer
import app.mauri.sukrara.share.follower.NearbySearch
import app.mauri.sukrara.share.owner.OwnerShareState
import app.mauri.sukrara.share.owner.usecase.RestoreIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.security.SecureRandom

/** The restore form's state. The code and password never live here (screen-local, not saved). */
sealed interface RestoreUi {
    data object Idle : RestoreUi

    /** Looking for the backup on the relays (up to ~12 s). */
    data object Busy : RestoreUi

    /** Shown inline under the form; the typed code stays so the person can fix it and retry. */
    data class Failed(val result: RestoreIdentity.Result) : RestoreUi

    data class Restored(val followers: Int) : RestoreUi
}

/** The join step's own state (the link itself stays in the screen, never logged). */
sealed interface JoinUi {
    data object Idle : JoinUi

    /** Looking for the invite on the relays (up to ~25 s), or saving the key after *Follow*. */
    data object Busy : JoinUi

    /**
     * The invite was found: who is inviting (the name and picture in it), waiting for *Follow* or *Cancel*. Nothing
     * was saved or sent yet.
     */
    data class Confirm(val invite: Invite) : JoinUi

    data class Failed(val result: JoinResult) : JoinUi
}

/**
 * A role picked on the role step, waiting for the person's confirmation. [loss] = the history it deletes from this
 * phone (its own dialog, with *Export CSV first*); null = nothing to delete (the plain role confirmation).
 */
data class RoleChange(
    val role: Role,
    val loss: RoleLoss?,
)

/** Everything one setup screen shows. [step] null = setup is over: go home. */
data class SetupUi(
    val step: SetupStep?,
    /** 1-based position and the number of steps ("Step 3 of 7"). */
    val index: Int,
    val count: Int,
    val facts: SetupFacts,
    val record: SetupRecord,
    val readiness: AlertReadiness,
    val alerts: AlertThresholds,
    val gate: RestoreGate,
    val share: OwnerShareState,
    val restore: RestoreUi,
    val follow: FollowState = FollowState.NotFollowing,
    val join: JoinUi = JoinUi.Idle,
    /** An invite link opened on this phone: the join step's field starts with it. */
    val invite: String? = null,
    /** *Find phones nearby* while it looks (null = not looking). */
    val nearby: NearbySearch? = null,
    val busy: Boolean,
    /** Shown alone (a Finish-setup step, or setup opened on one step): no walk around it; Back leaves. */
    val single: Boolean,
    /** Back shows the previous step ([SetupMachine.previous]); false = Back leaves setup. */
    val canBack: Boolean,
    /** The role step's open confirmation (null = none). */
    val roleChange: RoleChange? = null,
)

/**
 * Where setup is, kept as screen state in [saved] (a rotation or a process death resumes exactly there; the record
 * only holds answers, so leaving half-way never loses a step):
 *
 * - opened on one step ([openedOn]: Settings › Sharing's *Set up sharing*, *Bring your history back* at launch, the
 *   join step from an invite link or *Join again*): that step alone until it is answered, then back where it was
 *   opened;
 * - opened from home (or at first launch): the walk resumes at the step left (Back walks to the previous one, and on
 *   its first step leaves); once the walk is over, every skipped step that still matters is shown alone, alerts first
 *   (home's *Finish setup*): done or *Later* → the next one, Back → home, then home.
 */
class SetupViewModel(
    private val m: SetupModule,
    private val openedOn: SetupStep? = null,
    private val saved: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    /** Walk, Finish or Visit ([Mode]); null until setup opened from home has read what is left. */
    private val mode = saved.getMutableStateFlow(MODE, if (openedOn != null) Mode.Visit.name else null)

    /** Finish / Visit: the step shown alone; null = it was answered and nothing is left (the screen leaves). */
    private val alone = saved.getMutableStateFlow(ALONE, openedOn?.name)

    /** Finish: the steps already shown in this visit (done or *Later* moves on, never back to one). */
    private val visited = saved.getMutableStateFlow(VISITED, "")

    /** Walk: the step Back went to (its answer is kept); null = the first unanswered step. */
    private val shown = saved.getMutableStateFlow<String?>(SHOWN, null)

    private val restore = MutableStateFlow<RestoreUi>(RestoreUi.Idle)
    private val joining = MutableStateFlow<JoinUi>(JoinUi.Idle)
    private val busy = MutableStateFlow(false)

    /** The role step's confirmation; kept while *Export CSV first* is open, so it shows again on the way back. */
    private val roleChange = MutableStateFlow<RoleChange?>(null)

    /** The thresholds being edited on the alert step (null = the stored ones). */
    private val editing = MutableStateFlow<AlertSettings?>(null)

    private val inputs =
        combine(
            m.setupStore.data,
            m.roles.role,
            m.hub.state,
            combine(m.sharing.state, m.follower.state, ::Pair),
            combine(m.alertSettings.settings, m.alertsConfirmedAt, ::Pair),
        ) {
            record,
            role,
            glucose,
            (share, follow),
            (alerts, confirmedAt),
            ->
            Inputs(record, role, glucose, share, follow, alerts, confirmedAt != null)
        }

    val ui: StateFlow<SetupUi?> =
        // the checklist is THE live flow (the monitor computes it asynchronously): never a copy taken on resume
        combine(
            inputs,
            m.readiness.readiness,
            combine(mode, alone, shown, ::Triple),
            combine(restore, busy, editing, ::Locals),
            combine(joining, m.pendingInvite, roleChange, ::Triple),
        ) { i, readiness, (modeName, aloneName, backTo), l, (join, invite, change) ->
            // opened from home: nothing until the walk-or-finish decision is made (no step flashes, no early "over")
            val mode = modeName?.let(Mode::valueOf) ?: return@combine null
            val now = m.clock.now()
            val facts =
                SetupFacts(
                    role = i.role,
                    following = i.follow.following,
                    ownSensorLive = RestoreGate.ownSensorLive(i.glucose, now),
                    hasIdentity = i.share is OwnerShareState.Sharing || i.share is OwnerShareState.AnotherAppSharingAsYou,
                    alertsConfirmed = i.alertsConfirmed,
                )
            val steps = SetupMachine.steps(facts)
            val single = mode != Mode.Walk
            val step =
                if (single) {
                    aloneName?.let(SetupStep::valueOf)?.takeIf { it in steps }
                } else {
                    backTo?.let(SetupStep::valueOf)?.takeIf { it in steps } ?: SetupMachine.next(i.record, facts)
                }
            SetupUi(
                step = step,
                index = (steps.indexOf(step) + 1).coerceAtLeast(1),
                count = steps.size,
                facts = facts,
                record = i.record,
                readiness = readiness,
                alerts = AlertThresholds(l.editing ?: i.alerts),
                gate = RestoreGate.evaluate(facts.ownSensorLive),
                share = i.share,
                restore = l.restore,
                follow = i.follow,
                join = join,
                invite = invite,
                busy = l.busy,
                single = single,
                canBack = !single && step != null && SetupMachine.previous(step, facts) != null,
                roleChange = change.takeIf { step == SetupStep.Role },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // opened from home: the walk where it stopped, or (the walk is over) the first skipped step that still matters
        if (mode.value == null) {
            viewModelScope.launch {
                val p = m.progress.first()
                if (SetupMachine.next(p.record, p.facts) != null) {
                    mode.value = Mode.Walk.name
                } else {
                    showAlone(SetupMachine.nextItem(p.record, p.facts, emptySet()))
                    mode.value = Mode.Finish.name
                }
            }
        }
    }

    fun onResume() {
        m.refresh()
    }

    /**
     * Back: the previous step in the walk (its answer is kept), or [onLeave] (the walk's first step: home's banner
     * resumes it there later). A step shown alone just leaves and stays as it was (a skipped one stays on home's
     * banner); only *Bring your history back* on its own is answered by Back (*Start empty*, written first), or it
     * would come back at every launch.
     */
    fun back(onLeave: () -> Unit) {
        // an invite shown for confirmation: Back is its Cancel (the step stays)
        if (joining.value is JoinUi.Confirm) return cancelInvite()
        // the join step's list is gone: Bluetooth stops looking
        searching.value = null
        val u = ui.value ?: return onLeave()
        val step = u.step ?: return onLeave()
        if (u.single) {
            if (step != SetupStep.History) return onLeave()
            // leaving clears this view model and its scope: the answer is written before the screen leaves
            viewModelScope.launch {
                try {
                    m.setupStore.update { SetupMachine.skip(it, step) }
                } finally {
                    onLeave()
                }
            }
            return
        }
        val previous = SetupMachine.previous(step, u.facts) ?: return onLeave()
        shown.value = previous.name
    }

    fun readinessFix(r: app.mauri.sukrara.core.model.AlertRequirement) = m.readiness.fix(r)

    fun readinessAnswerUnlocked(answer: app.mauri.sukrara.core.model.UnlockedSound) = m.readiness.answerUnlockedSound(answer)

    fun continueWelcome() = record(SetupMachine::completeWelcome)

    /**
     * Continue on the role step: asks what choosing [role] would delete, then shows its confirmation
     * ([SetupUi.roleChange]). Nothing changes until the person confirms ([chooseRole]); a failed check leaves the step
     * as it was.
     */
    fun reviewRole(role: Role) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            try {
                roleChange.value = RoleChange(role, m.roles.loss(role))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("Sukrara.setup", "role check failed: ${e.javaClass.simpleName}")
            } finally {
                busy.value = false
            }
        }
    }

    /** Cancel on the role confirmation: the role stays as it was and nothing is deleted. */
    fun cancelRole() {
        roleChange.value = null
    }

    /**
     * P10: called after the person confirmed what the role means (and, when it deletes history, *Delete and
     * continue*). A changed role resets the alert runtime first.
     */
    fun chooseRole(role: Role) =
        act {
            roleChange.value = null
            searching.value = null
            val facts = ui.value?.facts ?: return@act
            val chosen = SetupMachine.chooseRole(role, confirmed = true, facts) as? Result.Done ?: return@act
            if (facts.role != chosen.value) m.resetAlerts()
            m.roles.set(chosen.value)
            m.startMonitoring()
        }

    /** The invite link opened on this phone that was already looked up by itself (not again after a rotation). */
    private var openedLookedUp: String? = null

    /**
     * *Join*: looks up the invite behind [link] (reads only) and shows who is inviting with *Follow* / *Cancel*. An
     * invite link opened on this phone is looked up by itself, once per opening ([opened]).
     */
    fun lookUp(
        link: String,
        opened: Boolean = false,
    ) {
        if (joining.value == JoinUi.Busy || joining.value is JoinUi.Confirm) return
        if (opened) {
            if (link == openedLookedUp) return
            openedLookedUp = link
        }
        joining.value = JoinUi.Busy
        viewModelScope.launch {
            val found =
                try {
                    m.follower.lookUp(link)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    InviteLookup.Failed(JoinResult.Offline)
                }
            joining.value =
                when (found) {
                    is InviteLookup.Found -> JoinUi.Confirm(found.invite)
                    is InviteLookup.Failed -> JoinUi.Failed(found.result)
                }
        }
    }

    /** *Follow* on the invite shown: joins as [name] (with a new picture); then monitoring starts, which sends the request. */
    fun join(name: String) {
        val invite = (joining.value as? JoinUi.Confirm)?.invite ?: return
        joining.value = JoinUi.Busy
        viewModelScope.launch {
            val result =
                try {
                    m.follower.join(invite, profileFor(name))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    JoinResult.Offline
                }
            joining.value = if (result == JoinResult.Joined) JoinUi.Idle else JoinUi.Failed(result)
            if (result == JoinResult.Joined) {
                m.pendingInvite.value = null
                openedLookedUp = null
                // the follow's own link takes over: a second search would spend the shared scan budget
                searching.value = null
            }
        }
    }

    /**
     * *Cancel* (or Back) on the invite shown: nothing was sent. A link opened on this phone leaves the field too (the
     * person may not have wanted it); a pasted one stays.
     */
    fun cancelInvite() {
        if (joining.value !is JoinUi.Confirm) return
        joining.value = JoinUi.Idle
        m.pendingInvite.value = null
        openedLookedUp = null
    }

    /** null = not looking; each new number starts a fresh search (after Bluetooth or the permission came back). */
    private val searching = MutableStateFlow<Int?>(null)

    /** *Find phones nearby*: Bluetooth looks only while the join step shows the list. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val nearbySearch: StateFlow<NearbySearch?> =
        searching
            .flatMapLatest { on ->
                if (on !=
                    null
                ) {
                    m.follower.findNearby().onStart { emit(NearbySearch(NearbyStatus.Searching, emptyList())) }
                } else {
                    flowOf(null)
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun findNearby(on: Boolean) {
        searching.value = if (on) (searching.value ?: 0) + 1 else null
        clearJoinError()
    }

    /** Bluetooth turned on / Nearby devices allowed: a running search starts again. */
    fun refreshNearby() {
        searching.value?.let { searching.value = it + 1 }
    }

    /** Asks the wearer whose phone offered *Pair nearby*; the search stops (the follow's own link takes over). */
    fun joinNearby(
        offer: NearbyOffer,
        name: String,
    ) {
        if (joining.value == JoinUi.Busy) return
        viewModelScope.launch {
            joining.value = JoinUi.Busy
            searching.value = null
            val result =
                try {
                    m.follower.joinNearby(offer, profileFor(name))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    JoinResult.Offline
                }
            joining.value = if (result == JoinResult.Joined) JoinUi.Idle else JoinUi.Failed(result)
        }
    }

    /** *Same code* uses the wearer's key; *Different code* ends the join and says why (back to the link and the list). */
    fun confirmNearby(same: Boolean) {
        viewModelScope.launch {
            val done = m.follower.confirmNearby(same)
            if (done && !same) joining.value = JoinUi.Failed(JoinResult.CodesDiffered)
        }
    }

    private fun profileFor(name: String) =
        name
            .trim()
            .take(Profile.MAX_NAME)
            .takeIf { it.isNotEmpty() }
            ?.let { Profile(it, Avatar.random(SecureRandom())) }

    fun clearJoinError() {
        if (joining.value is JoinUi.Failed) joining.value = JoinUi.Idle
    }

    fun completeJoin() {
        searching.value = null
        val facts = ui.value?.facts ?: return
        record { SetupMachine.completeJoin(it, facts) }
    }

    fun completePermissions() = record(SetupMachine::completePermissions)

    fun completeSensor() {
        val facts = ui.value?.facts ?: return
        record { SetupMachine.completeSensor(it, facts) }
    }

    fun editThreshold(
        kind: AlertKind,
        value: Double,
    ) {
        val field = ThresholdField.entries.firstOrNull { it.kind == kind } ?: return
        val current = ui.value?.alerts ?: return
        editing.value = current.with(field, value).settings
    }

    /** P4: saves exactly what is shown to the store the alert runtime reads, then completes the step. */
    fun confirmAlerts() =
        act {
            val shownAlerts = ui.value?.alerts ?: return@act
            if (!shownAlerts.ordered) return@act
            m.confirmAlerts(shownAlerts)
            editing.value = null
            write(SetupMachine::confirmAlerts)
        }

    /** P10: called after the person confirmed what Start fresh means, with how they appear to followers. */
    fun startFresh(profile: Profile) =
        act {
            val created = m.sharing.startFresh(profile)
            val facts = ui.value?.facts ?: return@act
            write { SetupMachine.completeIdentity(it, facts.copy(hasIdentity = created || facts.hasIdentity)) }
        }

    fun completeIdentity() {
        val facts = ui.value?.facts ?: return
        record { SetupMachine.completeIdentity(it, facts) }
    }

    fun retryConflict() = act { m.sharing.retryConflict() }

    /** Restores only through an open gate, re-checked here (the facts may have changed since the screen drew). */
    fun restore(
        code: String,
        password: String?,
    ) = act {
        val u = ui.first { it != null }!!
        val gate = RestoreGate.evaluate(ownSensorLive = RestoreGate.ownSensorLive(m.hub.state.value, m.clock.now()))
        if (!gate.open) return@act
        restore.value = RestoreUi.Busy
        // an unexpected failure is "nothing changed, try again", never a stuck spinner
        val result =
            try {
                m.sharing.restore(code.trim(), password?.takeIf { it.isNotEmpty() })
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                RestoreIdentity.Result.RelaysUnreachable
            }
        restore.value = if (result is RestoreIdentity.Result.Restored) RestoreUi.Restored(result.followers) else RestoreUi.Failed(result)
        if (result is RestoreIdentity.Result.Restored) write { SetupMachine.completeIdentity(it, u.facts.copy(hasIdentity = true)) }
    }

    fun clearRestoreError() {
        if (restore.value is RestoreUi.Failed) restore.value = RestoreUi.Idle
    }

    fun skip(step: SetupStep) = record { SetupMachine.skip(it, step) }

    /**
     * *Bring it back*: the history step is done (written first, while this screen still lives), then [open] runs
     * (Import history takes setup's place: there is nothing after this step to come back to).
     */
    fun openHistoryImport(open: () -> Unit) {
        viewModelScope.launch {
            try {
                write(SetupMachine::completeHistory)
            } finally {
                open()
            }
        }
    }

    private fun record(change: (SetupRecord) -> SetupRecord) = act { write(change) }

    /**
     * Writes [change]. A step shown alone is over once [change] answers it ([SetupMachine.answers]): Finish setup
     * moves on to the next skipped step not shown yet, a step opened on its own goes back where it was opened.
     */
    private suspend fun write(change: (SetupRecord) -> SetupRecord) {
        m.setupStore.update(change)
        val step = alone.value?.let(SetupStep::valueOf) ?: return
        if (mode.value == Mode.Walk.name || !SetupMachine.answers(step, change)) return
        if (mode.value == Mode.Finish.name) {
            val p = m.progress.first()
            showAlone(SetupMachine.nextItem(p.record, p.facts, visitedSteps()))
        } else {
            alone.value = null
        }
    }

    /** Finish setup shows [step] alone next (null = nothing left: home). */
    private fun showAlone(step: SetupStep?) {
        if (step != null) visited.value = (visitedSteps() + step).joinToString(",") { it.name }
        alone.value = step?.name
    }

    private fun visitedSteps(): Set<SetupStep> =
        visited.value
            .split(',')
            .filter { it.isNotEmpty() }
            .map(SetupStep::valueOf)
            .toSet()

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy.value = true
            try {
                block()
                // the walk goes on from the first unanswered step (a step reached with Back was just answered)
                if (mode.value == Mode.Walk.name) shown.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // the class only (no message: it could carry a code or a value); the screen stays on the step to retry
                android.util.Log.w("Sukrara.setup", "setup action failed: ${e.javaClass.simpleName}")
            } finally {
                busy.value = false
            }
        }
    }

    private data class Inputs(
        val record: SetupRecord,
        val role: Role?,
        val glucose: app.mauri.sukrara.core.model.GlucoseState,
        val share: OwnerShareState,
        val follow: FollowState,
        val alerts: AlertSettings,
        val alertsConfirmed: Boolean,
    )

    private data class Locals(
        val restore: RestoreUi,
        val busy: Boolean,
        val editing: AlertSettings?,
    )

    /** How setup was opened ([SetupViewModel]): walking, Finish setup's skipped steps, or one step on its own. */
    private enum class Mode { Walk, Finish, Visit }

    companion object {
        private const val MODE = "setup.mode"
        private const val ALONE = "setup.alone"
        private const val VISITED = "setup.visited"
        private const val SHOWN = "setup.shown"

        fun factory(
            module: SetupModule,
            startAt: SetupStep? = null,
        ) = viewModelFactory { initializer { SetupViewModel(module, startAt, createSavedStateHandle()) } }
    }
}
