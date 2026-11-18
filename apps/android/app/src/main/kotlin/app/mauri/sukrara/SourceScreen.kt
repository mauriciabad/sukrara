// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.mauri.sukrara.designsystem.rows.NavigationRow
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.feature.settings.SettingsPage
import app.mauri.sukrara.feature.settings.follower.settingsFromLabel
import app.mauri.sukrara.feature.settings.search.Section
import app.mauri.sukrara.feature.settings.search.SectionIcon
import app.mauri.sukrara.feature.settings.sensor.SensorSettingsActions
import app.mauri.sukrara.feature.settings.sensor.SensorSettingsViewModel
import app.mauri.sukrara.feature.settings.source.SourceActions
import app.mauri.sukrara.feature.settings.source.SourcePageContent
import app.mauri.sukrara.feature.settings.source.SourcePageState
import app.mauri.sukrara.feature.sharing.follower.FollowingPage
import app.mauri.sukrara.feature.sharing.follower.FollowingViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import app.mauri.sukrara.feature.settings.R as SettingsR

/**
 * Settings › Source (docs/44 "Settings"), joined here because its rows come from three features: the settings page
 * itself, the sensor's (Juggluco's API secret, its state) and sharing's (another Sukrara's follow). *Whose glucose* and
 * *Where it comes from* open setup's steps on their own ([openStep]); the stream's *Stop following* leaves for setup,
 * as it always did.
 */
@Composable
internal fun SourceScreen(
    graph: AppGraph,
    openStep: (app.mauri.sukrara.feature.sharing.setup.SetupStep) -> Unit,
    openSensor: () -> Unit,
    openImport: () -> Unit,
    openSettingsFrom: () -> Unit,
    onLeftStream: () -> Unit,
    onBack: () -> Unit,
) {
    val loaded by graph.specLoaded.collectAsStateWithLifecycle(null)
    val stored by remember(graph) { graph.source.data.map { it.spec } }.collectAsStateWithLifecycle(null)
    val glucose by graph.monitor.hub.state
        .collectAsStateWithLifecycle()
    val sensorVm: SensorSettingsViewModel = viewModel(factory = SensorSettingsViewModel.factory(graph.sensorSettings))
    val sensor by sensorVm.state.collectAsStateWithLifecycle()
    val from by graph.followerSettings.state.collectAsStateWithLifecycle(null)
    val now by produceState(graph.clock.now()) {
        while (true) {
            delay(NOW_MS)
            value = graph.clock.now()
        }
    }
    SettingsPage(stringResource(SettingsR.string.st_source_title), onBack) {
        val spec = loaded?.spec
        SourcePageContent(
            SourcePageState(spec, answered = stored != null, name = glucose.link?.wearerName, latest = glucose.latest?.t),
            sensor,
            now,
            SourceActions(
                changePerson = { openStep(app.mauri.sukrara.feature.sharing.setup.SetupStep.Person) },
                changeSource = { openStep(app.mauri.sukrara.feature.sharing.setup.SetupStep.Source) },
                openSensor = openSensor,
                openImport = openImport,
            ),
            SensorSettingsActions(setJugglucoSecret = sensorVm::setJugglucoSecret),
            streamRows = {
                FollowingPage(
                    viewModel = viewModel(factory = FollowingViewModel.factory(graph.setup.follower)),
                    onJoinAgain = { openStep(app.mauri.sukrara.feature.sharing.setup.SetupStep.Join) },
                    onLeft = onLeftStream,
                    embedded = true,
                )
                // which settings and alerts apply: the stream's own row (the overview has it too)
                from?.let { f ->
                    SettingsGroup {
                        NavigationRow(
                            stringResource(SettingsR.string.st_from_title),
                            openSettingsFrom,
                            summary = settingsFromLabel(f),
                            leading = { SectionIcon(Section.SettingsFrom) },
                        )
                    }
                }
            },
        )
    }
}

/** How often the page's "last reading 2 minutes ago" is written again. */
private const val NOW_MS = 30_000L
