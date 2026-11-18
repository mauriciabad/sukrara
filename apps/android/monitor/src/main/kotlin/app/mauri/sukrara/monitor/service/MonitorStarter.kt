// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.monitor.service

import app.mauri.sukrara.core.model.ServiceState
import app.mauri.sukrara.core.model.SourceSpec
import app.mauri.sukrara.core.model.StopReason
import app.mauri.sukrara.monitor.hub.GlucoseHubImpl

/** Why the service is being started (logs, and which paths may start it). */
enum class StartReason { App, Boot, LockedBoot, PackageReplaced, TimeChanged, HealthCheck, AlertCheck, Resume }

/** What the source has to read now (the starter's check: cheap, it runs on every start and health check). */
enum class Readiness {
    /** Something to read: the service runs. */
    Ready,

    /** Nothing before the first unlock (a Sukrara stream, Juggluco): the service waits; monitoring stays as it was. */
    AfterUnlock,

    /**
     * Nothing set up to read (no sensor paired, not following anyone): nothing comes until the person sets a source up,
     * so this phone isn't monitoring.
     */
    Nothing,
}

enum class StartResult {
    Started,

    /** Setup isn't done or nothing is set up to monitor: no service, no "stopped" notice. */
    NotNeeded,

    /** Android refused: the hub says Stopped and the person sees "Monitoring stopped — tap to resume". */
    Denied,
}

/** Starts the foreground service; [launch] returns false when Android refused it. */
fun interface ServiceLauncher {
    fun launch(): Boolean
}

/** The "Monitoring stopped — tap to resume" notice (channel `status`). */
interface StoppedNotice {
    fun show()

    fun cancel()
}

/**
 * The only way the monitoring service is started (app open, boot, update, time change, health check, the alert check
 * of the alert check, the notice's tap). Starting a running service is harmless, so every path just calls [start].
 * A refusal is never swallowed: the hub shows Stopped and the notice offers to resume.
 */
class MonitorStarter(
    private val hub: GlucoseHubImpl,
    /** What the source has to read now. */
    private val readiness: suspend (SourceSpec) -> Readiness,
    private val launcher: ServiceLauncher,
    private val notice: StoppedNotice,
    private val log: (String) -> Unit = {},
) {
    suspend fun start(reason: StartReason): StartResult {
        val now = readinessNow()
        if (now != Readiness.Ready) {
            // nothing to resume any more: an earlier "stopped" goes away
            if (hub.state.value.service is ServiceState.Stopped) hub.setService(ServiceState.NotRunning)
            notice.cancel()
            settle(now)
            log("start ($reason): ${StartResult.NotNeeded} (${now ?: "no source"})")
            return StartResult.NotNeeded
        }
        return if (launcher.launch()) {
            notice.cancel()
            StartResult.Started
        } else {
            denied()
            StartResult.Denied
        }.also { log("start ($reason): $it") }
    }

    /**
     * The running service found nothing to read after a source change (the sensor chosen with none paired) and stops:
     * the same as a start that finds it ([settle]), without waiting for the next start.
     */
    suspend fun nothingToRead() = settle(readinessNow())

    private suspend fun readinessNow(): Readiness? =
        hub.state.value.spec
            ?.let { readiness(it) }

    /**
     * Nothing set up to read (the sensor forgotten, the sensor chosen before one is paired) is not monitoring, as after
     * *Forget this sensor*: home says so (*Pair sensor*, never "it reconnects on its own") and no "no data" alarm waits
     * for a reading that can't come; readings still on their way can't arm it again.
     */
    private suspend fun settle(now: Readiness?) {
        if (now == Readiness.Nothing && hub.state.value.monitoringStartedAt != null) hub.resetMonitoring(fence = true)
    }

    /** The service itself couldn't go to the foreground (or Android refused the start). */
    fun denied() {
        hub.setService(ServiceState.Stopped(StopReason.StartDenied))
        notice.show()
    }
}
