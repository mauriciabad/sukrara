// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.model

import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

/**
 * Everything the app knows about glucose right now, from the one source of truth ([GlucoseHub]). UI, widgets, the
 * live notification, alerts, sharing and uploads all read this; nothing keeps its own copy.
 */
data class GlucoseState(
    /** Null until first-run setup chose a role. */
    val role: Role?,
    val latest: Reading?,
    /** The recent window, oldest first. */
    val recent: List<Reading>,
    val sensor: SensorState,
    val source: SourceStatus,
    val alerts: AlertUiState,
    /** The follower's link to the wearer; null on a wearer phone. */
    val link: LinkState?,
    /**
     * When monitoring started: the first reading from the current source (persisted; cleared only when the person
     * removes the sensor or changes the role). Null = not monitoring yet, so no "no data" alarm can be armed.
     */
    val monitoringStartedAt: Instant? = null,
    /** Whether the monitoring service runs. */
    val service: ServiceState = ServiceState.NotRunning,
) {
    /** Why nothing is monitored yet ("Not monitoring: no sensor yet"), or null while monitoring. */
    val notMonitoring: NotMonitoringReason?
        get() =
            when {
                role == null -> NotMonitoringReason.SetupIncomplete
                monitoringStartedAt == null -> NotMonitoringReason.NoSensorYet
                else -> null
            }

    companion object {
        /** Before anything is known: nothing is shown as a value and no data alarm can fire. */
        val Initial =
            GlucoseState(
                role = null,
                latest = null,
                recent = emptyList(),
                sensor = SensorState.None,
                source = SourceStatus.NotStarted,
                alerts = AlertUiState(),
                link = null,
            )
    }
}

/** The wearer's sensor as the person needs to see it. */
sealed interface SensorState {
    /** No sensor set up yet ("Not monitoring: no sensor yet"). */
    data object None : SensorState

    data class WarmingUp(val readyAt: Instant) : SensorState

    data class Active(val endsAt: Instant?) : SensorState

    data object Ended : SensorState

    /** This build has no Libre 3 native core (a debug build made without Juggluco). */
    data object CoreMissing : SensorState

    data object BluetoothOff : SensorState

    /** The sensor can't be read until the person does something (the UI words live in the new-sensor flow and Settings › Sensor). */
    data class ActionNeeded(val action: SensorAction) : SensorState
}

/** What the person has to do so the sensor can be read again. */
enum class SensorAction {
    /** Scan the sensor with the phone (NFC): a new sensor, a lost or unreadable pairing, or taking it over. */
    ScanSensor,

    /** Sign in to LibreView so Sukrara can start / take over the sensor with the account's receiver ID. */
    SignInLibreView,

    /** Grant the Nearby devices (Bluetooth) permission. */
    GrantBluetooth,
}

/** Why the app isn't monitoring yet. Shown as "Not monitoring: no sensor yet" with the action "Set up sensor". */
enum class NotMonitoringReason {
    /** First-run setup hasn't chosen a role. */
    SetupIncomplete,

    /** A role is set but no reading has come from a source yet (no sensor paired / not joined, or waiting). */
    NoSensorYet,
}

/** The monitoring foreground service, as the person needs to see it. */
sealed interface ServiceState {
    data object Running : ServiceState

    /** Not started (yet), or nothing to monitor. */
    data object NotRunning : ServiceState

    /** Android refused or ended it; shown as "Monitoring stopped — tap to resume". */
    data class Stopped(val reason: StopReason) : ServiceState
}

enum class StopReason {
    /** Android didn't allow starting it from the background (ForegroundServiceStartNotAllowedException). */
    StartDenied,
}

/** Whether readings are arriving from the current source. */
sealed interface SourceStatus {
    /** Monitoring hasn't started (no alarm about missing data in this state). */
    data object NotStarted : SourceStatus

    data object Connecting : SourceStatus

    data class Live(val lastReadingAt: Instant) : SourceStatus

    data class Interrupted(val since: Instant) : SourceStatus
}

/**
 * What the alert runtime shows: which alert kinds are firing (contract kind ids), *Mute all* ([mutedUntil] null = off,
 * [UNTIL_TURNED_OFF] = until the person turns it off) and the sides that are quiet or remind less ([quiet]), each with
 * its way out ([AlertControls]), the badge's answer for the card ([status], every card surface draws it) and how long
 * each treatment quiets alerts ([treatmentQuiet]: the cards' mute lines and last-treatment ring).
 */
data class AlertUiState(
    val firingKinds: Set<String> = emptySet(),
    val status: CardStatus = CardStatus.None,
    val treatmentQuiet: TreatmentQuiet = TreatmentQuiet(),
    val mutedUntil: Instant? = null,
    val quiet: List<AlertQuietSide> = emptyList(),
    /**
     * The runtime's steps that failed on their last attempt (`Notify:level`, `Alarm`, `Save`…; the diagnostic log has
     * the exception class), each until it works again. The alert checklist ([AlertReadiness]) reads it: home shows it
     * as [AlertRequirement.AlertDelivery].
     */
    val failedSteps: Set<String> = emptySet(),
    /** What alerted and what the person did lately on this phone, newest first (Settings › Alerts › Lately). */
    val lately: List<AlertNote> = emptyList(),
) {
    val mutedUntilTurnedOff: Boolean get() = mutedUntil == UNTIL_TURNED_OFF

    companion object {
        /** *Mute all* with no end. */
        val UNTIL_TURNED_OFF: Instant = Instant.ofEpochSecond(Long.MAX_VALUE / 1000)
    }
}

/**
 * How long a treatment quiets the alerts it answers, as the alert engine runs it now (on a follower mirroring the
 * wearer, the wearer's rules): [rapidS] after rapid insulin, [carbsS] after carbs, in seconds; null = that rule is off,
 * or no engine runs. Every card draws its mute lines and last-treatment ring from it, so they end where the engine's
 * quiet does (contracts/alerts/SPEC.md §2).
 */
data class TreatmentQuiet(
    val rapidS: Long? = null,
    val carbsS: Long? = null,
) {
    /** No treatment quiets anything. */
    val none: Boolean get() = rapidS == null && carbsS == null

    /** When [t]'s quiet ends (epoch s), or null: its kind quiets nothing (long-acting, a note, a rule that's off). */
    fun until(t: Treatment): Long? =
        when (t.kind) {
            TreatmentKind.RapidInsulin -> rapidS
            TreatmentKind.Carbs -> carbsS
            else -> null
        }?.let { t.at.epochSecond + it }
}

/**
 * One line of the alerts' recent history: [kind] (an alert kind's wire name, null for Mute all) alerted at [mgdl],
 * cleared, or the person muted it for [minutes] (null = until turned off)… [remote]: the wearer's alert, mirrored.
 */
data class AlertNote(
    val at: Instant,
    val type: AlertNoteType,
    val kind: String? = null,
    val mgdl: Int? = null,
    val minutes: Int? = null,
    val remote: Boolean = false,
)

/** [AllBack]: a timed Mute all ended by itself; [Resumed]: an alert shown again without sound after a restart. */
enum class AlertNoteType { Fired, Cleared, Muted, Unmuted, RemindLess, RemindAsUsual, Back, AllMuted, AllUnmuted, AllBack, Resumed }

/** Why a side is quiet (contracts/alerts/SPEC.md §2 "States and exits"). */
enum class AlertQuietReason {
    /** Muted from a notification or the app ("Unmute", "Change time"). */
    Muted,

    /** Carbs logged in the window ("Alert me anyway"). */
    AfterCarbs,

    /** Rapid insulin logged in the window ("Alert me anyway"). */
    AfterInsulin,

    /** A high falling back into range ("Alert me anyway"). */
    FallingBack,
}

/**
 * One side the person can act on now. [kind] = the episode's kind (contract id; the side's main kind without one);
 * [matching] = it still matches; [reason] null = not quiet but reminding less every [relaxMin] minutes; [until] the
 * quiet's end (null = no fixed end); [canRemindLess] = *Remind less* would slow it down.
 */
data class AlertQuietSide(
    val kind: String,
    val matching: Boolean,
    val reason: AlertQuietReason?,
    val until: Instant?,
    val relaxMin: Int,
    val canRemindLess: Boolean,
)

/** A follower's link to the wearer it follows. */
data class LinkState(
    val wearerName: String?,
    val lastHeardAt: Instant?,
    val revoked: Boolean = false,
)

/**
 * The single glucose state source. Exactly one input per role feeds it (wearer: the sensor source; follower: the
 * follower engine). Implemented in `:monitor`.
 */
interface GlucoseHub {
    val state: StateFlow<GlucoseState>

    suspend fun ingest(
        readings: List<Reading>,
        from: SourceId,
    )

    fun setSource(status: SourceStatus)

    /** The sensor as shown (a follower mirrors the wearer's). */
    fun setSensor(sensor: SensorState)

    /** The alert runtime's summary (firing kinds, mute) for every surface. */
    fun setAlerts(alerts: AlertUiState)

    /** The follower's link to the wearer. */
    fun setLink(link: LinkState?)
}
