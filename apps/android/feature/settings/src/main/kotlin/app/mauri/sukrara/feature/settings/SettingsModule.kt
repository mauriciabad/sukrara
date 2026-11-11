// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import app.mauri.sukrara.core.model.AlertControls
import app.mauri.sukrara.core.model.AlertReadinessSource
import app.mauri.sukrara.core.model.AlertUiState
import app.mauri.sukrara.core.model.AppUpdates
import app.mauri.sukrara.core.model.Clock
import app.mauri.sukrara.core.model.SensorState
import app.mauri.sukrara.core.model.SystemAccessPort
import app.mauri.sukrara.core.model.storage.SettingsRepository
import app.mauri.sukrara.feature.settings.advanced.AdvancedPort
import app.mauri.sukrara.feature.settings.follower.SettingsFromPort
import app.mauri.sukrara.feature.settings.sensorend.SensorCalendarPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant

/**
 * The settings feature's wiring: `AppGraph` builds it with one line and implements [SettingsDeps] by
 * returning [deps]. Construction does no I/O.
 */
class SettingsModule(
    settings: SettingsRepository,
    alertSettings: AlertSettingsStore,
    alertControls: AlertControls,
    alertState: Flow<AlertUiState>,
    readiness: AlertReadinessSource,
    appVersion: String,
    prerelease: Boolean,
    clock: Clock,
    sensor: Flow<SensorState>,
    appsShared: Flow<Set<AppOutput>>,
    /** Root and the System UI module; null in a build without them. */
    systemAccess: SystemAccessPort?,
    /** Which version this is: Google Play or GitHub (About). */
    distribution: Distribution,
    /** The GitHub version's updater (About); returns null in the Google Play build. */
    updates: () -> AppUpdates?,
    alertPlayback: AlertPlayback,
    pensAvailable: () -> Boolean = { false },
    pens: () -> app.mauri.sukrara.core.model.PensPort? = { null },
    clinicBanner: Flow<Boolean> = flowOf(false),
    dismissClinicBanner: () -> Unit = {},
    restoreClinicBanner: () -> Unit = {},
    closedTips: Flow<Int> = flowOf(0),
    restoreClosedTips: () -> Unit = {},
    advanced: () -> AdvancedPort = { AdvancedPort.None },
    overviewSharing: Flow<OverviewSharing> = flowOf(OverviewSharing()),
    settingsFrom: SettingsFromPort = SettingsFromPort.None,
    sensorCalendar: () -> SensorCalendarPort = { SensorCalendarPort.None },
) {
    val deps: SettingsDeps =
        object : SettingsDeps {
            override val settings = settings
            override val alertSettings = alertSettings
            override val alertControls = alertControls
            override val alertState = alertState
            override val readiness = readiness
            override val appVersion = appVersion
            override val prerelease = prerelease
            override val distribution = distribution
            override val updates: AppUpdates? by lazy { updates() }
            override val clock = clock
            override val sensor = sensor
            override val appsShared = appsShared
            override val systemAccess = systemAccess
            override val alertPlayback = alertPlayback

            override val clinicBanner = clinicBanner

            override fun pensAvailable() = pensAvailable()

            override val pens get() = pens()

            override fun dismissClinicBanner() = dismissClinicBanner()

            override fun restoreClinicBanner() = restoreClinicBanner()

            override val closedTips = closedTips

            override fun restoreClosedTips() = restoreClosedTips()

            override val advanced by lazy { advanced() }

            override val overviewSharing = overviewSharing
            override val settingsFrom = settingsFrom
            override val sensorCalendar by lazy { sensorCalendar() }
        }

    /** When the alert thresholds were confirmed (null = never): home's Finish-setup card asks until they are. */
    val thresholdsConfirmedAt: Flow<Instant?> = SchemaPrefs.thresholdsConfirmedAt(settings)
}
