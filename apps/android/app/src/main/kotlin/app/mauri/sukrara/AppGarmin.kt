// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import app.mauri.sukrara.feature.home.banners.GarminTip
import app.mauri.sukrara.feature.sharing.SetupWatchApp
import app.mauri.sukrara.integrations.garmin.GarminApp
import app.mauri.sukrara.integrations.garmin.GarminLinkState
import app.mauri.sukrara.integrations.garmin.iconRes
import app.mauri.sukrara.integrations.garmin.inlineNameRes
import app.mauri.sukrara.integrations.garmin.nameRes
import app.mauri.sukrara.integrations.garmin.whatRes

/**
 * What home and setup read from Sukrara's Garmin link (#199): the link knows which of Sukrara's apps each
 * watch has with the switch off too (`GarminLink.detect`, at start and when a watch connects).
 */
internal object AppGarmin {
    /**
     * Home's Garmin tip: one of Sukrara's apps is on a watch while *Send to Sukrara on your watch* is off; null while
     * sending is on, or none is known (no Garmin Connect, no watch connected since the start, none installed).
     */
    fun tip(s: GarminLinkState): GarminTip? {
        if (s.on) return null
        val watch = s.foundOn ?: return null
        val found = s.found
        return GarminTip(watch.name, found.map { it.key }, found.singleOrNull()?.inlineNameRes)
    }

    /** Setup's Garmin step is offered: Garmin Connect is on this phone, or a watch has one of Sukrara's apps. */
    fun offered(s: GarminLinkState): Boolean = s.garminConnect || s.found.isNotEmpty()

    /** Setup's Garmin step's apps: each with what it gives, its Connect IQ store listing and whether a watch has it. */
    fun apps(s: GarminLinkState): List<SetupWatchApp> =
        GarminApp.entries.map { app ->
            SetupWatchApp(
                app.key,
                app.nameRes,
                app.whatRes,
                app.iconRes,
                url = app::storeUrl,
                onWatch = app in s.found,
            )
        }
}
