// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.spike.cdmprobe

import android.app.ActivityManager
import android.app.ForegroundServiceStartNotAllowedException
import android.companion.AssociationInfo
import android.companion.CompanionDeviceManager
import android.companion.DevicePresenceEvent
import android.companion.ObservingDevicePresenceRequest
import android.content.Context
import android.content.Intent
import android.os.ParcelUuid
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Everything the probe does, shared by the activity (associate), the receiver (adb) and the presence service. */
object Probe {
    private const val TAG = "CdmProbe"
    private val time = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    /** One log line: logcat (tag CdmProbe) and files/probe.log, with the pid so a process restart shows. */
    fun log(
        context: Context,
        line: String,
    ) {
        val full = "${LocalTime.now().format(time)} pid=${Process.myPid()} up=${SystemClock.elapsedRealtime() / 1000}s $line"
        Log.i(TAG, full)
        File(context.filesDir, "probe.log").appendText(full + "\n")
    }

    fun readLog(context: Context): String = File(context.filesDir, "probe.log").takeIf { it.exists() }?.readText().orEmpty()

    fun cdm(context: Context): CompanionDeviceManager = context.getSystemService(CompanionDeviceManager::class.java)

    fun describe(a: AssociationInfo): String =
        "id=${a.id} mac=${a.deviceMacAddress} name=${a.displayName} profile=${a.deviceProfile} " +
            "selfManaged=${a.isSelfManaged}"

    fun list(context: Context) {
        val all = cdm(context).myAssociations
        log(context, "associations: ${all.size}" + all.joinToString("") { "\n  " + describe(it) })
    }

    /** API 36: observe by association id (the device's address) or by a service UUID. */
    fun observe(
        context: Context,
        id: Int?,
        uuid: String?,
    ) {
        val request =
            ObservingDevicePresenceRequest
                .Builder()
                .apply {
                    if (id != null) setAssociationId(id)
                    if (uuid != null) setUuid(ParcelUuid.fromString(uuid))
                }.build()
        runCatching { cdm(context).startObservingDevicePresence(request) }
            .onSuccess { log(context, "observe id=$id uuid=$uuid: ok") }
            .onFailure { log(context, "observe id=$id uuid=$uuid: ${it.javaClass.simpleName} ${it.message}") }
    }

    fun unobserve(
        context: Context,
        id: Int?,
        uuid: String?,
    ) {
        val request =
            ObservingDevicePresenceRequest
                .Builder()
                .apply {
                    if (id != null) setAssociationId(id)
                    if (uuid != null) setUuid(ParcelUuid.fromString(uuid))
                }.build()
        runCatching { cdm(context).stopObservingDevicePresence(request) }
            .onSuccess { log(context, "unobserve id=$id uuid=$uuid: ok") }
            .onFailure { log(context, "unobserve id=$id uuid=$uuid: ${it.javaClass.simpleName} ${it.message}") }
    }

    fun disassociate(
        context: Context,
        id: Int,
    ) {
        runCatching { cdm(context).disassociate(id) }
            .onSuccess { log(context, "disassociate $id: ok") }
            .onFailure { log(context, "disassociate $id: ${it.javaClass.simpleName} ${it.message}") }
    }

    /** The name of a DevicePresenceEvent.EVENT_* constant (read from the class, so new ones show by name). */
    fun eventName(event: Int): String =
        DevicePresenceEvent::class.java.fields
            .firstOrNull { it.name.startsWith("EVENT_") && it.type == Int::class.javaPrimitiveType && it.getInt(null) == event }
            ?.name ?: "EVENT_$event"

    /** How Android ranks this process right now, and whether Doze / battery limits apply to it. */
    fun standing(context: Context): String {
        val am = context.getSystemService(ActivityManager::class.java)
        val me = am.runningAppProcesses?.firstOrNull { it.pid == Process.myPid() }
        val pm = context.getSystemService(PowerManager::class.java)
        return "importance=${me?.importance} ignoringBatteryOptimizations=${pm.isIgnoringBatteryOptimizations(context.packageName)} " +
            "bgRestricted=${am.isBackgroundRestricted}"
    }

    /** Tries to start the foreground service now, from wherever the caller is (background in the tests). */
    fun startLink(
        context: Context,
        why: String,
    ) {
        runCatching { context.startForegroundService(Intent(context, LinkService::class.java).putExtra("why", why)) }
            .onSuccess { log(context, "fgs start ($why): allowed") }
            .onFailure {
                val kind = if (it is ForegroundServiceStartNotAllowedException) "DENIED" else it.javaClass.simpleName
                log(context, "fgs start ($why): $kind ${it.message}")
            }
    }

    fun stopLink(context: Context) {
        context.stopService(Intent(context, LinkService::class.java))
    }
}
