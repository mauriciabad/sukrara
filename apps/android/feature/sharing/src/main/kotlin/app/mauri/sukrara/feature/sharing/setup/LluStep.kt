// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.sharing.setup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.core.model.OnlineHealth
import app.mauri.sukrara.core.model.Person
import app.mauri.sukrara.core.model.SourceKind
import app.mauri.sukrara.designsystem.banner.NoticeCard
import app.mauri.sukrara.designsystem.banner.NoticeTone
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.designsystem.motion.LocalReducedMotion
import app.mauri.sukrara.designsystem.motion.Motion
import app.mauri.sukrara.designsystem.rows.RadioRow
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.source.iconRes
import app.mauri.sukrara.designsystem.source.onlineHealthText
import app.mauri.sukrara.feature.sharing.R
import kotlinx.coroutines.delay
import java.time.Instant
import app.mauri.sukrara.core.designsystem.R as DsR

/** What the LibreLinkUp step shows now. */
private enum class LluMode { Form, Choose, Problem, Connected }

/**
 * The connect step for LibreLinkUp (docs/44 step 2, "Setup" Connect; Appendix C "States and exits"): sign in (for your
 * own glucose, *Sign in as …* with the LibreView account Sukrara already has, in one tap), then whose glucose when the
 * account follows several people, then *Signed in as …* with the source's health or *Reading received* and *Continue*.
 * Signed in with no one shared (or new terms to accept in LibreLinkUp), it says what to do and *Try again* asks again
 * without the password. Each problem shows under the field it is about; a refused password is never retried by
 * itself (retries lock the account). No glucose value shows here (a value is a card surface).
 */
@Composable
internal fun ColumnScope.LluStep(
    u: SetupUi,
    actions: SetupActions,
) {
    val llu = u.llu
    val c = llu.connection
    val person = (u.facts.spec ?: u.facts.running)?.person ?: Person.Yours
    val haptics = rememberHaptics()
    val signedOut = c.health == OnlineHealth.SignedOut
    var changing by rememberSaveable(c.email) { mutableStateOf(false) }
    var other by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable(c.email) { mutableStateOf(c.email) }
    // the password is never kept in saved state: a rotation asks for it again
    var password by remember { mutableStateOf("") }
    LaunchedEffect(llu.result) {
        when (llu.result) {
            LluConnectOutcome.Connected, LluConnectOutcome.Choose -> {
                haptics.confirm()
                changing = false
                password = ""
            }

            null -> {
                Unit
            }

            else -> {
                haptics.reject()
                // *Sign in as …* refused (the LibreView password changed, or none is saved): the form, its email filled
                // in, says so under the password
                val libreView = c.libreViewEmail
                if (llu.result == LluConnectOutcome.BadCredentials && libreView != null && !other) {
                    other = true
                    email = libreView
                }
            }
        }
    }
    val mode =
        when {
            changing || !c.signedIn || signedOut -> {
                LluMode.Form
            }

            c.patient.isEmpty() && c.people.size > 1 -> {
                LluMode.Choose
            }

            c.patient.isEmpty() || c.health == OnlineHealth.NotShared || c.health == OnlineHealth.ActionNeeded -> {
                LluMode.Problem
            }

            else -> {
                LluMode.Connected
            }
        }
    StepHeader(
        stringResource(if (mode == LluMode.Choose) R.string.sh_setup_llu_choose_title else R.string.sh_setup_llu_title),
        stringResource(
            when {
                mode == LluMode.Choose -> R.string.sh_setup_llu_choose_body
                person == Person.Yours -> R.string.sh_setup_llu_body
                else -> R.string.sh_setup_llu_body_theirs
            },
        ),
    )
    val reduced = LocalReducedMotion.current
    AnimatedContent(mode, transitionSpec = { Motion.fadeThrough(reduced) }, label = "llu-step") { m ->
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (m) {
                LluMode.Form -> {
                    // changing an account that works: *Keep this account* goes back to it, and *Sign in as …* is offered
                    // only for another account than this one
                    val keeping = c.signedIn && !signedOut
                    val keep =
                        stringResource(R.string.sh_setup_llu_keep) to {
                            changing = false
                            other = false
                        }
                    val libreView =
                        c.libreViewEmail?.takeIf {
                            person == Person.Yours && !other && !signedOut && !(keeping && it.equals(c.email, ignoreCase = true))
                        }
                    if (libreView != null) {
                        NoticeCard(
                            stringResource(R.string.sh_setup_llu_libreview_body),
                            title = stringResource(R.string.sh_setup_llu_libreview_title),
                            icon = DsR.drawable.ds_ic_person,
                        )
                        LluProblem(llu.result)
                        AnimatedVisibility(llu.connecting) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        StepButtons(
                            stringResource(R.string.sh_setup_llu_libreview_use, libreView),
                            actions.connectLluLibreView,
                            enabled = !llu.connecting,
                            secondary =
                                listOfNotNull(
                                    stringResource(R.string.sh_setup_llu_other) to { other = true },
                                    keep.takeIf { keeping },
                                ),
                        )
                    } else {
                        LluForm(email, {
                            email = it
                            actions.clearLluResult()
                        }, password, {
                            password = it
                            actions.clearLluResult()
                        }, llu.result)
                        AnimatedVisibility(llu.connecting) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        StepButtons(
                            stringResource(if (llu.connecting) R.string.sh_setup_llu_connecting else R.string.sh_setup_llu_connect),
                            { actions.connectLlu(email, password) },
                            enabled = email.isNotBlank() && password.isNotEmpty() && !llu.connecting,
                            secondary =
                                if (keeping) {
                                    listOf(keep)
                                } else {
                                    listOf(stringResource(R.string.sh_setup_later) to { actions.skip(SetupStep.Sensor) })
                                },
                        )
                    }
                    Text(
                        stringResource(R.string.sh_setup_llu_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                LluMode.Choose -> {
                    var picked by rememberSaveable { mutableStateOf<String?>(null) }
                    SettingsGroup(modifier = Modifier.selectableGroup()) {
                        for (p in c.people) {
                            RadioRow(
                                title = p.name.ifBlank { stringResource(R.string.sh_setup_llu_unnamed) },
                                selected = picked == p.id,
                                onSelect = { picked = p.id },
                            )
                        }
                    }
                    StepButtons(
                        stringResource(R.string.sh_setup_continue),
                        { picked?.let(actions.chooseLlu) },
                        enabled = picked != null,
                        secondary = listOf(stringResource(R.string.sh_setup_llu_change) to { changing = true }),
                    )
                }

                LluMode.Problem -> {
                    val terms = llu.result == LluConnectOutcome.Terms || c.health == OnlineHealth.ActionNeeded
                    NoticeCard(
                        stringResource(if (terms) R.string.sh_setup_llu_problem_terms else R.string.sh_setup_llu_problem_no_one),
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        title = stringResource(R.string.sh_setup_llu_signed_in, c.email),
                        tone = NoticeTone.Warning,
                    )
                    AnimatedVisibility(llu.connecting) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    StepButtons(
                        stringResource(R.string.sh_setup_llu_try_again),
                        actions.recheckLlu,
                        enabled = !llu.connecting,
                        secondary =
                            listOf(
                                stringResource(R.string.sh_setup_llu_change) to { changing = true },
                                stringResource(R.string.sh_setup_later) to { actions.skip(SetupStep.Sensor) },
                            ),
                    )
                }

                LluMode.Connected -> {
                    LluConnected(u, c, actions) { changing = true }
                }
            }
        }
    }
}

/** *Signed in as …*: whose glucose, the source's health (or *Reading received*), *Continue* and *Change account*. */
@Composable
private fun ColumnScope.LluConnected(
    u: SetupUi,
    c: LluConnection,
    actions: SetupActions,
    change: () -> Unit,
) {
    val now by produceState(Instant.now()) {
        while (true) {
            delay(NOW_MS)
            value = Instant.now()
        }
    }
    val live = u.facts.ownSensorLive
    val text =
        if (live) {
            stringResource(R.string.sh_setup_sensor_live)
        } else {
            onlineHealthText(c.health, c.newest, now, "LibreLinkUp", SourceKind.LibreLinkUp)
        }
    NoticeCard(
        if (c.name.isNotBlank()) stringResource(R.string.sh_setup_llu_reading, c.name) + "\n" + text else text,
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        title = stringResource(R.string.sh_setup_llu_signed_in, c.email),
        icon = if (live) DsR.drawable.ds_ic_check_circle else c.health.iconRes(),
        tone = if (live || c.health.fine) NoticeTone.Info else NoticeTone.Warning,
    )
    if (!live && c.health == OnlineHealth.Connecting) {
        Text(
            stringResource(R.string.sh_setup_llu_waiting),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    StepButtons(
        stringResource(R.string.sh_setup_continue),
        actions.completeSensor,
        secondary = listOf(stringResource(R.string.sh_setup_llu_change) to change),
    )
}

/** The email and password fields, each with its own problem, and the problems that belong to neither. */
@Composable
private fun LluForm(
    email: String,
    onEmail: (String) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    result: LluConnectOutcome?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            email,
            onEmail,
            label = { Text(stringResource(R.string.sh_setup_llu_email)) },
            singleLine = true,
            isError = result == LluConnectOutcome.BadCredentials,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            password,
            onPassword,
            label = { Text(stringResource(R.string.sh_setup_llu_password)) },
            singleLine = true,
            isError = result == LluConnectOutcome.BadCredentials,
            visualTransformation = PasswordVisualTransformation(),
            supportingText =
                if (result == LluConnectOutcome.BadCredentials) {
                    { Text(stringResource(R.string.sh_setup_llu_problem_refused)) }
                } else {
                    null
                },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        LluProblem(result)
    }
}

/** A problem that belongs to no field (terms, too many attempts, no answer), said once where it happened. */
@Composable
private fun LluProblem(result: LluConnectOutcome?) {
    val general =
        when (result) {
            LluConnectOutcome.Terms -> R.string.sh_setup_llu_problem_terms
            LluConnectOutcome.RateLimited -> R.string.sh_setup_llu_problem_rate
            LluConnectOutcome.NoOne -> R.string.sh_setup_llu_problem_no_one
            LluConnectOutcome.Unreachable -> R.string.sh_setup_llu_problem_unreachable
            LluConnectOutcome.Offline -> R.string.sh_setup_site_problem_offline
            LluConnectOutcome.Stopped -> DsR.string.ds_online_llu_stopped
            else -> null
        }
    AnimatedVisibility(general != null) {
        NoticeCard(
            general?.let { stringResource(it) },
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            tone = NoticeTone.Warning,
        )
    }
}

/** How often *last reading 2 minutes ago* is written again. */
private const val NOW_MS = 30_000L
