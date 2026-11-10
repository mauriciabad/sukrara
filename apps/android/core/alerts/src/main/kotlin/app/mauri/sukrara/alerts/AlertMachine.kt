// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.alerts

/** Everything that can change what alerts (one call of [AlertMachine.handle] each). */
sealed interface AlertInput {
    /** A new reading (any source; an older one than the newest only re-evaluates). */
    data class Reading(val reading: AlertReading) : AlertInput

    /** The timer ([AlertEffect.Schedule]) or any re-check. */
    data object Tick : AlertInput

    /** The treatment log changed (logged, edited, deleted; deleted ones already left out). */
    data class Treatments(val doses: Doses) : AlertInput

    data class Settings(val settings: AlertSettings) : AlertInput

    /** The sensor started, ended or was replaced (null = none known). */
    data class Sensor(val sensor: SensorWindow?) : AlertInput

    /**
     * The reading source started at [startedAt] (epoch s; null = none): no data counts from it until a newer reading,
     * so a source that never delivers a first reading alerts too.
     */
    data class Source(val startedAt: Long?) : AlertInput

    /** This device's actions (SPEC §2): never shared with the followers. */
    data class Mute(val kind: AlertKind, val minutes: Int) : AlertInput

    data class RemindLess(val kind: AlertKind) : AlertInput

    data class RemindAsUsual(val kind: AlertKind) : AlertInput

    data class Unmute(val kind: AlertKind) : AlertInput

    /** *Mute all* until [until] (epoch s, [Rules.NEVER] = until turned off). */
    data class MuteAll(val until: Long) : AlertInput

    data object UnmuteAll : AlertInput

    /** The wearer's `alert` event (followers mirroring the wearer; ignored otherwise). */
    data class Remote(val event: AlertEvent) : AlertInput
}

/** What the runtime does, in order; [Schedule] is always the last effect of a call. */
sealed interface AlertEffect {
    /** Post [firing]'s notification ([silent]: shown again after a restart, no sound). */
    data class Fire(val firing: Firing, val silent: Boolean = false) : AlertEffect

    /** Remove the notification of firing [id]. */
    data class Clear(val kind: AlertKind, val id: String) : AlertEffect

    /** [side]'s Mute / Remind less ended: back by the margin (history line, badge). */
    data class Back(val side: Side) : AlertEffect

    /** Send [event] to the followers (wearer only; what the rules decide without this device's actions). */
    data class Mirror(val event: AlertEvent) : AlertEffect

    /** Wake [at] (epoch s, exact alarm) and send [AlertInput.Tick]; null = no timer needed. */
    data class Schedule(val at: Long?) : AlertEffect
}

/**
 * The alert state machine of one device, the one entry point of the runtime: [handle] an [AlertInput] at `now`, get
 * [AlertEffect]s. Per [role]: the wearer runs the rules twice (SPEC §2) — [local] with this device's actions for its
 * notifications, [shared] without them for the followers ([AlertEffect.Mirror]); a follower with its own rules runs
 * [local]; a follower mirroring the wearer shows the wearer's events through [WearerMirror] and still checks its own
 * `no_data` locally (its link to the wearer). [snapshot] after every call; [start] after a restart, also from the
 * alarm receiver with the service dead (no data needs only the saved last reading and `now`).
 */
class AlertMachine(
    val role: AlertRole,
    settings: AlertSettings,
    doses: Doses = Doses(),
    newId: () -> String = AlertEngine::randomId,
) {
    var settings: AlertSettings = settings
        private set
    var doses: Doses = doses
        private set
    private val local = AlertEngine(newId)
    private val shared = if (role == AlertRole.WEARER) AlertEngine(newId) else null
    private var mirror = if (role == AlertRole.FOLLOWER_MIRROR) WearerMirror() else null
    private var lastNow = 0L

    /** [lastNow]'s moment on the monotonic clock (null = not seen in this process). */
    private var lastElapsed: Long? = null

    /** The rules [local] runs: a mirroring follower checks only `no_data` itself. */
    private val localSettings: AlertSettings
        get() =
            if (role != AlertRole.FOLLOWER_MIRROR) {
                settings
            } else {
                settings.copy(
                    rules =
                        AlertKind.entries.associateWith { k ->
                            settings.rule(k).let {
                                if (k ==
                                    AlertKind.NO_DATA
                                ) {
                                    it
                                } else {
                                    it.copy(on = false)
                                }
                            }
                        },
                )
            }

    val engine: AlertEngine get() = local
    val wearerMirror: WearerMirror? get() = mirror

    /**
     * Restores [saved] (ignored when it belongs to another role) and seeds the newest stored reading [seed], sensor
     * [sensor], then evaluates once (whatever came due while the process was down; a stale reading never alerts).
     * Returns the notifications to show again silently, what alerts now, and the timer.
     */
    fun start(
        saved: AlertState?,
        seed: AlertReading?,
        sensor: SensorWindow?,
        now: Long,
        elapsed: Long? = null,
    ): List<AlertEffect> {
        val state = saved?.takeIf { it.role == role }
        local.sensor = sensor
        shared?.sensor = sensor
        val shown = state?.let { local.restore(it.local, localSettings, now) }.orEmpty()
        state?.shared?.let { shared?.restore(it, settings, now) }
        state?.mirror?.let { if (role == AlertRole.FOLLOWER_MIRROR) mirror = WearerMirror(it) }
        lastNow = state?.lastNow ?: 0
        observe(now, elapsed) // a clock set back while the process was down
        local.seed(seed?.notAfter(now))
        shared?.seed(seed?.notAfter(now))
        // what came due while the process was down (no data, a repeat, a mute's end) alerts now; stale readings can't
        return shown.map { AlertEffect.Fire(it, silent = true) } + reevaluate(now) + schedule(now)
    }

    /** What every surface shows about this device's alerts (the mirror's mutes on a mirroring follower). */
    fun uiState(now: Long): AlertUiState {
        val own = AlertUiState.of(local, localSettings, doses, now)
        val m = mirror ?: return own
        return own.copy(
            quiet = own.quiet + AlertUiState.ofMirror(m, settings, now).quiet,
            muteAllUntil = maxOf(own.muteAllUntil, m.muteAllUntil.takeIf { it > now } ?: 0),
        )
    }

    /** The badge under the value at [now] (the effective rules, also a mirroring follower's: the wearer's). */
    fun status(now: Long): AlertStatus = AlertStatus.of(local.last, settings, doses, now, uiState(now))

    fun snapshot(): AlertState =
        AlertState(role = role, local = local.snapshot(), shared = shared?.snapshot(), mirror = mirror?.snapshot(), lastNow = lastNow)

    /**
     * Notes [now] at [elapsed] (the monotonic clock in s, `SystemClock.elapsedRealtime`; null = unknown). A clock set
     * back (beyond NTP noise) moves every kept time back with it ([AlertEngine.shiftBack]): by exactly the jump when
     * the monotonic clock says how much time passed since the last input, else (the first input after a restart) as if
     * none had, so what was due comes at most that long later.
     */
    private fun observe(now: Long, elapsed: Long?) {
        val passed = lastElapsed?.let { prev -> elapsed?.minus(prev)?.takeIf { it >= 0 } }
        val delta = lastNow + (passed ?: 0) - now
        if (delta > Rules.CLOCK_BACK_TOLERANCE_S) {
            local.shiftBack(delta)
            shared?.shiftBack(delta)
            mirror?.shiftBack(delta)
        }
        if (passed != null || delta > Rules.CLOCK_BACK_TOLERANCE_S || now > lastNow) lastNow = now
        if (elapsed != null) lastElapsed = elapsed
    }

    /** Runs [input] at [now] (epoch s); [elapsed] = the monotonic clock in s, so a clock set back is measured exactly. */
    fun handle(input: AlertInput, now: Long, elapsed: Long? = null): List<AlertEffect> {
        observe(now, elapsed)
        val out = mutableListOf<AlertEffect>()
        when (input) {
            is AlertInput.Reading -> {
                out += published(shared?.reading(input.reading, settings, doses, now))
                out += mirrorReading(input.reading, now)
                out += own(local.reading(input.reading, localSettings, doses, now))
            }

            AlertInput.Tick -> {
                out += published(shared?.evaluate(settings, doses, now))
                out += mirrorReading(local.last, now)
                out += own(local.evaluate(localSettings, doses, now))
            }

            is AlertInput.Treatments -> {
                doses = input.doses
                out += reevaluate(now)
            }

            is AlertInput.Settings -> {
                settings = input.settings
                if (!settings.on) mirror?.let { m -> m.hideAll().forEach { out += AlertEffect.Clear(it.kind, it.id) } }
                out += reevaluate(now)
            }

            is AlertInput.Sensor -> {
                local.sensor = input.sensor
                shared?.sensor = input.sensor
                out += reevaluate(now)
            }

            is AlertInput.Source -> {
                out += published(shared?.sourceStarted(input.startedAt, settings, doses, now))
                out += own(local.sourceStarted(input.startedAt, localSettings, doses, now))
            }

            is AlertInput.Remote -> {
                out += remote(input.event, now)
            }

            else -> {
                out += action(input, now)
            }
        }
        // a follower's Mute all over by its time (the timer, or any input that comes first): the wearer's newest alert
        // still up shows at once — after the input, so a firing arriving at that moment shows once
        mirror?.let { m -> m.expire(now).forEach { out += AlertEffect.Fire(it) } }
        return out + schedule(now)
    }

    private fun reevaluate(now: Long): List<AlertEffect> =
        published(shared?.evaluate(settings, doses, now)) + own(local.evaluate(localSettings, doses, now))

    private fun mirrorsKind(kind: AlertKind) = mirror != null && kind != AlertKind.NO_DATA

    private fun action(input: AlertInput, now: Long): List<AlertEffect> {
        val s = localSettings
        val m = mirror
        return when (input) {
            is AlertInput.Mute -> {
                if (input.minutes <= 0) {
                    emptyList()
                } else if (m != null && mirrorsKind(input.kind)) {
                    listOfNotNull(m.mute(input.kind, now + input.minutes * 60L)?.let { AlertEffect.Clear(it.kind, it.id) })
                } else {
                    own(local.mute(input.kind, input.minutes, s, doses, now))
                }
            }

            is AlertInput.RemindLess -> {
                if (m != null && mirrorsKind(input.kind)) {
                    emptyList<AlertEffect>().also { m.relax(input.kind, settings.actions.relaxMin) }
                } else {
                    own(local.relax(input.kind, s, doses, now))
                }
            }

            is AlertInput.RemindAsUsual -> {
                if (m != null && mirrorsKind(input.kind)) {
                    listOfNotNull(m.unrelax(input.kind, now)?.let { AlertEffect.Fire(it) })
                } else {
                    own(local.unrelax(input.kind, s, doses, now))
                }
            }

            is AlertInput.Unmute -> {
                if (m != null && mirrorsKind(input.kind)) {
                    listOfNotNull(m.unmute(input.kind, now)?.let { AlertEffect.Fire(it) })
                } else {
                    own(local.unmute(input.kind, s, doses, now))
                }
            }

            is AlertInput.MuteAll -> {
                m?.muteAll(input.until).orEmpty().map { AlertEffect.Clear(it.kind, it.id) } +
                    own(local.muteAll(input.until, s, doses, now))
            }

            AlertInput.UnmuteAll -> {
                m?.unmuteAll(now).orEmpty().map { AlertEffect.Fire(it) } + own(local.unmuteAll(s, doses, now))
            }

            else -> {
                error("not an action: $input")
            }
        }
    }

    private fun remote(e: AlertEvent, now: Long): List<AlertEffect> {
        val m = mirror ?: return emptyList()
        if (!settings.on || now - e.at > AlertEvent.MAX_AGE_S || e.k == AlertKind.NO_DATA) return emptyList()
        return when (e.state) {
            AlertEvent.State.FIRE -> e.toFiring().let { f -> if (m.fire(f)) listOf(AlertEffect.Fire(f)) else emptyList() }
            AlertEvent.State.CLEAR -> listOfNotNull(m.clear(e.k, e.id)?.let { AlertEffect.Clear(it.kind, it.id) })
        }
    }

    private fun mirrorReading(r: AlertReading?, now: Long): List<AlertEffect> =
        mirror?.reading(r, settings, now).orEmpty().map { AlertEffect.Back(it) }

    private fun own(actions: List<RuleAction>): List<AlertEffect> =
        actions.map {
            when (it) {
                is RuleAction.Fire -> AlertEffect.Fire(it.firing)
                is RuleAction.Clear -> AlertEffect.Clear(it.kind, it.id)
                is RuleAction.Back -> AlertEffect.Back(it.side)
            }
        }

    /** The shared run's decisions as `alert` events (its Back can't happen: it has no actions of this device). */
    private fun published(actions: List<RuleAction>?): List<AlertEffect> =
        actions.orEmpty().mapNotNull {
            when (it) {
                is RuleAction.Fire -> AlertEffect.Mirror(AlertEvent.fire(it.firing))
                is RuleAction.Clear -> AlertEffect.Mirror(AlertEvent.clear(it.kind, it.id, it.at, shared?.last))
                is RuleAction.Back -> null
            }
        }

    private fun schedule(now: Long) =
        AlertEffect.Schedule(
            listOfNotNull(local.nextCheckAt(localSettings, now), shared?.nextCheckAt(settings, now), mirror?.nextCheckAt(now)).minOrNull(),
        )
}
