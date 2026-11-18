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
import app.mauri.sukrara.designsystem.banner.NoticeCard
import app.mauri.sukrara.designsystem.banner.NoticeTone
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.designsystem.icons.Service
import app.mauri.sukrara.designsystem.motion.LocalReducedMotion
import app.mauri.sukrara.designsystem.motion.Motion
import app.mauri.sukrara.designsystem.source.iconRes
import app.mauri.sukrara.designsystem.source.onlineHealthText
import app.mauri.sukrara.feature.sharing.R
import app.mauri.sukrara.graph.ClockText
import kotlinx.coroutines.delay
import java.time.Instant
import app.mauri.sukrara.core.designsystem.R as DsR

/**
 * The connect step for an online source (docs/44 "Setup" Connect, step 1: a Nightscout site): the address and the token
 * or API secret (a pasted `?token=` address fills it), *Connect* (one read proves both before anything is saved), each
 * problem under the field it is about, then *Connected to …* with the site's health and *Continue*. No glucose value
 * shows here (a value is a card surface): the reading's age says it arrived. Opened alone from Settings › Source it
 * changes the site, or signs in again with the address filled in. *Later* keeps it on *Finish setup*.
 */
@Composable
internal fun ColumnScope.SiteStep(
    u: SetupUi,
    actions: SetupActions,
) {
    val site = u.site
    val c = site.connection
    val person = (u.facts.spec ?: u.facts.running)?.person ?: Person.Yours
    val haptics = rememberHaptics()
    val signedOut = c.health == OnlineHealth.SignedOut
    var editing by rememberSaveable(c.root, c.saved) { mutableStateOf(c.root.isEmpty() || !c.saved || signedOut) }
    var address by rememberSaveable(c.root) { mutableStateOf(c.root) }
    // the secret is never kept in saved state: a rotation asks for it again
    var credential by remember { mutableStateOf("") }
    LaunchedEffect(site.result) {
        when (site.result) {
            SiteConnectResult.Connected -> {
                haptics.confirm()
                editing = false
                credential = ""
            }

            null -> {
                Unit
            }

            else -> {
                haptics.reject()
            }
        }
    }
    StepHeader(
        stringResource(R.string.sh_setup_site_title),
        stringResource(if (person == Person.Yours) R.string.sh_setup_site_body else R.string.sh_setup_site_body_theirs),
        Service.Nightscout,
    )
    val reduced = LocalReducedMotion.current
    AnimatedContent(editing, transitionSpec = { Motion.fadeThrough(reduced) }, label = "site-step") { form ->
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (form) {
                SiteForm(
                    address = address,
                    onAddress = {
                        address = it
                        actions.clearSiteResult()
                    },
                    credential = credential,
                    onCredential = {
                        credential = it
                        actions.clearSiteResult()
                    },
                    result = site.result ?: SiteConnectResult.Refused.takeIf { signedOut && address == c.root && credential.isEmpty() },
                    keepsSaved = c.saved && !signedOut && address.trim().trimEnd('/') == c.root,
                )
                AnimatedVisibility(site.connecting) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                StepButtons(
                    stringResource(if (site.connecting) R.string.sh_setup_site_connecting else R.string.sh_setup_site_connect),
                    { actions.connectSite(address, credential) },
                    enabled = address.isNotBlank() && !site.connecting,
                    secondary =
                        if (c.saved && c.root.isNotEmpty() && !signedOut) {
                            listOf(stringResource(R.string.sh_setup_site_keep) to { editing = false })
                        } else {
                            listOf(stringResource(R.string.sh_setup_later) to { actions.skip(SetupStep.Sensor) })
                        },
                )
            } else {
                Connected(u, c, actions) { editing = true }
            }
        }
    }
}

/** *Connected to …*: the site's health (or *Reading received*), *Continue* and *Change site*. */
@Composable
private fun ColumnScope.Connected(
    u: SetupUi,
    c: SiteConnection,
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
    NoticeCard(
        if (live) {
            stringResource(R.string.sh_setup_sensor_live)
        } else {
            onlineHealthText(
                c.health,
                c.newest,
                now,
                c.host,
                readyAt = c.readyAt?.takeIf { it.isAfter(now) }?.let { ClockText.time(it.epochSecond) },
            )
        },
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        title = stringResource(R.string.sh_setup_site_connected, c.host),
        icon = if (live) DsR.drawable.ds_ic_check_circle else c.health.iconRes(),
        tone = if (live || c.health.fine) NoticeTone.Info else NoticeTone.Warning,
    )
    if (!live && c.health == OnlineHealth.Connecting) {
        Text(
            stringResource(R.string.sh_setup_site_waiting),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    StepButtons(
        stringResource(R.string.sh_setup_continue),
        actions.completeConnect,
        secondary = listOf(stringResource(R.string.sh_setup_site_change) to change),
    )
}

/** The address and the token / secret fields, each with its own problem. */
@Composable
private fun SiteForm(
    address: String,
    onAddress: (String) -> Unit,
    credential: String,
    onCredential: (String) -> Unit,
    result: SiteConnectResult?,
    keepsSaved: Boolean,
) {
    val addressProblem =
        when (result) {
            SiteConnectResult.BadAddress -> R.string.sh_setup_site_problem_url
            SiteConnectResult.NeedsHttps -> R.string.sh_setup_site_problem_https
            SiteConnectResult.NotNightscout -> R.string.sh_setup_site_problem_not_ns
            else -> null
        }
    val credentialProblem = R.string.sh_setup_site_problem_refused.takeIf { result == SiteConnectResult.Refused }
    val general =
        when (result) {
            SiteConnectResult.Unreachable -> R.string.sh_setup_site_problem_unreachable
            SiteConnectResult.Offline -> R.string.sh_setup_site_problem_offline
            else -> null
        }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            address,
            onAddress,
            label = { Text(stringResource(R.string.sh_setup_site_address)) },
            placeholder = { Text(stringResource(R.string.sh_setup_site_placeholder)) },
            singleLine = true,
            isError = addressProblem != null,
            supportingText = addressProblem?.let { { Text(stringResource(it)) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            credential,
            onCredential,
            label = { Text(stringResource(R.string.sh_setup_site_credential)) },
            singleLine = true,
            isError = credentialProblem != null,
            visualTransformation = PasswordVisualTransformation(),
            supportingText = {
                Text(
                    stringResource(
                        credentialProblem
                            ?: if (keepsSaved) R.string.sh_setup_site_credential_saved else R.string.sh_setup_site_credential_help,
                    ),
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        AnimatedVisibility(general != null) {
            NoticeCard(
                general?.let { stringResource(it) },
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                tone = NoticeTone.Warning,
            )
        }
    }
}

/** How often *last reading 2 minutes ago* is written again. */
private const val NOW_MS = 30_000L
