// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.core.model

import java.time.Instant

/**
 * One LibreView maintenance window of the LibreLinkUp account's region (contracts/sources/librelinkup/status.json,
 * docs/45): [start] to [end]; [live] = Abbott marks it in progress.
 */
data class MaintenanceWindow(
    val start: Instant,
    val end: Instant,
    val live: Boolean = false,
)

/**
 * docs/44 "The move": when Sukrara may offer to read the sensor itself instead of an online source. THE never-rules
 * every offer shares (LibreView maintenance's quiet *Read the sensor in Sukrara* on home today, the move suggestion of
 * step 4 later), so they never disagree: your own glucose, a sensor family Sukrara has tested directly, and no loop or
 * pump app holding the CGM (a takeover would cut it off from the pump). Never on the Mac (no phone app is the point).
 */
object MoveRules {
    /**
     * Loop and pump apps that may hold the CGM (docs/44: AAPS, CamAPS FX, Omnipod 5, Tandem, CareLink's MiniMed
     * Mobile), their Play Store ids: one installed = no offer.
     */
    val LOOP_APPS: List<String> =
        listOf(
            "info.nightscout.androidaps",
            "com.camdiab.fx_alert.mgdl",
            "com.camdiab.fx_alert.mmoll",
            "com.camdiab.fx_alert.mmoll.ca",
            "com.insulet.myblue.pdm",
            "com.tandemdiabetes.tconnect",
            "com.tandemdiabetes.mobi",
            "com.medtronic.diabetes.minimedmobile.eu",
            "com.medtronic.diabetes.minimedmobile.us",
        )

    /**
     * LibreLinkUp's sensor product type `pt` of a family Sukrara has tested directly (docs/44: today Libre 3 / 3 Plus,
     * `pt` 4; 3 is a Libre 2 / 2 Plus); unknown = no offer.
     */
    fun testedFamily(pt: Int?): Boolean = pt == 4

    /** Whether reading the sensor in Sukrara may be offered: your glucose, a tested family, no loop or pump app. */
    fun mayOffer(
        yours: Boolean,
        testedFamily: Boolean,
        loopApp: Boolean,
    ): Boolean = yours && testedFamily && !loopApp
}
