// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.core.model.SourceKind
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
import app.mauri.sukrara.integrations.librelinkup.LibreLinkUpSourceActions
import app.mauri.sukrara.integrations.librelinkup.LibreLinkUpSourceRows
import app.mauri.sukrara.integrations.nightscout.site.NightscoutSourceActions
import app.mauri.sukrara.integrations.nightscout.site.NightscoutSourceRows
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
    /** The new-sensor flow (LibreLinkUp's *Read the sensor in Sukrara*: the takeover). */
    openNewSensor: () -> Unit,
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
            SourcePageState(spec, answered = stored != null, name = glucose.name, latest = glucose.latest?.t),
            sensor,
            now,
            SourceActions(
                changePerson = { openStep(app.mauri.sukrara.feature.sharing.setup.SetupStep.Person) },
                changeSource = { openStep(app.mauri.sukrara.feature.sharing.setup.SetupStep.Source) },
                openSensor = openSensor,
                openImport = openImport,
            ),
            SensorSettingsActions(setJugglucoSecret = sensorVm::setJugglucoSecret),
            siteRows = {
                val scope = rememberCoroutineScope()
                if (spec?.kind == SourceKind.LibreLinkUp) {
                    val llu by graph.integrations.libreLinkUpSource.view
                        .collectAsStateWithLifecycle(null)
                    llu?.let { v ->
                        LibreLinkUpSourceRows(
                            v,
                            spec.person,
                            now,
                            LibreLinkUpSourceActions(
                                editAccount = { openStep(app.mauri.sukrara.feature.sharing.setup.SetupStep.Sensor) },
                                retry = {
                                    scope.launch {
                                        // the person accepted the terms or invited the account: whom it follows, read again
                                        // (an answer asks for the graph at once; a refusal isn't followed by more calls)
                                        graph.integrations.libreLinkUpSource.recheck()
                                    }
                                },
                                choose = { id -> scope.launch { graph.integrations.libreLinkUpSource.choose(id) } },
                                takeOver = openNewSensor,
                                signOut = { scope.launch { graph.integrations.libreLinkUpSource.signOut() } },
                                openSensor = openSensor,
                            ),
                        )
                    }
                    return@SourcePageContent
                }
                val site by graph.integrations.nightscoutSource.view
                    .collectAsStateWithLifecycle(null)
                site?.let { v ->
                    NightscoutSourceRows(
                        v,
                        spec?.person ?: Person.Yours,
                        now,
                        NightscoutSourceActions(
                            editSite = { openStep(app.mauri.sukrara.feature.sharing.setup.SetupStep.Sensor) },
                            retry = graph.integrations.nightscoutSource::kick,
                            setTreatments = { on ->
                                scope.launch {
                                    graph.nightscoutSourceConfig.set(
                                        graph.nightscoutSourceConfig.data
                                            .first()
                                            .copy(treatments = on),
                                    )
                                    if (on) graph.integrations.nightscoutSource.retryWrites()
                                }
                            },
                            pullHistory = {
                                scope.launch {
                                    graph.integrations.nightscoutSource.pullHistory(graph.clock.now().minus(java.time.Duration.ofDays(90)))
                                }
                            },
                        ),
                    )
                }
            },
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
