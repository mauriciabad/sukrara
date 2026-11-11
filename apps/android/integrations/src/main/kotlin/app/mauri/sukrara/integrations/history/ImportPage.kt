// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.history

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.mauri.sukrara.core.model.storage.ImportSource
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.designsystem.motion.LocalReducedMotion
import app.mauri.sukrara.designsystem.motion.Motion
import app.mauri.sukrara.designsystem.motion.Reveal
import app.mauri.sukrara.designsystem.motion.Swap
import app.mauri.sukrara.designsystem.rows.ActionRow
import app.mauri.sukrara.designsystem.rows.CardRow
import app.mauri.sukrara.designsystem.rows.FooterText
import app.mauri.sukrara.designsystem.rows.NavigationRow
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.rows.SettingsIntro
import app.mauri.sukrara.integrations.R
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/** What Import history shows. */
data class ImportUi(
    /** Only the wearer imports: a follower's history is the wearer's, shared from their phone. */
    val wearer: Boolean,
    val state: ImportState,
    val tidepoolSignedIn: Boolean,
    val tidepoolEmail: String = "",
) {
    val running: Boolean get() = state is ImportState.Running
}

class ImportActions(
    val importTidepool: () -> Unit = {},
    val openTidepool: () -> Unit = {},
    val openLibreView: () -> Unit = {},
    val chooseFile: () -> Unit = {},
    val stop: () -> Unit = {},
    val dismiss: () -> Unit = {},
)

class ImportViewModel(
    private val importer: HistoryImporter,
    wearer: Flow<Boolean>,
    /** Tidepool signed in, and the account's email ("" unknown). */
    tidepool: Flow<Pair<Boolean, String>>,
    private val io: CoroutineDispatcher,
) : ViewModel() {
    val ui: StateFlow<ImportUi?> =
        combine(wearer, importer.state, tidepool) { w, s, (signed, email) -> ImportUi(w, s, signed, email) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Reads the chosen file (the system picker's grant) off the main thread and imports it. */
    fun importFile(
        context: Context,
        uri: Uri,
    ) {
        val app = context.applicationContext
        importer.launchFile {
            withContext(io) {
                runCatching {
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readNBytes(MAX_FILE_BYTES + 1)
                        if (bytes.size > MAX_FILE_BYTES) null else bytes.decodeToString()
                    }
                }.getOrNull()
            }
        }
    }

    fun importTidepool() = importer.launchTidepool()

    fun stop() = importer.stop()

    fun dismiss() = importer.dismiss()

    companion object {
        /** Years of LibreView history are a few MB; anything far bigger isn't a glucose file. */
        const val MAX_FILE_BYTES = 64 * 1024 * 1024
    }
}

/** The stateful page: the view model, the file picker and LibreView's page in the browser. */
@Composable
fun ImportPage(
    viewModel: ImportViewModel,
    openTidepool: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.importFile(context, uri)
        }
    ui?.let {
        ImportContent(
            it,
            ImportActions(
                importTidepool = viewModel::importTidepool,
                openTidepool = openTidepool,
                openLibreView = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(LIBREVIEW)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
                chooseFile = { picker.launch(FILE_TYPES) },
                stop = viewModel::stop,
                dismiss = viewModel::dismiss,
            ),
            modifier,
        )
    }
}

/** LibreView's site: *Glucose History › Download glucose data* is there (behind its own sign-in and captcha). */
const val LIBREVIEW = "https://www.libreview.com"

/** What the picker offers: CSV under its many names (some file managers label it as plain text or Excel). */
private val FILE_TYPES =
    arrayOf(
        "text/csv",
        "text/comma-separated-values",
        "application/csv",
        "text/plain",
        "application/vnd.ms-excel",
        "application/octet-stream",
    )

/**
 * Settings › Share with apps › Import history (wearer): the last 90 days from Tidepool, LibreView's file or a Sukrara
 * export, the import's progress with *Stop*, and what it added. A follower's page says their history comes from the
 * wearer.
 */
@Composable
fun ImportContent(
    ui: ImportUi,
    actions: ImportActions,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    if (!ui.wearer) {
        SettingsIntro(stringResource(R.string.in_import_follower))
        return@Column
    }
    var libreView by rememberSaveable { mutableStateOf(false) }
    SettingsIntro(stringResource(R.string.in_import_intro))
    Reveal(ui.state !is ImportState.Idle) {
        SettingsGroup { CardRow { ImportStatus(ui, actions) } }
    }
    SettingsGroup(title = stringResource(R.string.in_import_accounts_group)) {
        if (ui.tidepoolSignedIn) {
            ActionRow(
                stringResource(R.string.in_tp_title),
                actions.importTidepool,
                summary =
                    if (ui.tidepoolEmail.isEmpty()) {
                        stringResource(R.string.in_import_tidepool_summary_plain)
                    } else {
                        stringResource(R.string.in_import_tidepool_summary, ui.tidepoolEmail)
                    },
                enabled = !ui.running,
            )
        } else {
            NavigationRow(
                stringResource(R.string.in_tp_title),
                actions.openTidepool,
                summary = stringResource(R.string.in_import_tidepool_sign_in),
                enabled = !ui.running,
            )
        }
        ActionRow(
            stringResource(R.string.in_lv_title),
            { libreView = true },
            summary = stringResource(R.string.in_import_libreview_summary),
            enabled = !ui.running,
        )
    }
    SettingsGroup(title = stringResource(R.string.in_apps_files_group)) {
        ActionRow(
            stringResource(R.string.in_import_file),
            actions.chooseFile,
            summary = stringResource(R.string.in_import_file_summary),
            enabled = !ui.running,
        )
    }
    FooterText(stringResource(R.string.in_import_footer))
    if (libreView) {
        AlertDialog(
            onDismissRequest = { libreView = false },
            title = { Text(stringResource(R.string.in_import_libreview_title)) },
            text = { Text(stringResource(R.string.in_import_libreview_steps)) },
            confirmButton = {
                Row {
                    TextButton(onClick = actions.openLibreView) { Text(stringResource(R.string.in_import_libreview_open)) }
                    TextButton(onClick = {
                        libreView = false
                        actions.chooseFile()
                    }) { Text(stringResource(R.string.in_import_libreview_choose)) }
                }
            },
            dismissButton = { TextButton(onClick = { libreView = false }) { Text(stringResource(R.string.in_import_cancel)) } },
        )
    }
}

/** The running import (progress, what it added so far, *Stop*) or how it ended (*OK*). */
@Composable
private fun ImportStatus(
    ui: ImportUi,
    actions: ImportActions,
) {
    val haptics = rememberHaptics()
    val state = ui.state
    LaunchedEffect(state is ImportState.Done, state is ImportState.Failed) {
        when (state) {
            is ImportState.Done -> {
                haptics.confirm()
            }

            is ImportState.Failed -> {
                haptics.reject()
            }

            else -> {}
        }
    }
    Swap(state::class) { _ ->
        Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state) {
                is ImportState.Running -> {
                    Title(stringResource(runningTitle(state.source)))
                    val target = if (state.steps == 0) 0f else state.step.toFloat() / state.steps
                    val progress by animateFloatAsState(target, Motion.defaultSpatial(LocalReducedMotion.current), label = "import")
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text(addedText(state.tally), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = actions.stop) { Text(stringResource(R.string.in_import_stop)) }
                }

                is ImportState.Done -> {
                    Title(stringResource(doneTitle(state.source)))
                    TallyLines(state.tally)
                    Button(onClick = actions.dismiss) { Text(stringResource(R.string.in_import_ok)) }
                }

                is ImportState.Stopped -> {
                    Title(stringResource(R.string.in_import_stopped))
                    Text(addedText(state.tally), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.in_import_stopped_body), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = actions.dismiss) { Text(stringResource(R.string.in_import_ok)) }
                }

                is ImportState.Failed -> {
                    Title(stringResource(R.string.in_import_failed))
                    Text(failureText(state.why), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    if (state.tally.added > 0) Text(addedText(state.tally), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.why == ImportFailure.SignedOut) {
                            Button(onClick = {
                                actions.dismiss()
                                actions.openTidepool()
                            }) { Text(stringResource(R.string.in_import_tidepool_sign_in_action)) }
                        }
                        OutlinedButton(onClick = actions.dismiss) { Text(stringResource(R.string.in_import_ok)) }
                    }
                }

                ImportState.Idle -> {}
            }
        }
    }
}

@Composable
private fun Title(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })

@Composable
private fun TallyLines(t: ImportTally) {
    val lines =
        listOfNotNull(
            addedText(t),
            if (t.alreadyHere > 0) pluralStringResource(R.plurals.in_import_already, t.alreadyHere, t.alreadyHere) else null,
            if (t.outsidePeriod > 0) pluralStringResource(R.plurals.in_import_outside, t.outsidePeriod, t.outsidePeriod) else null,
            if (t.skipped > 0) pluralStringResource(R.plurals.in_import_skipped, t.skipped, t.skipped) else null,
        )
    Text(lines.joinToString("\n"), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun addedText(t: ImportTally): String =
    stringResource(
        R.string.in_import_added,
        pluralStringResource(R.plurals.in_import_readings, t.readings, t.readings),
        pluralStringResource(R.plurals.in_import_entries, t.entries, t.entries),
    )

@Composable
private fun failureText(why: ImportFailure): String =
    when (why) {
        ImportFailure.NotGlucoseData -> stringResource(R.string.in_import_err_not_glucose)
        ImportFailure.UnknownDates -> stringResource(R.string.in_import_err_dates)
        ImportFailure.Unreadable -> stringResource(R.string.in_import_err_unreadable)
        ImportFailure.SignedOut -> stringResource(R.string.in_import_err_signed_out)
        is ImportFailure.Network -> stringResource(why.why.text)
    }

private fun runningTitle(s: ImportSource): Int =
    when (s) {
        ImportSource.Tidepool -> R.string.in_import_running_tidepool
        ImportSource.LibreView -> R.string.in_import_running_libreview
        ImportSource.File -> R.string.in_import_running_file
    }

private fun doneTitle(s: ImportSource): Int =
    when (s) {
        ImportSource.Tidepool -> R.string.in_import_done_tidepool
        ImportSource.LibreView -> R.string.in_import_done_libreview
        ImportSource.File -> R.string.in_import_done_file
    }

/** The Share with apps row's summary: what is running, or what Import history does. */
@Composable
fun importStatusText(state: ImportState): String =
    when (state) {
        is ImportState.Running -> stringResource(runningTitle(state.source))
        is ImportState.Done -> stringResource(doneTitle(state.source))
        is ImportState.Failed -> stringResource(R.string.in_import_failed)
        is ImportState.Stopped -> stringResource(R.string.in_import_stopped)
        ImportState.Idle -> stringResource(R.string.in_import_row_summary)
    }
