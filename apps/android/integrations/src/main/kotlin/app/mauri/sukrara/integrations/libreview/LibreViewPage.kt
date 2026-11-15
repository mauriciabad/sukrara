// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations.libreview

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.mauri.sukrara.core.model.Durations
import app.mauri.sukrara.designsystem.dialog.NumberDialog
import app.mauri.sukrara.designsystem.dialog.NumberInput
import app.mauri.sukrara.designsystem.errors.UserErrors
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.designsystem.rows.FooterText
import app.mauri.sukrara.designsystem.rows.InfoRow
import app.mauri.sukrara.designsystem.rows.MainSwitchRow
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.rows.SettingsIntro
import app.mauri.sukrara.designsystem.rows.SwitchRow
import app.mauri.sukrara.designsystem.rows.ValueRow
import app.mauri.sukrara.graph.CardWords
import app.mauri.sukrara.integrations.R
import app.mauri.sukrara.integrations.ui.behindWhy
import app.mauri.sukrara.integrations.ui.since
import app.mauri.sukrara.integrations.ui.uploadStatusText
import app.mauri.sukrara.integrations.upload.UploadStatus
import app.mauri.sukrara.settings.SettingsSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import app.mauri.sukrara.core.designsystem.R as DsR

/** The *Take over* dialog's state. */
sealed interface LvTakeOverUi {
    data object Idle : LvTakeOverUi

    data object Busy : LvTakeOverUi

    data class Done(val result: LvTakeOver) : LvTakeOverUi
}

class LibreViewViewModel(
    private val uploader: LibreViewUploader,
    private val wearer: Boolean,
    private val now: () -> Instant,
) : ViewModel() {
    val page: StateFlow<LvPageState?> = uploader.page.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val _takeOver = MutableStateFlow<LvTakeOverUi>(LvTakeOverUi.Idle)
    val takeOver: StateFlow<LvTakeOverUi> = _takeOver.asStateFlow()

    /** The page's actions; [openAccount] opens THE LibreView account screen (the app's navigation). */
    fun actions(openAccount: () -> Unit) =
        LibreViewActions(
            change = { t -> viewModelScope.launch { runCatching { uploader.change(t) } } },
            openAccount = openAccount,
            takeOver = {
                _takeOver.value = LvTakeOverUi.Busy
                viewModelScope.launch {
                    val r = runCatching { uploader.takeOver() }.getOrElse { LvTakeOver.Failed(UserErrors.of(it)) }
                    _takeOver.value = LvTakeOverUi.Done(r)
                }
            },
            resetTakeOver = { _takeOver.value = LvTakeOverUi.Idle },
            setInterval = { min -> viewModelScope.launch { uploader.setInterval(min) } },
            sendEarlier = { viewModelScope.launch { runCatching { uploader.sendEarlier() } } },
        )

    fun nowInstant() = now()

    val isWearer get() = wearer
}

class LibreViewActions(
    val change: ((LvSettings) -> LvSettings) -> Unit = {},
    /** THE LibreView account screen: sign in (once, for the whole app), or who is signed in with *Sign out*. */
    val openAccount: () -> Unit = {},
    /** *Take over* from the device that uploads for the account now. */
    val takeOver: () -> Unit = {},
    val resetTakeOver: () -> Unit = {},
    val setInterval: (Double) -> Unit = {},
    /** *Send earlier glucose*: the current sensor's glucose from before the switch-on goes up too. */
    val sendEarlier: () -> Unit = {},
)

@Composable
fun LibreViewPage(
    viewModel: LibreViewViewModel,
    openAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val page by viewModel.page.collectAsStateWithLifecycle()
    val takeOver by viewModel.takeOver.collectAsStateWithLifecycle()
    var taking by rememberSaveable { mutableStateOf(false) }
    page?.let {
        LibreViewContent(
            it,
            viewModel.isWearer,
            takeOver,
            viewModel.nowInstant(),
            viewModel.actions(openAccount),
            taking,
            { v -> taking = v },
            modifier,
        )
    }
}

/** One upload state in this page's words (the account, not a site). */
@Composable
fun libreViewStatusText(
    status: UploadStatus,
    now: Instant,
): String =
    when (status) {
        UploadStatus.Setup -> {
            stringResource(R.string.in_lv_status_setup)
        }

        UploadStatus.AuthFailed -> {
            stringResource(R.string.in_lv_status_auth)
        }

        is UploadStatus.Behind -> {
            val why = if (status.source != null) stringResource(R.string.in_lv_glucose_other_source) else behindWhy(status)
            stringResource(R.string.in_lv_status_behind, since(status.since, now), why)
        }

        else -> {
            uploadStatusText(status, now)
        }
    }

/**
 * Settings › Share with apps › LibreView: the app's LibreView account (signed in once, on the account screen: this page
 * only shows it and opens it), the main switch (turning it off always works), the status (*Take over* when another
 * device uploads, *Sign in again* when LibreView refused the saved password), glucose and entries.
 */
@Composable
fun LibreViewContent(
    state: LvPageState,
    wearer: Boolean,
    takeOver: LvTakeOverUi,
    now: Instant,
    actions: LibreViewActions,
    taking: Boolean,
    setTaking: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    val s = state.settings
    val on = s.on && wearer && state.signedIn
    var choosingInterval by rememberSaveable { mutableStateOf(false) }
    var sendingEarlier by rememberSaveable { mutableStateOf(false) }
    SettingsIntro(stringResource(R.string.in_lv_intro))
    SettingsGroup {
        ValueRow(
            stringResource(R.string.in_lv_account),
            when {
                state.signInAgain -> stringResource(R.string.in_lv_sign_in_again)
                state.signedIn -> state.email.ifEmpty { stringResource(R.string.in_lv_signed_in) }
                else -> stringResource(R.string.in_lv_sign_in)
            },
            actions.openAccount,
            enabled = wearer,
            icon = DsR.drawable.ds_ic_account_circle,
        )
    }
    // the page's main switch (SPA MainSwitchPreference); while it can't be turned on, the page says why
    MainSwitchRow(
        stringResource(R.string.in_lv_main),
        s.on,
        { v -> actions.change { it.copy(on = v) } },
        // switching off always works (signed out, or no longer the wearer); switching on needs the account
        enabled = s.on || (wearer && state.signedIn),
        note =
            when {
                !wearer -> stringResource(R.string.in_status_follower)
                state.signInAgain -> stringResource(R.string.in_lv_status_again)
                !state.signedIn -> stringResource(R.string.in_lv_main_needs_account)
                else -> null
            },
    )
    if (s.on && wearer) {
        SettingsGroup {
            val status = state.status
            val text =
                when {
                    state.signInAgain -> {
                        stringResource(R.string.in_lv_status_again)
                    }

                    // the Glucose switch below already says where the glucose comes from
                    status is UploadStatus.Behind && status.source != null -> {
                        stringResource(R.string.in_lv_status_behind_only, since(status.since, now))
                    }

                    else -> {
                        libreViewStatusText(status, now)
                    }
                }
            when (status) {
                // another device uploads: only the person's *Take over* gets past it
                UploadStatus.AuthFailed -> {
                    ValueRow(stringResource(R.string.in_ns_status), text, {
                        actions.resetTakeOver()
                        setTaking(true)
                    })
                }

                // no usable account: the account screen
                UploadStatus.Setup -> {
                    ValueRow(stringResource(R.string.in_ns_status), text, actions.openAccount)
                }

                else -> {
                    InfoRow(stringResource(R.string.in_ns_status), Modifier.semantics { liveRegion = LiveRegionMode.Polite }, text)
                }
            }
        }
    }
    SettingsGroup(title = stringResource(R.string.in_ns_what)) {
        val off = if (on) null else stringResource(R.string.in_ns_unavailable)
        SwitchRow(stringResource(R.string.in_lv_glucose), s.glucose, { v ->
            actions.change { it.copy(glucose = v) }
        }, enabled = on, summary = off ?: glucoseSummary(state))
        // the phone holds the current sensor's glucose from before the switch-on: offered, never sent by itself
        val earlier = state.earlierFrom?.takeIf { on && s.glucose && state.otherSource == null }
        if (earlier != null) {
            ValueRow(
                stringResource(R.string.in_lv_earlier),
                stringResource(R.string.in_lv_earlier_summary, dateTime(earlier)),
                { sendingEarlier = true },
                icon = DsR.drawable.ds_ic_history,
            )
        }
        SwitchRow(stringResource(R.string.in_ns_entries), s.treatments, { v ->
            actions.change { it.copy(treatments = v) }
        }, enabled = on, summary = off ?: stringResource(R.string.in_lv_entries_summary))
        ValueRow(
            stringResource(R.string.in_lv_interval),
            if (on) intervalText(state.intervalMin) else stringResource(R.string.in_ns_unavailable),
            { choosingInterval = true },
            enabled = on,
            icon = DsR.drawable.ds_ic_schedule,
        )
    }
    FooterText(stringResource(R.string.in_lv_footer))
    if (taking) {
        TakeOverDialog(takeOver, actions) { setTaking(false) }
    }
    val earlierFrom = state.earlierFrom
    if (sendingEarlier && earlierFrom != null) {
        SendEarlierDialog(earlierFrom, actions.sendEarlier) { sendingEarlier = false }
    }
    if (choosingInterval) {
        val words = CardWords.of(LocalContext.current.resources).durations
        NumberDialog(
            stringResource(R.string.in_lv_interval),
            NumberInput(SettingsSpec.LIBRE_VIEW_INTERVAL),
            state.intervalMin,
            { Durations.text(it.toLong(), words) },
            { choosingInterval = false },
            onChoose = actions.setInterval,
        )
    }
}

/** "5 min", "1 h": THE duration formatter. */
@Composable
private fun intervalText(min: Double): String = Durations.text(min.toLong(), CardWords.of(LocalContext.current.resources).durations)

@Composable
private fun glucoseSummary(state: LvPageState): String {
    // switched on, but what the phone reads can't go up: said at once, not only once it has stalled
    if (state.otherSource != null && state.settings.glucose) return stringResource(R.string.in_lv_glucose_other_source)
    val from = state.glucoseFrom ?: return stringResource(R.string.in_lv_glucose_summary)
    return stringResource(R.string.in_lv_glucose_from, dateTime(from))
}

/** "3 Nov, 14:40": a moment with its date. */
@Composable
private fun dateTime(at: Instant): String =
    DateUtils.formatDateTime(
        LocalContext.current,
        at.toEpochMilli(),
        DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH,
    )

/**
 * *Send earlier glucose*: what goes up (the current sensor's glucose from [from]) and why it is asked (another app may
 * have sent it: LibreView would count it twice); *Send* or *Cancel*.
 */
@Composable
private fun SendEarlierDialog(
    from: Instant,
    send: () -> Unit,
    onClose: () -> Unit,
) {
    val haptics = rememberHaptics()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.in_lv_earlier_title)) },
        text = { Text(stringResource(R.string.in_lv_earlier_text, dateTime(from)), style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = {
                haptics.confirm()
                send()
                onClose()
            }) { Text(stringResource(R.string.in_lv_earlier_send)) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.in_cancel)) } },
    )
}

/**
 * Another device uploads for the account: what *Take over* does, *Take over* or *Cancel*. No password: the account is
 * the app's; if LibreView no longer accepts it, the dialog closes onto the account screen.
 */
@Composable
private fun TakeOverDialog(
    ui: LvTakeOverUi,
    actions: LibreViewActions,
    onClose: () -> Unit,
) {
    val haptics = rememberHaptics()
    val close by rememberUpdatedState(onClose)
    val openAccount by rememberUpdatedState(actions.openAccount)
    LaunchedEffect(ui) {
        val r = (ui as? LvTakeOverUi.Done)?.result ?: return@LaunchedEffect
        when (r) {
            LvTakeOver.Done -> {
                haptics.confirm()
                close()
            }

            LvTakeOver.SignIn -> {
                close()
                openAccount()
            }

            is LvTakeOver.Failed -> {
                haptics.reject()
            }
        }
    }
    val busy = ui == LvTakeOverUi.Busy
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.in_lv_take_over_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.in_lv_take_over_text), style = MaterialTheme.typography.bodyMedium)
                val result =
                    when {
                        busy -> {
                            stringResource(R.string.in_lv_taking_over)
                        }

                        ui is LvTakeOverUi.Done && ui.result is LvTakeOver.Failed -> {
                            stringResource(
                                R.string.in_lv_take_over_failed,
                                stringResource(ui.result.why.text),
                            )
                        }

                        else -> {
                            null
                        }
                    }
                if (result != null) {
                    Text(
                        result,
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = if (busy) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = actions.takeOver, enabled = !busy) { Text(stringResource(R.string.in_lv_take_over)) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.in_cancel)) } },
    )
}
