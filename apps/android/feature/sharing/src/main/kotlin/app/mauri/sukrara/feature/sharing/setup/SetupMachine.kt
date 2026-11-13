// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.setup

import app.mauri.sukrara.core.model.Role
import kotlinx.serialization.Serializable

/**
 * The first-run setup steps, in their binding order: the sensor runs here before the alerts are
 * confirmed and before the identity can be restored (a restore moves the followers to this phone, so it waits until
 * this phone has the sensor).
 */
enum class SetupStep {
    Welcome,
    Role,

    /** Follower only: join with the wearer's invite link (not skippable: following is the role). */
    Join,
    Permissions,

    /**
     * The GitHub version only (null [SetupFacts.updates] = a build without an updater, Google Play: never shown):
     * *Keep Sukrara up to date*, the opt-in for the daily update check (F-Droid's rule: updates that bypass a store are
     * opt-in, declining no harder than accepting). *Turn on* or *Not now*, both final here; Settings › About keeps the
     * switch. Not a Finish-setup item: once passed, home's suggestion banner is its second chance.
     */
    Updates,

    Sensor,
    Alerts,
    Identity,

    /**
     * Wearer only, last: *Bring your history back* (Import history: LibreView, Tidepool or a file). Offered once: done
     * or skipped, it never comes back (not among the Finish-setup items); Settings › Share with apps › Import history
     * stays. Offered on its own at launch when it is the only step left (`historyDue`: setup finished before it
     * existed, or an update replaced the database).
     */
    History,
    ;

    /** Welcome and Role can't be skipped: the app does nothing useful without a role. Every other step can. */
    val skippable: Boolean get() = this != Welcome && this != Role && this != Join
}

/**
 * What the person answered in setup, persisted (typed store `setup`): only answers, never where the screen is (a step
 * opened on its own is screen state, so leaving it half-way can't lose it). The role is NOT here: it lives in its own
 * store (`role`, the hub and the alert runtime read it), so a damaged setup file only shows the skipped steps again
 * and never turns a running wearer's alerts off.
 */
@Serializable
data class SetupRecord(
    val completed: Set<SetupStep> = emptySet(),
    val skipped: Set<SetupStep> = emptySet(),
)

/** What setup reads from the phone and the other areas, re-read on every resume. */
data class SetupFacts(
    /** The phone's role (its own store); null = not chosen yet. */
    val role: Role?,
    /** This phone follows someone (joined; not declined, used up or removed). */
    val following: Boolean = false,
    /** A live reading from this phone's own sensor ([RestoreGate.ownSensorLive]). */
    val ownSensorLive: Boolean,
    /** This phone already has a sharing identity (restored or started fresh). */
    val hasIdentity: Boolean,
    /** The alert thresholds were confirmed (here or in Settings › Alerts: `alerts.thresholdsConfirmedAt`). */
    val alertsConfirmed: Boolean = false,
    /** Every item of the alert checklist is met (the Allow-alerts row drops out). */
    val alertsReady: Boolean = false,
    /** The daily update check's answer; null = this build has no updater (Google Play): no updates step. */
    val updates: UpdateChoice? = null,
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
 * One line of the home "Finish setup" card: a skipped step that still matters; tapping reopens [step]. In the order
 * the card names them and Continue walks them: what alerts need first.
 */
enum class FinishItem(val step: SetupStep) {
    AllowAlerts(SetupStep.Permissions),
    AlertsNotConfirmed(SetupStep.Alerts),
    ConnectSensor(SetupStep.Sensor),
    RestoreFollowers(SetupStep.Identity),
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

/** Why choosing a role didn't happen. */
enum class RoleRefusal {
    /** P10: the role is chosen only after the person confirmed what it means. */
    NotConfirmed,
}

/**
 * The pure setup rules: the ordered steps, what completes or skips each one, and the "Finish setup" items. No Android,
 * no I/O; [SetupViewModel] persists the records it returns.
 */
object SetupMachine {
    fun steps(facts: SetupFacts): List<SetupStep> =
        when (facts.role) {
            // a follower's history is the wearer's (it comes from their phone): nothing to import
            Role.Follower -> {
                listOf(SetupStep.Welcome, SetupStep.Role, SetupStep.Join, SetupStep.Permissions, SetupStep.Updates, SetupStep.Alerts)
            }

            Role.Wearer, null -> {
                SetupStep.entries - SetupStep.Join
            }
        }.filter { it != SetupStep.Updates || facts.updates != null }

    /** *Bring it back* opens Import history: the step is done (offered once). */
    fun completeHistory(record: SetupRecord): SetupRecord = record.done(SetupStep.History)

    /**
     * The history step to offer on its own at launch: a wearer's only step left (setup finished before it existed, or
     * an update replaced the database). Never while the walk stopped at an earlier step: home's banner resumes the walk,
     * which offers it last.
     */
    fun historyDue(
        record: SetupRecord,
        facts: SetupFacts,
    ): Boolean = facts.role == Role.Wearer && next(record, facts) == SetupStep.History

    /** The step to show now, or null when setup is over (home). Resumes exactly where the person left. */
    fun next(
        record: SetupRecord,
        facts: SetupFacts,
    ): SetupStep? = steps(facts).firstOrNull { !done(it, record, facts) && it !in record.skipped }

    /**
     * Home's *Finish setup* banner, or null (no role yet: home says setup is incomplete; or nothing left). A walk left
     * before its end comes first and resumes where it stopped; never the join step (a follower's home already leads
     * there: no reading yet, or the follow ended).
     */
    fun card(
        record: SetupRecord,
        facts: SetupFacts,
    ): SetupCard? {
        if (facts.role == null) return null
        val next = next(record, facts)
        if (next != null) return if (next == SetupStep.Join) null else SetupCard.Resume(next)
        return finishItems(record, facts).firstOrNull()?.let(SetupCard::Finish)
    }

    /**
     * Back in the walk from [step]: the step before it (its answer is kept), or null = Back leaves setup (home's banner
     * resumes the walk at the step left). Never back into the role once chosen, except from the join step (a phone that
     * wears the sensor after all).
     */
    fun previous(
        step: SetupStep,
        facts: SetupFacts,
    ): SetupStep? {
        val steps = steps(facts)
        val before = steps.getOrNull(steps.indexOf(step) - 1) ?: return null
        return before.takeIf { step == SetupStep.Join || facts.role == null || it != SetupStep.Role }
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

    /** Welcome and Role are done once a role exists (a damaged setup file never asks for the role again). */
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
        } else {
            step in record.completed ||
                (facts.role != null && (step == SetupStep.Welcome || step == SetupStep.Role)) ||
                (step == SetupStep.Alerts && facts.alertsConfirmed)
        }

    /**
     * The walk already went past [step]: a later step was answered. Only for a step added to the walk after people
     * finished it (*Keep Sukrara up to date*): an install from before it isn't sent back into setup for it (home's
     * suggestion banner offers it instead).
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

    /** *Turn on* (or *Keep it on*) on the updates step: the daily check is on, the step done. */
    fun completeUpdates(record: SetupRecord): SetupRecord = record.done(SetupStep.Updates)

    /** P10: the role to store, or why not: refused until [confirmed]. */
    fun chooseRole(
        role: Role,
        confirmed: Boolean,
        facts: SetupFacts,
    ): Result<Role, RoleRefusal> =
        when {
            !confirmed -> Result.Refused(RoleRefusal.NotConfirmed)
            else -> Result.Done(role)
        }

    /** The join step is done once this phone follows someone (the request may still be waiting). */
    fun completeJoin(
        record: SetupRecord,
        facts: SetupFacts,
    ): SetupRecord = if (facts.following) record.done(SetupStep.Join) else record

    /** The checklist step is done (missing items stay on the home reliability row). */
    fun completePermissions(record: SetupRecord): SetupRecord = record.done(SetupStep.Permissions)

    /** The sensor step completes only with a real live reading from this phone's sensor. */
    fun completeSensor(
        record: SetupRecord,
        facts: SetupFacts,
    ): SetupRecord = if (facts.ownSensorLive) record.done(SetupStep.Sensor) else record

    /** P4: the only way to complete the alert step is "Use these thresholds" (the thresholds are saved first). */
    fun confirmAlerts(record: SetupRecord): SetupRecord = record.done(SetupStep.Alerts)

    /** The identity exists (restored, or started fresh after confirming). */
    fun completeIdentity(
        record: SetupRecord,
        facts: SetupFacts,
    ): SetupRecord = if (facts.hasIdentity) record.done(SetupStep.Identity) else record

    /** Skip / "Later": the step goes to the Finish-setup card. Welcome and Role can't be skipped. */
    fun skip(
        record: SetupRecord,
        step: SetupStep,
    ): SetupRecord = if (step.skippable) record.copy(skipped = record.skipped + step) else record

    /**
     * The home card's items: skipped steps that still matter. They drop out by themselves once the fact behind them
     * changes (a reading arrived, the thresholds were confirmed, an identity exists).
     */
    fun finishItems(
        record: SetupRecord,
        facts: SetupFacts,
    ): List<FinishItem> {
        if (facts.role == null) return emptyList()
        val steps = steps(facts)
        return FinishItem.entries.filter { item ->
            val step = item.step
            step in steps &&
                step !in record.completed &&
                step in record.skipped &&
                when (item) {
                    FinishItem.ConnectSensor -> !facts.ownSensorLive
                    FinishItem.RestoreFollowers -> !facts.hasIdentity
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

/** A pure decision: the new value, or why not. */
sealed interface Result<out T, out E> {
    data class Done<T>(val value: T) : Result<T, Nothing>

    data class Refused<E>(val reason: E) : Result<Nothing, E>
}
