// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.debug

import android.content.Context
import androidx.core.content.edit
import app.mauri.sukrara.integrations.garmin.ConnectIqPort
import app.mauri.sukrara.integrations.garmin.ConnectIqStore
import app.mauri.sukrara.integrations.garmin.GarminApp
import app.mauri.sukrara.integrations.garmin.IqDevice
import app.mauri.sukrara.integrations.garmin.IqSend

/**
 * Debug builds: a made-up Garmin Connect for the emulator (#199: home's Garmin tip, setup's Garmin step, the Garmin
 * page), with one connected watch, "Forerunner 265", that has the Sukrara apps [apps] (registry keys; empty = none).
 * Every message "arrives" and goes nowhere. `GARMIN_FAKE --es apps aura,app,field|none|off` (off = the real Garmin
 * Connect again) and `--ez store true` (stand-in Connect IQ store ids, so the store links open listing URLs
 * instead of the store's search before the real listings exist: they lead nowhere). Saved, then the app process ends: open the app. Never in a release build (this
 * source set).
 */
class FakeGarminConnect(
    private val apps: Set<String>,
) : ConnectIqPort {
    private val ids =
        GarminApp.entries
            .filter { it.key in apps }
            .map { it.id }
            .toSet()

    override fun installed() = true

    override suspend fun devices(): List<IqDevice> = listOf(IqDevice(WATCH_ID, "Forerunner 265", true))

    override suspend fun appVersion(
        device: IqDevice,
        app: String,
    ): Int? = if (app in ids) 1 else null

    override suspend fun send(
        device: IqDevice,
        app: String,
        message: ByteArray,
    ): IqSend = if (app in ids) IqSend.Delivered else IqSend.Failed(IqSend.DURING_TRANSFER)

    companion object {
        private const val WATCH_ID = 26500L
        private const val PREFS = "debug_garmin_fake"

        /** The fake's apps, or null = the real Garmin Connect (or the simulator). */
        fun apps(context: Context): Set<String>? =
            context
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("apps", null)
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() && it != "none" }
                ?.toSet()

        /** Stand-in store ids for every app (`--ez store true`), or null: the registry's (none yet). */
        fun store(context: Context): ConnectIqStore? =
            if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("store", false)) {
                ConnectIqStore(GarminApp.entries.associateWith { "debug-${it.key}" })
            } else {
                null
            }

        fun set(
            context: Context,
            apps: String?,
            store: Boolean,
        ) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit(commit = true) {
            if (apps == null || apps == "off") remove("apps") else putString("apps", apps)
            putBoolean("store", store)
        }
    }
}
