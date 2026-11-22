// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.home

import app.mauri.sukrara.core.model.GlucoseState
import app.mauri.sukrara.core.model.NotMonitoringReason
import app.mauri.sukrara.core.model.SensorAction
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.ServiceState
import app.mauri.sukrara.core.model.SourceKind
import app.mauri.sukrara.core.model.SourceProblem
import app.mauri.sukrara.core.model.SourceStatus
import app.mauri.sukrara.feature.home.banners.BlockedTaps
import app.mauri.sukrara.graph.GlucoseLook
import app.mauri.sukrara.graph.SensorRenew
import java.time.Duration
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

    /**
     * The sensor ended: a new one is paired here, or ([renew]) started in the app that reads it for this source (the
     * Libre app, Juggluco, a site's uploader), so the way out is Settings › Source, not *Pair new sensor*; a site's
     * sensor opens Settings › Sensor, where its new start is set when the site doesn't say it (docs/44 decision 10).
     */
    data class SensorEnded(
        val renew: SensorRenew = SensorRenew.HERE,
    ) : NoDataReason {
        override val action =
            when (renew) {
                SensorRenew.HERE -> HomeAction.PairSensor
                SensorRenew.ITS_APP -> HomeAction.OpenSensor
                else -> HomeAction.OpenSource
            }
    }

    /**
     * The phone's clock is behind the readings by [ahead], so the new ones are dropped (#50): they'd look fresh for too
     * long. Set the date and time right (automatic); the next reading clears it.
     */
    data class ClockBehind(val ahead: Duration) : NoDataReason {
        override val action = HomeAction.OpenDateSettings
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

    /**
     * An online source brings nothing and says why ([problem]: signed out, unreachable, offline, nothing new, asked to
     * wait): Settings › Source has the site, its state and the way out (docs/44 "Living with two apps").
     */
    data class SourceTrouble(
        val problem: SourceProblem,
        val since: Instant?,
        /** Which online source (its words: a site's uploader, or the Libre app behind LibreLinkUp). */
        val kind: SourceKind = SourceKind.Nightscout,
        /** LibreView's maintenance end, when Abbott's status page says it ([SourceProblem.Maintenance]). */
        val until: Instant? = null,
        /**
         * LibreView's maintenance: *Read the sensor in Sukrara* may be offered as a quiet second way out ([MoveRules]:
         * your glucose, a tested family, no loop or pump app holding the CGM).
         */
        val move: Boolean = false,
    ) : NoDataReason {
        override val action = HomeAction.OpenSource

        /** An expected wait (LibreView's maintenance): the way out is quiet, never in an error's look. */
        val waitOnly: Boolean get() = problem == SourceProblem.Maintenance
    }

    /** An online source that isn't reading: no site connected yet, or its first call is on its way (Settings › Source). */
    data object SiteNotConnected : NoDataReason {
        override val action = HomeAction.OpenSource
    }

    /**
     * Juggluco on this phone is the source and nothing comes from it: [problem] says why when it is known (not
     * installed, its web server doesn't answer, it refuses the API secret), else it has simply sent nothing new since
     * [since]. Never the sensor's "keep the phone near the sensor": Sukrara doesn't talk to the sensor here. Settings ›
     * Source has *Open Juggluco*, the secret and the way to another source.
     */
    data class PhoneAppSilent(
        val problem: PhoneAppProblem?,
        val since: Instant?,
    ) : NoDataReason {
        override val action = HomeAction.OpenSource
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
            /** Why Juggluco on this phone sends nothing, when it is the source and that is known. */
            phoneApp: PhoneAppProblem? = null,
            /** Reading the sensor in Sukrara may be offered ([MoveRules], the app's): LibreView maintenance's second way out. */
            moveOffered: Boolean = false,
        ): NoDataReason? {
            val follower = state.spec?.stream == true
            val latest = state.latest
            val valueShown = look.shown
            val sensor = state.sensor
            val needed = (sensor as? SensorState.ActionNeeded)?.action?.reason()
            val clock = state.clockBehind
            return when {
                state.notMonitoring == NotMonitoringReason.SetupIncomplete -> {
                    SetupIncomplete
                }

                // only while the stream is the source: another source's values never hide behind an ended follow
                follower && state.link?.revoked == true -> {
                    LinkRevoked
                }

                state.sensor == SensorState.CoreMissing -> {
                    CoreMissing
                }

                state.sensor == SensorState.Ended -> {
                    SensorEnded(SensorRenew.of(state.spec))
                }

                // even while the last value shows: every new reading is being dropped, and its age reads wrong
                clock != null -> {
                    ClockBehind(clock.ahead)
                }

                sensor is SensorState.WarmingUp && look.state == GlucoseLook.State.WARMING_UP -> {
                    WarmingUp(sensor.readyAt)
                }

                valueShown -> {
                    null
                }

                // Juggluco on this phone: its reason and way out, never the sensor's (Sukrara doesn't read one here)
                state.spec?.kind == SourceKind.PhoneApp && state.service !is ServiceState.Stopped -> {
                    PhoneAppSilent(phoneApp, latest?.t)
                }

                // the sensor was forgotten: nothing reconnects, so never "it reconnects on its own"
                latest != null && !follower && state.notMonitoring == NotMonitoringReason.NoSensorYet -> {
                    NoSensorYet(false, history = true)
                }

                state.service is ServiceState.Stopped -> {
                    MonitoringStopped
                }

                state.sensor == SensorState.BluetoothOff && !follower -> {
                    BluetoothOff
                }

                latest == null && state.notMonitoring == NotMonitoringReason.NoSensorYet -> {
                    NoSensorYet(follower)
                }

                // nothing comes until the person acts: say what to do, never "it reconnects on its own"
                !follower && needed != null -> {
                    needed
                }

                follower -> {
                    WearerSilent(state.link?.lastHeardAt ?: latest?.t)
                }

                // an online source knows why nothing comes: say it, with the way to fix it
                (state.source as? SourceStatus.Interrupted)?.problem != null -> {
                    val s = state.source as SourceStatus.Interrupted
                    val maintenance = s.problem == SourceProblem.Maintenance
                    SourceTrouble(
                        s.problem!!,
                        latest?.t,
                        state.spec?.kind ?: SourceKind.Nightscout,
                        until = s.until.takeIf { maintenance },
                        move = maintenance && moveOffered,
                    )
                }

                // an online source without a known problem: its page, not the sensor's
                state.spec?.online == true -> {
                    SourceTrouble(SourceProblem.NoNewReadings, latest?.t, state.spec?.kind ?: SourceKind.Nightscout)
                }

                else -> {
                    NoReadings((state.source as? SourceStatus.Interrupted)?.since ?: latest?.t)
                }
            }
        }
    }
}

/** Why Juggluco on this phone sends nothing (home's no-data reason). */
enum class PhoneAppProblem {
    /** Not installed on this phone (any more). */
    Missing,

    /** Its web server doesn't answer: Juggluco isn't running or the server is off. */
    Unreachable,

    /** It refuses Sukrara: the API secrets differ. */
    Refused,
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

    /** Settings › Source (an online source's site, state and sign-in). */
    data object OpenSource : HomeAction

    data object EnableBluetooth : HomeAction

    /** Android's date and time settings (the phone's clock is behind). */
    data object OpenDateSettings : HomeAction

    data object ResumeMonitoring : HomeAction

    data object AddTreatment : HomeAction

    /** A row of the treatment list: the treatment dialog edits that entry (amount, time, note). */
    data class EditTreatment(
        val id: String,
    ) : HomeAction

    /** The Treatments page: the last 31 days of entries (the list's *See all*, #207). */
    data object OpenTreatments : HomeAction

    /** Settings › Alerts (the quiet sheet's way to the rules). */
    data object OpenAlertSettings : HomeAction

    /** Settings › Alerts › Night alerts (home's night line, #38). */
    data object OpenNightAlerts : HomeAction

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

    /** *Turn on* on the updates suggestion: the GitHub version's daily update check goes on. */
    data object TurnOnUpdates : HomeAction

    /** *Turn on* on the Garmin tip: *Send to Sukrara on your watch* goes on. */
    data object TurnOnGarmin : HomeAction

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

    /** Settings › Insulin pens › this pen (*Pen read 2d ago* on the treatments' title). */
    data class OpenPen(
        val serial: String,
    ) : HomeAction
}
