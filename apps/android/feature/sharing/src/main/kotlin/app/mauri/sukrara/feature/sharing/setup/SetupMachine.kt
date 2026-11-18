// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.setup

import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.core.model.SourceKind
import app.mauri.sukrara.core.model.SourceSpec
import kotlinx.serialization.Serializable

/**
 * The first-run setup steps, in their binding order, in two parts (#181): the **essentials** (what must be set before
 * monitoring and alerts can be trusted: whose glucose and where from, the follow, what alerts need, the sensor, the
 * thresholds), then
 * [Ready], then the **extras** (useful, never needed to start: updates, followers, insulin pens, history), which the
 * person walks right away or later from Settings › *Finish setup*. The sensor runs before the identity can be
 * restored (a restore moves the followers to this phone, so it waits until this phone has the sensor).
 */
enum class SetupStep {
    Welcome,

    /**
     * *Whose glucose will Sukrara show?*: yours or someone else's, two illustrated choices (docs/44 "Setup"). Only a
     * pick: [Source] stores both answers together.
     */
    Person,

    /** *Where does it come from?*: the sources the person can use, grouped by what they feel; stores the answer. */
    Source,

    /** A Sukrara stream only: join with the invite link (not skippable: the stream is the source). */
    Join,
    Permissions,
    Sensor,
    Alerts,

    /**
     * Between the parts: the essentials are behind; what is left is listed, *Set up the rest* walks it now, *Later*
     * leaves it for Settings › *Finish setup* (skipped = later). Not shown when this phone has no extras.
     */
    Ready,

    /**
     * The GitHub version only (null [SetupFacts.updates] = a build without an updater, Google Play: never shown):
     * *Keep Sukrara up to date*, the opt-in for the daily update check (F-Droid's rule: updates that bypass a store are
     * opt-in, declining no harder than accepting). *Turn on* or *Not now*, both final here; Settings › About keeps the
     * switch.
     */
    Updates,

    /**
     * Before [Notification]: *Root*, whether this phone is rooted. Signs of root (LSPosed, a root manager) skip the
     * question. *Yes* asks for root at once (the root manager's prompt), then the one missing step with its fix and
     * *Check again* (the same card as Settings › Glucose notification); granted root completes it and picks the
     * status bar text on the next step. *No* is final; *Later* keeps it on *Finish setup*. Done by itself once Sukrara
     * has root.
     */
    Root,

    /**
     * *Glucose notification* (docs/36 §11): the same choice as Settings › Glucose notification (chip, notification
     * icon, status bar text with root), the status bar text already picked when Sukrara has root. *Continue* keeps the
     * choice; no *Later*: the chip is already an answer, and Settings changes it any time.
     */
    Notification,
    Identity,

    /**
     * Wearer with NFC only (null [SetupFacts.pens]): *Insulin pens*, whether to read smart pens. *Read my pens* turns
     * reading on, *I don't use one* is final (a pen tap still asks), *Later* keeps it on *Finish setup*.
     */
    Pens,

    /**
     * Wearer only, last: *Bring your history back* (Import history: LibreView, Tidepool or a file). Offered once: done
     * or skipped, it never comes back; Settings › Share with apps › Import history stays. Offered on its own at launch
     * when it is the only step left (`historyDue`: setup finished before it existed, or an update replaced the
     * database).
     */
    History,
    ;

    /** Welcome, Person and Source can't be skipped: the app does nothing useful without a source. Every other step can. */
    val skippable: Boolean get() = this != Welcome && this != Person && this != Source && this != Join

    /** Before [Ready]: monitoring and alerts can't be trusted until these are answered. */
    val essential: Boolean get() = ordinal < Ready.ordinal

    /** After [Ready]: never needed to start. */
    val extra: Boolean get() = ordinal > Ready.ordinal

    /** An extra whose skip is a final answer (*Not now*, *Start empty*): it leaves *Finish setup* like an answer. */
    internal val skipIsFinal: Boolean get() = this == Updates || this == History
}

/**
 * What the person answered in setup, persisted (typed store `setup`): only answers, never where the screen is (a step
 * opened on its own is screen state, so leaving it half-way can't lose it). The source is NOT here: it lives in its own
 * store (`source`, the hub and the alert runtime read it), so a damaged setup file only shows the skipped steps again
 * and never turns running alerts off.
 */
@Serializable
data class SetupRecord(
    val completed: Set<SetupStep> = emptySet(),
    val skipped: Set<SetupStep> = emptySet(),
)

/** What setup reads from the phone and the other areas, re-read on every resume. */
data class SetupFacts(
    /** Whose glucose and where from, as answered (its own store); null = not answered yet. */
    val spec: SourceSpec?,
    /**
     * What the phone runs on: [spec], else what it is already set up to read (an install updated from before the
     * question): Person and Source are asked once more, preselected with it.
     */
    val running: SourceSpec? = spec,
    /** This phone follows another Sukrara (joined; not declined, used up or removed). */
    val following: Boolean = false,
    /** A live reading this phone got first-hand: its sensor or Juggluco ([RestoreGate.ownSensorLive]). */
    val ownSensorLive: Boolean,
    /** This phone already has a sharing identity (restored or started fresh). */
    val hasIdentity: Boolean,
    /** The alert thresholds were confirmed (here or in Settings › Alerts: `alerts.thresholdsConfirmedAt`). */
    val alertsConfirmed: Boolean = false,
    /** Every item of the alert checklist is met (the Allow-alerts row drops out). */
    val alertsReady: Boolean = false,
    /** The daily update check's answer; null = this build has no updater (Google Play): no updates step. */
    val updates: UpdateChoice? = null,
    /**
     * Insulin pens on this phone: null = it can't read pens (no NFC: no step); true = reading answered (on or off,
     * here, in Settings › Insulin pens or at a pen tap); false = still *Ask at the first pen tap*.
     */
    val pens: Boolean? = null,
    /** Sukrara has root (the root step is then done by itself). */
    val root: Boolean = false,
)

/** Where the GitHub version's daily update check stands (it is opt-in: off until the person turns it on). */
enum class UpdateChoice {
    /** Off and never answered: setup's step and the suggestion banner offer it. */
    Open,

    /** Turned on (here, the banner or Settings › About). */
    On,

    /** Off on purpose: *Not now* (setup or the banner), or turned off in Settings › About. */
    Off,
}

/**
 * One line of the home "Finish setup" card: a skipped essential step that still matters; tapping reopens [step]
 * (the extras are Settings' *Finish setup*, [SetupMachine.extrasLeft]: home stays about glucose). In the order
 * the card names them and Continue walks them: what alerts need first.
 */
enum class FinishItem(val step: SetupStep) {
    AllowAlerts(SetupStep.Permissions),
    AlertsNotConfirmed(SetupStep.Alerts),
    ConnectSensor(SetupStep.Sensor),
}

/**
 * What home's *Finish setup* banner names; its *Continue* opens setup, which picks the same thing ([SetupMachine.card]).
 */
sealed interface SetupCard {
    /** The walk was left before its end (Back on its first step, or never reached): it resumes at [step]. */
    data class Resume(val step: SetupStep) : SetupCard

    /** The walk is over: the first skipped step that still matters (each is then shown alone, in item order). */
    data class Finish(val item: FinishItem) : SetupCard
}

/**
 * The pure setup rules: the ordered steps, what completes or skips each one, and the "Finish setup" items. No Android,
 * no I/O; [SetupViewModel] persists the records it returns.
 */
object SetupMachine {
    fun steps(facts: SetupFacts): List<SetupStep> {
        val spec = facts.spec ?: facts.running
        val all =
            when {
                // the history of a stream is on the other Sukrara: nothing to import; no pens, no followers of its own
                spec?.stream == true -> {
                    listOf(
                        SetupStep.Welcome,
                        SetupStep.Person,
                        SetupStep.Source,
                        SetupStep.Join,
                        SetupStep.Permissions,
                        SetupStep.Alerts,
                        SetupStep.Ready,
                        SetupStep.Updates,
                        SetupStep.Root,
                        SetupStep.Notification,
                    )
                }

                // someone else's glucose from an app on this phone: connect it, nothing shared, uploaded or imported
                spec?.owner == false -> {
                    SetupStep.entries - SetupStep.Join - SetupStep.Identity - SetupStep.Pens - SetupStep.History
                }

                else -> {
                    SetupStep.entries - SetupStep.Join
                }
            }.filter {
                (it != SetupStep.Updates || facts.updates != null) &&
                    (it != SetupStep.Pens || facts.pens != null)
            }
        // nothing optional on this phone: the essentials end setup
        return if (all.none { it.extra }) all - SetupStep.Ready else all
    }

    /** [step]'s 1-based place in its part (the essentials or the extras) and the part's size; null = [SetupStep.Ready]. */
    fun place(
        step: SetupStep,
        facts: SetupFacts,
    ): Pair<Int, Int>? {
        if (step == SetupStep.Ready) return null
        val part = steps(facts).filter { it.essential == step.essential && it != SetupStep.Ready }
        return (part.indexOf(step) + 1).coerceAtLeast(1) to part.size
    }

    /** *Bring it back* opens Import history: the step is done (offered once). */
    fun completeHistory(record: SetupRecord): SetupRecord = record.done(SetupStep.History)

    /**
     * The history step to offer on its own at launch: a wearer's only step left (setup finished before it existed, or
     * an update replaced the database). Never while the walk stopped at an earlier step: home's banner (an essential)
     * or Settings' *Finish setup* (an extra) resumes the walk, which offers it last.
     */
    fun historyDue(
        record: SetupRecord,
        facts: SetupFacts,
    ): Boolean {
        if (facts.spec?.owner != true) return false
        val next = next(record, facts)
        return next == SetupStep.History || (next == null && extrasLeft(record, facts) == listOf(SetupStep.History))
    }

    /** The step to show now, or null when setup is over (home). Resumes exactly where the person left. */
    fun next(
        record: SetupRecord,
        facts: SetupFacts,
    ): SetupStep? {
        // *Later* on Ready: the walk ends with the essentials (the extras wait on Settings' Finish setup)
        val walk = steps(facts).filter { SetupStep.Ready !in record.skipped || it.essential }
        return walk.firstOrNull { !done(it, record, facts) && it !in record.skipped }
    }

    /**
     * Settings' *Finish setup*: the extras still open once the essentials' walk is over (or paused at [SetupStep.Ready]):
     * not answered, not done elsewhere and not declined for good (*Not now*, *Start empty*); none before a source.
     */
    fun extrasLeft(
        record: SetupRecord,
        facts: SetupFacts,
    ): List<SetupStep> {
        if (facts.spec == null || next(record, facts)?.essential == true) return emptyList()
        return steps(facts).filter { it.extra && !done(it, record, facts) && !(it.skipIsFinal && it in record.skipped) }
    }

    /** *Set up the rest* (Ready) or Settings' *Finish setup*: the walk goes on into the extras, those put off included. */
    fun continueExtras(record: SetupRecord): SetupRecord =
        record.copy(
            completed = record.completed + SetupStep.Ready,
            skipped = record.skipped.filterNot { it == SetupStep.Ready || (it.extra && !it.skipIsFinal) }.toSet(),
        )

    /**
     * Home's *Finish setup* banner, or null (no source yet: home says setup is incomplete; or nothing left). Essentials
     * only: a walk left before the essentials' end comes first and resumes where it stopped (never the join step: a
     * stream's home already leads there); the extras are Settings' *Finish setup* ([extrasLeft]). An install updated
     * from before the question runs on what it reads ([SetupFacts.running]): its banner resumes at whose glucose.
     */
    fun card(
        record: SetupRecord,
        facts: SetupFacts,
    ): SetupCard? {
        if (facts.spec == null) return if (facts.running != null) SetupCard.Resume(SetupStep.Person) else null
        val next = next(record, facts)
        if (next != null && next.essential) return if (next == SetupStep.Join) null else SetupCard.Resume(next)
        return finishItems(record, facts).firstOrNull()?.let(SetupCard::Finish)
    }

    /**
     * Back in the walk from [step]: the step before it (its answer is kept), or null = Back leaves setup (home's banner
     * resumes the walk at the step left). From the source back to whose glucose, and from the join step back to the
     * source (another source after all); otherwise never back into them once answered: Settings › Source changes them.
     */
    fun previous(
        step: SetupStep,
        facts: SetupFacts,
    ): SetupStep? {
        val steps = steps(facts)
        val before = steps.getOrNull(steps.indexOf(step) - 1) ?: return null
        return before.takeIf {
            step == SetupStep.Join || step == SetupStep.Source || facts.spec == null || (it != SetupStep.Person && it != SetupStep.Source)
        }
    }

    /**
     * The Finish-setup walk (home's banner after the walk is over): the next skipped step that still matters, not shown
     * yet in this visit ([shown]), in item order; null = home.
     */
    fun nextItem(
        record: SetupRecord,
        facts: SetupFacts,
        shown: Set<SetupStep>,
    ): SetupStep? = finishItems(record, facts).firstOrNull { it.step !in shown }?.step

    /**
     * Whether [change] (one of this machine's step actions, its facts already captured) answers [step]: completes or
     * skips it. A step shown on its own (a Finish-setup item, Settings' *Set up sharing*) is over once this is true,
     * whatever was recorded for it before.
     */
    fun answers(
        step: SetupStep,
        change: (SetupRecord) -> SetupRecord,
    ): Boolean = change(SetupRecord()).let { step in it.completed || step in it.skipped }

    /** Welcome, Person and Source are done once a source is stored (a damaged setup file never asks for it again). */
    private fun done(
        step: SetupStep,
        record: SetupRecord,
        facts: SetupFacts,
    ): Boolean =
        // joined, and the person moved on from the waiting screen (its code); a follow that ended asks again
        if (step == SetupStep.Join) {
            facts.following && step in record.completed
        } else if (step == SetupStep.Updates) {
            step in record.completed || step in record.skipped || facts.updates != UpdateChoice.Open || passed(step, record, facts)
        } else if (step == SetupStep.Ready) {
            // an install that went past it before it existed
            step in record.completed || passed(step, record, facts)
        } else if (step == SetupStep.Root) {
            step in record.completed || facts.root || passed(step, record, facts)
        } else if (step == SetupStep.Notification) {
            step in record.completed || passed(step, record, facts)
        } else if (step == SetupStep.Pens) {
            step in record.completed || facts.pens == true
        } else {
            step in record.completed ||
                (facts.spec != null && (step == SetupStep.Welcome || step == SetupStep.Person || step == SetupStep.Source)) ||
                (step == SetupStep.Alerts && facts.alertsConfirmed) ||
                // restored or started fresh elsewhere (Settings › Sharing's Set up)
                (step == SetupStep.Identity && facts.hasIdentity)
        }

    /**
     * The walk already went past [step]: a later step was answered. Only for a step added to the walk after people
     * finished it (*Keep Sukrara up to date*, [SetupStep.Ready], [SetupStep.Root], [SetupStep.Notification]): an install from before it isn't sent back into setup
     * for it.
     */
    private fun passed(
        step: SetupStep,
        record: SetupRecord,
        facts: SetupFacts,
    ): Boolean {
        val steps = steps(facts)
        return steps.drop(steps.indexOf(step) + 1).any { it in record.completed || it in record.skipped }
    }

    fun completeWelcome(record: SetupRecord): SetupRecord = record.done(SetupStep.Welcome)

    /** *Read my pens* (reading turned on) or *I don't use one* (final; a pen tap still asks): the pens step is done. */
    fun completePens(record: SetupRecord): SetupRecord = record.done(SetupStep.Pens)

    /** *Turn on* (or *Keep it on*) on the updates step: the daily check is on, the step done. */
    fun completeUpdates(record: SetupRecord): SetupRecord = record.done(SetupStep.Updates)

    /** *No* on the root step (final), or *Continue* once Sukrara has root. */
    fun completeRoot(record: SetupRecord): SetupRecord = record.done(SetupStep.Root)

    /** *Continue* on the glucose notification step: the choice made there stays. */
    fun completeNotification(record: SetupRecord): SetupRecord = record.done(SetupStep.Notification)

    /**
     * The source picker's choice for [person] before the person picks one: what this phone runs on when it brings that
     * person's glucose, else the usual one (your sensor; their Sukrara, as following was).
     */
    fun preselected(
        running: SourceSpec?,
        person: Person,
    ): SourceKind =
        running?.kind?.takeIf { running.person == person }
            ?: if (person == Person.Theirs) SourceKind.Sukrara else SourceKind.Sensor

    /** The join step is done once this phone follows someone (the request may still be waiting). */
    fun completeJoin(
        record: SetupRecord,
        facts: SetupFacts,
    ): SetupRecord = if (facts.following) record.done(SetupStep.Join) else record

    /** The checklist step is done (missing items stay on the home reliability row). */
    fun completePermissions(record: SetupRecord): SetupRecord = record.done(SetupStep.Permissions)

    /** The sensor step completes only with a real live reading this phone got first-hand (its sensor or Juggluco). */
    fun completeSensor(
        record: SetupRecord,
        facts: SetupFacts,
    ): SetupRecord = if (facts.ownSensorLive) record.done(SetupStep.Sensor) else record

    /** P4: the only way to complete the alert step is "Confirm thresholds" (the thresholds are saved first). */
    fun confirmAlerts(record: SetupRecord): SetupRecord = record.done(SetupStep.Alerts)

    /** The identity exists (restored, or started fresh after confirming). */
    fun completeIdentity(
        record: SetupRecord,
        facts: SetupFacts,
    ): SetupRecord = if (facts.hasIdentity) record.done(SetupStep.Identity) else record

    /** Skip / "Later": the step goes to the Finish-setup card. Welcome, Person and Source can't be skipped. */
    fun skip(
        record: SetupRecord,
        step: SetupStep,
    ): SetupRecord = if (step.skippable) record.copy(skipped = record.skipped + step) else record

    /**
     * The home card's items: skipped essential steps that still matter. They drop out by themselves once the fact
     * behind them changes (the checklist is complete, a reading arrived, the thresholds were confirmed).
     */
    fun finishItems(
        record: SetupRecord,
        facts: SetupFacts,
    ): List<FinishItem> {
        if (facts.spec == null) return emptyList()
        val steps = steps(facts)
        return FinishItem.entries.filter { item ->
            val step = item.step
            step in steps &&
                step !in record.completed &&
                step in record.skipped &&
                when (item) {
                    FinishItem.ConnectSensor -> !facts.ownSensorLive
                    FinishItem.AlertsNotConfirmed -> !facts.alertsConfirmed
                    FinishItem.AllowAlerts -> !facts.alertsReady
                }
        }
    }

    /**
     * The answer to [step] is forgotten: the walk offers it again. Only for a fact that changed the step's meaning (an
     * update replaced the database: *Bring your history back* again); a step merely opened on its own is screen state.
     */
    fun reopen(
        record: SetupRecord,
        step: SetupStep,
    ): SetupRecord = record.copy(completed = record.completed - step, skipped = record.skipped - step)

    private fun SetupRecord.done(step: SetupStep) = copy(completed = completed + step, skipped = skipped - step)
}
