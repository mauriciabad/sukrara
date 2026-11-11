// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.designsystem.motion.ScreenMotion
import app.mauri.sukrara.feature.settings.SettingsRoute
import app.mauri.sukrara.feature.sharing.setup.SetupStep
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which motion each of the app's screens gets (docs/FEATURES.md "Screen motion"): setup fades through over the app,
 * every other screen is a page opened from another one (the Settings app's slide and predictive back).
 */
class ScreenMotionTest {
    @Test fun `setup fades through, wherever it's opened`() {
        assertEquals(ScreenMotion.FadeThrough, screenMotion(SetupRoute))
        SetupStep.entries.forEach { assertEquals(it.name, ScreenMotion.FadeThrough, screenMotion(SetupStepRoute(it))) }
    }

    @Test fun `every other screen is a page`() {
        val pages =
            listOf(
                HomeRoute,
                SharingRoute,
                ShareRoute,
                InviteHereRoute("https://sukrara.mauri.app/join#x"),
                ProfileRoute,
                BackupRoute,
                ReplaceIdentityRoute,
                DataProblemsRoute,
                AppsRoute,
                NightscoutUploadRoute,
                ExportRoute,
                ImportRoute,
                ExportAllRoute,
                LibreViewRoute,
                TidepoolRoute,
                NightscoutApiRoute,
                BroadcastsRoute,
                HealthConnectRoute,
                NewSensorKey,
                LibreViewSignInKey,
                SensorSettingsKey,
            )
        pages.forEach { assertEquals(it.toString(), ScreenMotion.Page, screenMotion(it)) }
    }

    @Test fun `every settings page is a page`() {
        val routes =
            listOf(
                SettingsRoute.Overview,
                SettingsRoute.Alerts,
                SettingsRoute.AlertKindPage(AlertKind.LOW),
                SettingsRoute.NightAlerts,
                SettingsRoute.NightKindPage(AlertKind.VERY_LOW),
                SettingsRoute.Ranges,
                SettingsRoute.Graph,
                SettingsRoute.GraphMarks,
                SettingsRoute.LockScreen,
                SettingsRoute.Pens,
                SettingsRoute.Pen("ABC123"),
                SettingsRoute.PenDoses("ABC123", review = true),
                SettingsRoute.Reliability,
                SettingsRoute.StatusBar,
                SettingsRoute.SensorEnd,
                SettingsRoute.About,
                SettingsRoute.ThresholdReview,
                SettingsRoute.Advanced,
                SettingsRoute.Log,
                SettingsRoute.SettingsFrom,
            )
        routes.forEach { assertEquals(it.toString(), ScreenMotion.Page, screenMotion(SettingsKey.of(it))) }
    }
}
