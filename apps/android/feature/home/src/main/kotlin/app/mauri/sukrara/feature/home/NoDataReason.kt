// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.home

import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.NotMonitoringReason
import app.mauri.sukrara.core.model.Role
import app.mauri.sukrara.core.model.SensorAction
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.ServiceState
import app.mauri.sukrara.core.model.SourceStatus
import app.mauri.sukrara.feature.home.banners.BlockedTaps
import app.mauri.sukrara.graph.GlucoseLook
import java.time.Instant

/**
 * Why the home screen has no value to show, and what the person can do about it. Never a bare "---": every reason has
 * a title, an explanation and (when something can be done) an [action]. The first matching rule wins, in the order
 * of [of]: things the person must fix before things that are just slow.
 */
sealed interface NoDataReason {
    val action: HomeAction?

    /**
     * True = there is nothing for the card to draw yet (never a reading, or no way to read one), so the reason takes the
     * card's place. Otherwise the card stays (its chart keeps the last trajectory; the renderer draws "No reading
     * since", the warm-up or "Sensor ended") and the reason sits under it, compact.
     */
    val fullPage: Boolean get() = false

    /** First-run setup hasn't chosen a role yet. */
    data object SetupIncomplete : NoDataReason {
        override val action = HomeAction.OpenSetup
        override val fullPage = true
    }

    /**
     * A role is set but no reading has come yet ("Not monitoring: no sensor yet"), or the wearer forgot the sensor
     * ([history]: older readings stay on the card's chart). No "no data" alarm is armed in this state, so the wording
     * is never an alarm's.
     */
    data class NoSensorYet(
        val follower: Boolean,
        val history: Boolean = false,
    ) : NoDataReason {
        override val action = if (follower) HomeAction.OpenSetup else HomeAction.PairSensor
        override val fullPage = !history
    }

    data object CoreMissing : NoDataReason {
        override val action: HomeAction? = null
        override val fullPage = true
    }

    data object BluetoothOff : NoDataReason {
        override val action = HomeAction.EnableBluetooth
    }

    /**
     * Another app took the sensor over (the official app, Juggluco: it keeps refusing Sukrara): close that app, then
     * take the sensor back with a scan (the new-sensor flow's takeover).
     */
    data object UsedByAnotherApp : NoDataReason {
        override val action = HomeAction.PairSensor
    }

    /** The saved pairing can't be used: scan the sensor with the phone (NFC) again; the sensor keeps running. */
    data object ScanNeeded : NoDataReason {
        override val action = HomeAction.PairSensor
    }

    /** A sensor found by a scan refused the pairing (wrong id, PIN not entered): pair it again. */
    data object PairAgain : NoDataReason {
        override val action = HomeAction.PairSensor
    }

    /** Android refused to start monitoring in the background. */
    data object MonitoringStopped : NoDataReason {
        override val action = HomeAction.ResumeMonitoring
    }

    data object SensorEnded : NoDataReason {
        override val action = HomeAction.PairSensor
    }

    data class WarmingUp(val readyAt: Instant) : NoDataReason {
        override val action: HomeAction? = null
    }

    /** The wearer removed this follower. */
    data object LinkRevoked : NoDataReason {
        override val action = HomeAction.OpenSetup
    }

    /** Follower: nothing heard from the wearer's phone since [since] (null = never). */
    data class WearerSilent(val since: Instant?) : NoDataReason {
        override val action: HomeAction? = null
    }

    /** Wearer: monitoring runs but the sensor sent nothing for 20 min (the card's "no readings" step). */
    data class NoReadings(val since: Instant?) : NoDataReason {
        override val action = HomeAction.OpenSensor
    }

    companion object {
        /**
         * The reason for [state], or null when the card has a value to show. The card's own decision [look]
         * ([homeLook]) decides "no value": a reason is only given when it hides the number (≥ 20 min or none), or when a
         * state makes any number wrong (setup, ended, warming up), so the panel and the card never disagree.
         */
        fun of(
            state: GlucoseState,
            look: GlucoseLook,
        ): NoDataReason? {
            val follower = state.role == Role.Follower
            val latest = state.latest
            val valueShown = look.shown
            val sensor = state.sensor
            val needed = (sensor as? SensorState.ActionNeeded)?.action?.reason()
            return when {
                state.notMonitoring == NotMonitoringReason.SetupIncomplete -> SetupIncomplete

                state.link?.revoked == true -> LinkRevoked

                state.sensor == SensorState.CoreMissing -> CoreMissing

                state.sensor == SensorState.Ended -> SensorEnded

                sensor is SensorState.WarmingUp && look.state == GlucoseLook.State.WARMING_UP -> WarmingUp(sensor.readyAt)

                valueShown -> null

                // the sensor was forgotten: nothing reconnects, so never "it reconnects on its own"
                latest != null && !follower && state.notMonitoring == NotMonitoringReason.NoSensorYet -> NoSensorYet(false, history = true)

                state.service is ServiceState.Stopped -> MonitoringStopped

                state.sensor == SensorState.BluetoothOff && !follower -> BluetoothOff

                latest == null && state.notMonitoring == NotMonitoringReason.NoSensorYet -> NoSensorYet(follower)

                // nothing comes until the person acts: say what to do, never "it reconnects on its own"
                !follower && needed != null -> needed

                follower -> WearerSilent(state.link?.lastHeardAt ?: latest?.t)

                else -> NoReadings((state.source as? SourceStatus.Interrupted)?.since ?: latest?.t)
            }
        }
    }
}

/** The no-data reason for what the sensor needs, or null: the others have their own place (permission, sign-in). */
private fun SensorAction.reason(): NoDataReason? =
    when (this) {
        SensorAction.TakeOver -> NoDataReason.UsedByAnotherApp
        SensorAction.ScanSensor -> NoDataReason.ScanNeeded
        SensorAction.PairAgain -> NoDataReason.PairAgain
        SensorAction.SignInLibreView, SensorAction.GrantBluetooth -> null
    }

/** Something the home screen asks the app to do (routed by `:app`; home never navigates by itself). */
sealed interface HomeAction {
    data object OpenSetup : HomeAction

    data object OpenSettings : HomeAction

    data object PairSensor : HomeAction

    data object OpenSensor : HomeAction

    data object EnableBluetooth : HomeAction

    data object ResumeMonitoring : HomeAction

    data object AddTreatment : HomeAction

    /** Add treatment with Carbs ([carbs]) or Rapid focused: the card's *Eat carbs* / *Take insulin* pill. */
    data class Treat(
        val carbs: Boolean,
    ) : HomeAction

    /** Settings › Alerts (the quiet sheet's way to the rules). */
    data object OpenAlertSettings : HomeAction

    /** Settings › Alert reliability: an item the person answers there (*Sound while the phone is unlocked*). */
    data object OpenAlertReliability : HomeAction

    data object AddWidget : HomeAction

    data object OpenBackup : HomeAction

    /** "How you appear" (the wearer's profile row). */
    data object OpenProfile : HomeAction

    /** Share with someone (a new invite). */
    data object Share : HomeAction

    /** The LibreView upload's page (the clinic banner's *LibreView*, an entry's cloud mark). */
    data object OpenClinicUpload : HomeAction

    /** The Tidepool upload's page (the clinic banner's *Tidepool*). */
    data object OpenTidepool : HomeAction

    /** NFC taps blocked by Android: its screen that allows them. */
    data object AllowTaps : HomeAction

    /**
     * NFC taps blocked by Android: turn off what is blocked ([taps]: pen reading, *Open Sukrara when you scan a
     * sensor*), the honest exit for someone who doesn't want taps.
     */
    data class TurnOffTaps(
        val taps: BlockedTaps,
    ) : HomeAction

    /** Sukrara's System UI module waits for a restart: restart System UI with root. */
    data object RestartSystemUi : HomeAction
}
