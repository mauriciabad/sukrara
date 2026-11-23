// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import app.mauri.sukrara.integrations.R
import app.mauri.sukrara.integrations.garmin.AppLink
import app.mauri.sukrara.integrations.garmin.GarminApp
import app.mauri.sukrara.integrations.garmin.GarminLinkState
import app.mauri.sukrara.integrations.garmin.GarminWatch
import app.mauri.sukrara.integrations.garmin.LinkReport
import app.mauri.sukrara.integrations.garmin.WatchPayload
import app.mauri.sukrara.integrations.garmin.WatchProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import app.mauri.sukrara.core.designsystem.R as DsR

/** #199: what home's Garmin tip and setup's Garmin step read from the Garmin link. */
class AppGarminTest {
    private fun watch(
        name: String,
        vararg apps: GarminApp,
    ) = GarminWatch.of(
        name.length.toLong(),
        name,
        true,
        GarminApp.entries.flatMap { it.ids }.associateWith { id -> AppLink(installed = apps.any { it.id == id }) },
    )

    @Test
    fun theTipNamesTheWatchAndTheAppsOnlyWhileSendingIsOff() {
        val off =
            GarminLinkState(on = false, garminConnect = true, watches = listOf(watch("Venu 3"), watch("Forerunner 265", GarminApp.Aura)))
        val tip = AppGarmin.tip(off)!!
        assertEquals("Forerunner 265", tip.watch)
        assertEquals(listOf("aura"), tip.apps)
        assertEquals(R.string.in_garmin_aura_inline, tip.name)
        assertEquals(listOf("GarminOff:aura"), tip.keys)
        // two apps: no single name
        val two = off.copy(watches = listOf(watch("Forerunner 265", GarminApp.Aura, GarminApp.Field)))
        assertNull(AppGarmin.tip(two)!!.name)
        assertEquals(listOf("aura", "field"), AppGarmin.tip(two)!!.apps)
        // sending on, or no Sukrara app on any watch, or nothing known yet: none
        assertNull(AppGarmin.tip(off.copy(on = true)))
        assertNull(AppGarmin.tip(off.copy(watches = listOf(watch("Venu 3")))))
        assertNull(AppGarmin.tip(GarminLinkState(on = false)))
    }

    @Test
    fun theUpdateBannerNamesTheOlderSide() {
        val on = GarminLinkState(garminConnect = true, watches = listOf(watch("Descent", GarminApp.Aura)))

        fun reading(version: Int): GarminLinkState {
            val r = LinkReport(Instant.EPOCH, emptyList(), null, null, version)
            return on.copy(report = r, reports = mapOf(WatchProfile.Face to r))
        }
        // the face reads an older payload: update it; a newer one: update Sukrara
        val older = AppGarmin.update(reading(WatchPayload.VERSION - 1))!!
        assertEquals("Descent", older.watch)
        assertEquals("aura", older.app)
        assertEquals(R.string.in_garmin_aura_inline, older.name)
        assertFalse(older.newer)
        assertTrue(AppGarmin.update(reading(WatchPayload.VERSION + 1))!!.newer)
        // the same version, no report, or sending off: none
        assertNull(AppGarmin.update(reading(WatchPayload.VERSION)))
        assertNull(AppGarmin.update(on))
        assertNull(AppGarmin.update(reading(WatchPayload.VERSION - 1).copy(on = false)))
    }

    @Test
    fun theSetupStepIsOfferedWithGarminConnectOrAnAppOnAWatch() {
        assertFalse(AppGarmin.offered(GarminLinkState()))
        assertTrue(AppGarmin.offered(GarminLinkState(garminConnect = true)))
        assertTrue(AppGarmin.offered(GarminLinkState(watches = listOf(watch("Venu 3", GarminApp.App)))))
    }

    @Test
    fun theStepsAppsOpenTheirListing() {
        val s = GarminLinkState(garminConnect = true, watches = listOf(watch("Venu 3", GarminApp.App)))
        val apps = AppGarmin.apps(s)
        assertEquals(GarminApp.entries.map { it.key }, apps.map { it.key })
        assertEquals(listOf("app"), apps.filter { it.onWatch }.map { it.key })
        // its listing, in the phone's language
        assertEquals(GarminApp.entries.map { it.storeUrl("es") }, apps.map { it.url("es") })
        assertEquals("https://apps.garmin.com/es-ES/apps/2859fe48-7e9e-4e52-ae2a-a319fa76fcfc", apps.first { it.key == "aura" }.url("es"))
        // each with its kind's Connect IQ icon
        assertEquals(
            listOf(DsR.drawable.ds_ic_watch_face, DsR.drawable.ds_ic_device_app, DsR.drawable.ds_ic_data_field),
            apps.map { it.icon },
        )
    }
}
