// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.alerts

import app.mauri.sukrara.alerts.AlertEffect
import app.mauri.sukrara.alerts.AlertEngine
import app.mauri.sukrara.alerts.AlertEvent
import app.mauri.sukrara.alerts.AlertGroup
import app.mauri.sukrara.alerts.AlertInput
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertMachine
import app.mauri.sukrara.alerts.AlertReading
import app.mauri.sukrara.alerts.AlertRole
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.alerts.AlertSource
import app.mauri.sukrara.alerts.AlertState
import app.mauri.sukrara.alerts.AlertStatus
import app.mauri.sukrara.alerts.Doses
import app.mauri.sukrara.alerts.Firing
import app.mauri.sukrara.alerts.QuietReason
import app.mauri.sukrara.alerts.Rules
import app.mauri.sukrara.alerts.SensorWindow
import app.mauri.sukrara.alerts.Side
import app.mauri.sukrara.core.model.AlertControls
import app.mauri.sukrara.core.model.AlertQuietReason
import app.mauri.sukrara.core.model.AlertQuietSide
import app.mauri.sukrara.core.model.AlertUiState
import app.mauri.sukrara.core.model.CardStatus
import app.mauri.sukrara.core.model.Clock
import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.SensorTiming
import app.mauri.sukrara.core.model.Treatment
import app.mauri.sukrara.core.model.TreatmentKind
import app.mauri.sukrara.core.model.TreatmentQuiet
import app.mauri.sukrara.monitor.lockedboot.LockedBootPlan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import app.mauri.sukrara.alerts.AlertUiState as EngineUiState
import app.mauri.sukrara.alerts.TreatmentKind as EngineTreatmentKind

/**
 * THE alert runtime of this process: the one owner of the [AlertMachine] (the rules). The monitoring service
 * feeds it ([observe]: hub state, settings, treatments, the wearer's mirrored settings and `alert` events), the alarm
 * receiver ticks it with the service dead ([tick]: it resumes from the saved [AlertRecord]), and every surface acts on
 * it ([AlertControls]). Because both paths go through this one object, an alert can't post twice.
 *
 * Every input runs under one [Mutex], in this order: the machine decides → the effects are applied (notifications,
 * followers, the one exact alarm) → the state is saved → the summary is published to the hub. Applying before saving
 * means a process death in between rings again after the restart (the safe failure) instead of resuming silently.
 *
 * No phantom alarms: until the hub says monitoring started, `no_data` is off; without a role (setup not
 * done, or the hub still loading) nothing runs.
 */
class AlertRuntime(
    private val store: AlertStateStore,
    private val treatments: Flow<List<Treatment>>,
    private val presenter: AlertPresenter,
    private val alarm: AlertAlarm,
    private val events: AlertEventSink,
    private val publish: (AlertUiState) -> Unit,
    private val clock: Clock,
    private val newId: () -> String = AlertEngine::randomId,
    /** The diagnostic log (`DiagLog`): step names and exception classes only, never values. */
    private val log: (String) -> Unit = {},
    /** Saves what the phone needs before the first unlock after a restart (`LockedBootStore`), with every save. */
    private val lockedBoot: (LockedBootPlan) -> Unit = {},
    /** The monotonic clock in s (`SystemClock.elapsedRealtime`): a wall clock set back is measured exactly. */
    private val elapsed: () -> Long? = { null },
    /** The phone's time zone now (it changes when travelling): the night alerts' window is read in it (#38). */
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : AlertControls {
    private val mutex = Mutex()
    private var started = false
    private var machine: AlertMachine? = null
    private var own = AlertSettings()
    private var wearer: AlertSettings? = null
    private var role: Role? = null
    private var monitoring = false
    private var doses = Doses()
    private var sensor: SensorWindow? = null

    /**
     * When monitoring started (epoch s) as last given to the machine (it keeps its own copy): no data counts from it
     * until the source delivers a reading, also with none ever.
     */
    private var sourceStart: Long? = null
    private var who: String? = null
    private var fedReadingAt: Long? = null

    /** The recent history (Settings › Alerts › Lately), oldest first, saved with the record. */
    private var notes: List<SavedNote> = emptyList()

    /** When Mute all ends as the history last saw it (0 = off), saved: its end by itself gets one *Alerts back on*. */
    private var muteAllSeen = 0L

    /** Which alert each group's notification shows (from the effects), and the muted cards up. */
    private val firing = HashMap<AlertGroup, Firing>()

    /** The kind each group last rang (shown or not since): a level change inside one alert gets its history line. */
    private val lastFired = HashMap<AlertGroup, AlertKind>()
    private val mutedCards = HashMap<AlertGroup, Pair<Side, Long>>()
    private val shownRelax = HashMap<AlertGroup, Int>()

    /**
     * What failed on its last attempt (a step, or a step for one group's notification): listed in the hub's alert state
     * until that same thing works again.
     */
    private val failed = HashSet<String>()

    /** Alerts whose post Android refused, with the sound they should have had: posted again, as loud, next time. */
    private val unposted = HashMap<AlertGroup, Boolean>()

    private fun now(): Long = clock.now().epochSecond

    /** Feeds the runtime while the monitoring service runs (cancelled with [scope]). */
    fun observe(
        scope: CoroutineScope,
        state: Flow<GlucoseState>,
        wearerSettings: Flow<AlertSettings?>,
        remoteEvents: Flow<AlertEvent>,
    ) {
        scope.launch { store.settings.collect { safely { onSettings(it) } } }
        scope.launch { wearerSettings.collect { safely { onWearerSettings(it) } } }
        scope.launch { treatments.collect { safely { onDoses(dosesOf(it)) } } }
        // only what the rules read: the hub's alerts field is this runtime's own output (publishUi → hub.setAlerts), so
        // observing the whole state took the mutex again after every publish (#44)
        scope.launch { state.map(AlertFeed::of).distinctUntilChanged().collect { safely { onFeed(it) } } }
        scope.launch { remoteEvents.collect { safely { process(AlertInput.Remote(it)) } } }
    }

    /**
     * One failing input (a load that failed, a broken store) never stops the collectors that follow: it is logged and
     * the next input or the exact alarm tries again. Effect failures don't reach here ([applyAndSave] isolates them).
     */
    private suspend fun safely(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("input failed: ${e.javaClass.simpleName}")
        }
    }

    /**
     * "Start fresh" / leaving a role: every alert notification goes, the alarm is cancelled, the saved state
     * is dropped and nothing runs until the hub reports a role again.
     */
    suspend fun reset() {
        mutex.withLock {
            started = true
            (firing.keys + mutedCards.keys).forEach { g -> attempt(Step.Notify, g) { presenter.clear(g) } }
            firing.clear()
            lastFired.clear()
            mutedCards.clear()
            shownRelax.clear()
            machine = null
            role = null
            monitoring = false
            sensor = null
            sourceStart = null
            fedReadingAt = null
            failed.clear()
            notes = emptyList()
            muteAllSeen = 0
            unposted.clear()
            attempt(Step.Alarm) { alarm.set(null) }
            attempt(Step.Save) {
                store.save { AlertRecord() }
                lockedBoot(LockedBootPlan())
            }
            attempt(Step.Publish) { publishUi(now()) }
        }
    }

    /** Publishes the alert state now, without an input. */
    suspend fun refreshUi() {
        mutex.withLock { publishUi(now()) }
    }

    /** The exact alarm fired (or a boot / clock change): evaluate now, also with the service dead. */
    suspend fun tick() {
        process(AlertInput.Tick)
    }

    override suspend fun mute(kind: String, minutes: Int) = action(kind) { AlertInput.Mute(it, minutes) }

    override suspend fun unmute(kind: String) = action(kind) { AlertInput.Unmute(it) }

    override suspend fun remindLess(kind: String) = action(kind) { AlertInput.RemindLess(it) }

    override suspend fun remindAsUsual(kind: String) = action(kind) { AlertInput.RemindAsUsual(it) }

    override suspend fun muteAll(minutes: Int?) {
        process(AlertInput.MuteAll(if (minutes == null) Rules.NEVER else now() + minutes * 60L))
    }

    override suspend fun unmuteAll() {
        process(AlertInput.UnmuteAll)
    }

    /**
     * #39: the person tapped or swiped an alert, or opened the app: an answer to a very low showing on this phone (the
     * followers' escalation doesn't come, or is withdrawn). [kind] null = whatever alert is up (the app opened). In a
     * process Android had stopped (the swipe's broadcast or the app's start brought it back) the saved alerts are read
     * first: an answer is never lost to a runtime that hasn't started yet. A store that fails is logged (the app's
     * start must never crash on it).
     */
    suspend fun acknowledge(kind: String? = null) =
        safely {
            start()
            val k = kind?.let(AlertKind::fromWire) ?: firing[AlertGroup.LEVEL]?.kind ?: return@safely
            if (machine?.role == AlertRole.WEARER) process(AlertInput.Acknowledge(k))
        }

    /**
     * Shows [kind]'s notification again without sound: a *Mute…* reply that wasn't a length changes nothing, and the
     * re-post takes the reply's spinner away.
     */
    suspend fun reshow(kind: String) {
        mutex.withLock {
            val group = AlertKind.fromWire(kind)?.group ?: return
            val m = machine ?: return
            val f = firing[group]
            if (f != null) {
                presenter.show(f, m.settingsAt(now()), silent = true, who = whoFor(m), relaxedMin = relaxedMin(m, group, now()))
            } else {
                mutedCards.remove(group)
                reconcileMutedCards(now())
            }
        }
    }

    private suspend fun action(kind: String, input: (AlertKind) -> AlertInput) {
        AlertKind.fromWire(kind)?.let { process(input(it)) }
    }

    internal suspend fun onState(state: GlucoseState) = onFeed(AlertFeed.of(state))

    private suspend fun onFeed(state: AlertFeed) {
        val r = state.role ?: return // setup not done, or the hub hasn't loaded yet: never act on that
        mutex.withLock {
            ensureStarted()
            val now = now()
            who = state.wearerName
            val monitoringNow = state.monitoringStartedAt != null
            val window = windowOf(state.sensor)
            // before no data turns on: it counts from the source's start, never from an older source's last reading
            val started = state.monitoringStartedAt?.epochSecond
            if (started != sourceStart) {
                sourceStart = started
                run(AlertInput.Source(started), now)
            }
            if (r != role || monitoringNow != monitoring) {
                monitoring = monitoringNow
                rebuildOrUpdate(r, now)
            }
            if (window != sensor) {
                sensor = window
                run(AlertInput.Sensor(window), now)
            }
            val latest = state.latest
            if (latest != null && latest.t.epochSecond != fedReadingAt) {
                fedReadingAt = latest.t.epochSecond
                run(AlertInput.Reading(readingOf(latest)), now)
            }
        }
    }

    internal suspend fun onSettings(settings: AlertSettings) {
        mutex.withLock {
            ensureStarted()
            val sourceChanged = settings.source != own.source
            own = settings
            if (sourceChanged) rebuildOrUpdate(role, now()) else run(AlertInput.Settings(effectiveSettings()), now())
        }
    }

    internal suspend fun onWearerSettings(settings: AlertSettings?) {
        mutex.withLock {
            ensureStarted()
            wearer = settings
            run(AlertInput.Settings(effectiveSettings()), now())
        }
    }

    internal suspend fun onDoses(d: Doses) {
        mutex.withLock {
            ensureStarted()
            doses = d
            run(AlertInput.Treatments(d), now())
        }
    }

    /** Runs [input] and returns the effects applied (the scenario tests read them). */
    internal suspend fun process(input: AlertInput): List<AlertEffect> =
        mutex.withLock {
            ensureStarted()
            when (input) {
                is AlertInput.Settings -> own = input.settings
                is AlertInput.Treatments -> doses = input.doses
                is AlertInput.Sensor -> sensor = input.sensor
                is AlertInput.Source -> sourceStart = input.startedAt
                else -> Unit
            }
            run(input, now())
        }

    /** Resumes from the saved record (first input of this process); returns what resuming applied. */
    internal suspend fun start(): List<AlertEffect> = mutex.withLock { ensureStarted() }

    private suspend fun ensureStarted(): List<AlertEffect> {
        if (started) return emptyList()
        // everything is read before the runtime counts as started: a failed or cancelled load is tried again next time
        val settings = store.settings.first()
        val loadedDoses = dosesOf(treatments.first())
        val record = store.load()
        own = settings
        doses = loadedDoses
        started = true
        monitoring = record.monitoring
        who = record.who
        notes = record.notes
        muteAllSeen = record.muteAllUntil
        sensor = record.sensor?.window
        val saved = record.machine ?: return emptyList()
        role = if (saved.role == AlertRole.WEARER) Role.Wearer else Role.Follower
        return startMachine(saved.role, saved, now())
    }

    private suspend fun rebuildOrUpdate(newRole: Role?, now: Long) {
        val alertRole = newRole?.let(::alertRoleOf)
        role = newRole
        val m = machine
        if (m != null && m.role == alertRole) {
            run(AlertInput.Settings(effectiveSettings()), now)
            return
        }
        // a new role: the old role's notifications go; the machine drops a saved state of another role
        firing.keys.toList().forEach { g -> attempt(Step.Notify, g) { presenter.clear(g) } }
        firing.clear()
        lastFired.clear()
        mutedCards.clear()
        unposted.clear()
        if (alertRole == null) {
            machine = null
            attempt(Step.Alarm) { alarm.set(null) }
            attempt(Step.Save) { save() }
            attempt(Step.Publish) { publishUi(now) }
        } else {
            startMachine(alertRole, m?.snapshot(), now)
        }
    }

    private suspend fun startMachine(alertRole: AlertRole, saved: AlertState?, now: Long): List<AlertEffect> {
        val m = AlertMachine(alertRole, effectiveSettings(alertRole), doses, newId, zone)
        machine = m
        val effects = m.start(saved, saved?.local?.last, sensor, now, elapsed())
        fedReadingAt = saved?.local?.last?.t
        // a machine that starts without a saved state of its role (a new role) learns when monitoring started
        val fresh = saved?.role != alertRole && sourceStart != null
        val source = if (fresh) m.handle(AlertInput.Source(sourceStart), now, elapsed()) else emptyList()
        return applyAndSave(m, effects + source, now)
    }

    private suspend fun run(input: AlertInput, now: Long): List<AlertEffect> {
        val m = machine ?: return emptyList()
        val effective = if (input is AlertInput.Settings) AlertInput.Settings(effectiveSettings()) else input
        return applyAndSave(m, m.handle(effective, now, elapsed()), now, input)
    }

    /**
     * Applies [effects], then saves and publishes. Every step is attempted on its own: a notification Android refused
     * never costs the exact alarm (the only thing that re-checks with the service dead), the saved state or the other
     * notifications. The alarm goes first for the same reason. A failed step is logged and listed in the hub's alert
     * state ([AlertUiState.failedSteps]: the checklist's AlertDelivery) until it works again; a refused alert is posted again, with its
     * sound, on the next input.
     */
    private suspend fun applyAndSave(
        m: AlertMachine,
        effects: List<AlertEffect>,
        now: Long,
        input: AlertInput? = null,
    ): List<AlertEffect> {
        val muteAll = m.uiState(now).muteAllUntil
        notes = AlertNotes.append(notes, AlertNotes.of(input, effects, now, muteAllSeen, muteAll, lastFired))
        muteAllSeen = muteAll
        effects.filterIsInstance<AlertEffect.Fire>().forEach { lastFired[it.firing.kind.group] = it.firing.kind }
        effects.filterIsInstance<AlertEffect.Schedule>().forEach { e -> attempt(Step.Alarm) { alarm.set(e.at) } }
        val firedNow = effects.filterIsInstance<AlertEffect.Fire>().map { it.firing.kind.group }.toSet()
        retryUnposted(m, now, skip = firedNow)
        for (e in ringOrder(effects, m.settingsAt(now))) {
            when (e) {
                is AlertEffect.Fire -> {
                    val group = e.firing.kind.group
                    firing[group] = e.firing
                    mutedCards.remove(group)
                    val relax = relaxedMin(m, group, now)
                    shownRelax[group] = relax
                    post(m, group, e.firing, e.silent, relax)
                }

                is AlertEffect.Clear -> {
                    if (firing[e.kind.group].let { it == null || it.id == e.id }) {
                        firing.remove(e.kind.group)
                        unposted.remove(e.kind.group)
                        attempt(Step.Notify, e.kind.group) { presenter.clear(e.kind.group) }
                    }
                }

                is AlertEffect.Back -> {
                    Unit
                }

                // the history line is the (it reads the state)
                is AlertEffect.Mirror -> {
                    attempt(Step.Mirror) { events.publish(e.event) }
                }

                // #39: the wearer answered the escalated very low: shown again plain, without sound
                is AlertEffect.Answered -> {
                    val group = e.firing.kind.group
                    if (firing[group]?.id == e.firing.id) {
                        firing[group] = e.firing
                        post(m, group, e.firing, silent = true, relaxedMin(m, group, now))
                    }
                }

                // the history line (above)
                is AlertEffect.Escalated -> {
                    Unit
                }

                is AlertEffect.Schedule -> {
                    Unit
                } // applied first, above
            }
        }
        refreshRelaxed(m, now)
        reconcileMutedCards(now)
        attempt(Step.Save) { save() }
        attempt(Step.Publish) { publishUi(now) }
        return effects
    }

    /** Posts [f] in [group]'s slot; a refusal is remembered with its sound, for [retryUnposted]. */
    private suspend fun post(m: AlertMachine, group: AlertGroup, f: Firing, silent: Boolean, relax: Int) {
        if (!silent) presenter.beforeRing()
        if (attempt(Step.Notify, group) { presenter.show(f, m.settingsAt(now()), silent, whoFor(m), relax) }) {
            unposted.remove(group)
        } else {
            unposted[group] = silent && unposted[group] != false // a loud post that failed stays loud
        }
    }

    /** Posts the alerts Android refused earlier again, as loud as they were meant to be (unless [skip]: posted now). */
    private suspend fun retryUnposted(m: AlertMachine, now: Long, skip: Set<AlertGroup>) {
        for ((group, silent) in unposted.toMap()) {
            if (group in skip) continue
            val f = firing[group]
            if (f == null) {
                unposted.remove(group) // gone meanwhile
                continue
            }
            post(m, group, f, silent, relaxedMin(m, group, now))
        }
    }

    /**
     * Runs one effect step (one real call; [group] for a notification slot); a failure is logged (step + exception
     * class, no health data) and remembered until the same step (for the same group) works again.
     */
    private suspend fun attempt(step: Step, group: AlertGroup? = null, block: suspend () -> Unit): Boolean {
        val key = if (group == null) step.name else "${step.name}:${group.wire}"
        return try {
            block()
            failed.remove(key)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed.add(key)
            log("${step.name.lowercase()} failed: ${e.javaClass.simpleName}")
            false
        }
    }

    /** The kinds of effect step [attempt] tracks. */
    internal enum class Step { Alarm, Notify, Mirror, Save, Publish }

    /**
     * A side muted by this device (from the notification or the app) shows "… alerts muted" with its count-down and
     * the ways out in its group's slot, unless an alert of that group is up; it goes when the mute ends.
     */
    private suspend fun reconcileMutedCards(now: Long) {
        val m = machine ?: return
        val want =
            m
                .uiState(now)
                .quiet
                .filter { it.reason == QuietReason.NOTIFICATION && it.until in (now + 1) until Rules.NEVER }
                .associateBy { it.kind.group }
                .filterKeys { it !in firing }
        for (group in mutedCards.keys - want.keys) attempt(Step.Notify, group) { presenter.clear(group) }
        mutedCards.keys.retainAll(want.keys)
        for ((group, q) in want) {
            val card = q.kind.side to q.until
            if (mutedCards[group] == card) continue
            attempt(Step.Notify, group) {
                presenter.showMuted(group, q.kind.side, q.kind, q.until, m.settingsAt(now), now)
                mutedCards[group] = card // only once shown: a refused post is tried again next time
            }
        }
    }

    /** *Remind less* / *as usual* swaps the alert's button and interval line: shown again silently. */
    private suspend fun refreshRelaxed(m: AlertMachine, now: Long) {
        for ((group, f) in firing) {
            val relax = relaxedMin(m, group, now)
            if (shownRelax[group] == relax || group in unposted) continue
            attempt(Step.Notify, group) {
                presenter.show(f, m.settingsAt(now), silent = true, who = whoFor(m), relaxedMin = relax)
                shownRelax[group] = relax
            }
        }
    }

    private fun whoFor(m: AlertMachine) = who.takeIf { m.role != AlertRole.WEARER }

    private fun relaxedMin(m: AlertMachine, group: AlertGroup, now: Long) =
        m
            .uiState(now)
            .quiet
            .firstOrNull { it.kind.group == group }
            ?.relaxMin ?: 0

    private suspend fun save() {
        val m = machine
        val snapshot = m?.snapshot()
        store.save {
            it.copy(
                machine = snapshot,
                monitoring = monitoring,
                who = who,
                sensor = sensor?.let(::SavedSensor),
                notes = notes,
                muteAllUntil = muteAllSeen,
            )
        }
        // the machine's settings already have no data off before monitoring started
        lockedBoot(
            LockedBootPlan.of(m?.settings, monitoring, m?.engine?.noDataSince(), m?.uiState(now())?.muteAllUntil ?: 0, sensor),
        )
    }

    private fun publishUi(now: Long) {
        val m = machine
        val ui = m?.uiState(now) ?: EngineUiState()
        publish(
            AlertUiState(
                firingKinds = firing.values.map { it.kind.wire }.toSet(),
                status = m?.status(now)?.let(::cardStatusOf) ?: CardStatus.None,
                treatmentQuiet = m?.let { treatmentQuietOf(it.settings, ui) } ?: TreatmentQuiet(),
                mutedUntil = instantOf(ui.muteAllUntil),
                nightUntil = ui.nightUntil.takeIf { it > 0 }?.let(Instant::ofEpochSecond),
                quiet =
                    ui.quiet.map { q ->
                        AlertQuietSide(
                            kind = q.kind.wire,
                            matching = q.matching,
                            reason =
                                when (q.reason) {
                                    QuietReason.NOTIFICATION -> AlertQuietReason.Muted
                                    QuietReason.CARBS -> AlertQuietReason.AfterCarbs
                                    QuietReason.INSULIN -> AlertQuietReason.AfterInsulin
                                    QuietReason.FALLING -> AlertQuietReason.FallingBack
                                    null -> null
                                },
                            until = q.until.takeIf { it in 1 until Rules.NEVER }?.let(Instant::ofEpochSecond),
                            relaxMin = q.relaxMin,
                            canRemindLess = q.canRelax,
                        )
                    },
                failedSteps = if (m == null) emptySet() else failed.toSet(),
                lately = notes.asReversed().mapNotNull { it.note() },
            ),
        )
    }

    private fun alertRoleOf(r: Role): AlertRole =
        when {
            r == Role.Wearer -> AlertRole.WEARER
            own.source == AlertSource.WEARER -> AlertRole.FOLLOWER_MIRROR
            else -> AlertRole.FOLLOWER_OWN
        }

    private fun effectiveSettings(alertRole: AlertRole? = machine?.role): AlertSettings {
        val base = if (alertRole == null || alertRole == AlertRole.WEARER) own else AlertMirror.effective(own, wearer)
        if (monitoring) return base
        return base.withRule(AlertKind.NO_DATA, base.rule(AlertKind.NO_DATA).copy(on = false))
    }

    companion object {
        fun readingOf(r: Reading) = AlertReading(r.t.epochSecond, r.mgdl, r.rateMgdlPerMin?.toDouble())

        fun dosesOf(treatments: List<Treatment>) =
            Doses(
                carbs = treatments.filter { !it.deleted && it.kind == TreatmentKind.Carbs }.map { it.at.epochSecond }.sorted(),
                rapid = treatments.filter { !it.deleted && it.kind == TreatmentKind.RapidInsulin }.map { it.at.epochSecond }.sorted(),
            )

        /** The engine's sensor life from what the hub shows (null: no window known, no data counts as usual). */
        fun windowOf(s: SensorState): SensorWindow? =
            when (s) {
                is SensorState.Active -> {
                    s.endsAt?.let { SensorWindow(startEpoch = 0, endEpoch = it.epochSecond, warmupEndEpoch = 0) }
                }

                // the card's dates for the same state (`cardInfo`): start = ready − the default warm-up
                is SensorState.WarmingUp -> {
                    s.readyAt.epochSecond.let { ready ->
                        val start = ready - SensorTiming.DEFAULT_WARMUP_MIN * 60L
                        SensorWindow(start, start + SensorTiming.DEFAULT_WEAR_MIN * 60L, ready)
                    }
                }

                SensorState.Ended -> {
                    SensorWindow(startEpoch = 0, endEpoch = 1, warmupEndEpoch = 0)
                }

                else -> {
                    null
                }
            }

        /**
         * How long each treatment quiets alerts under [s], the rules the engine runs (`Rules.coveringDose`: a dose quiets
         * while `now − t < quietSeconds`), for the cards' mute lines and last-treatment ring; a rule that's off quiets
         * nothing (as the Mac's `CardAlertsMapping.muteEnd`). A quiet the person lifted (*Alert me anyway* on the high /
         * low side, [ui]'s `lifted`) ends for the doses logged up to then, as in the engine.
         */
        fun treatmentQuietOf(s: AlertSettings, ui: EngineUiState = EngineUiState()): TreatmentQuiet =
            TreatmentQuiet(
                rapidS = s.quietSeconds(EngineTreatmentKind.RAPID).takeIf { s.afterInsulin.on },
                carbsS = s.quietSeconds(EngineTreatmentKind.CARBS).takeIf { s.afterCarbs.on },
                rapidLiftedAt = ui.lifted[Side.HIGH] ?: 0,
                carbsLiftedAt = ui.lifted[Side.LOW] ?: 0,
            )

        /** The badge's answer in the card's terms (contracts/graph/status.json). */
        fun cardStatusOf(s: AlertStatus): CardStatus =
            when (s) {
                AlertStatus.None -> {
                    CardStatus.None
                }

                is AlertStatus.Needed -> {
                    CardStatus.Needed(lowSide(s.kind))
                }

                is AlertStatus.Taken -> {
                    CardStatus.Taken(
                        if (s.treatment == EngineTreatmentKind.RAPID) TreatmentKind.RapidInsulin else TreatmentKind.Carbs,
                        s.at,
                        cardEnd(s.until),
                    )
                }

                is AlertStatus.Paused -> {
                    CardStatus.Paused(lowSide(s.kind), cardEnd(s.until))
                }

                is AlertStatus.Muted -> {
                    CardStatus.Muted(cardEnd(s.until))
                }
            }

        /** The card's low side: very low, low and falling fast (what carbs answer). */
        private fun lowSide(k: AlertKind) = k.side == Side.LOW || k.side == Side.FALLING_FAST

        private fun cardEnd(until: Long) = if (until >= Rules.NEVER) CardStatus.NEVER else until

        fun instantOf(muteAllUntil: Long): Instant? =
            when {
                muteAllUntil <= 0 -> null
                muteAllUntil >= Rules.NEVER -> AlertUiState.UNTIL_TURNED_OFF
                else -> Instant.ofEpochSecond(muteAllUntil)
            }
    }
}

/**
 * The part of the hub's [GlucoseState] the alert rules read: role, the follower's link name, when monitoring started, the
 * sensor and the newest reading. Never the hub's `alerts` (the runtime's own output), so publishing the alert state
 * doesn't feed back into the runtime (#44).
 */
internal data class AlertFeed(
    val role: Role?,
    val wearerName: String?,
    val monitoringStartedAt: Instant?,
    val sensor: SensorState,
    val latest: Reading?,
) {
    companion object {
        fun of(s: GlucoseState) = AlertFeed(s.role, s.link?.wearerName, s.monitoringStartedAt, s.sensor, s.latest)
    }
}
