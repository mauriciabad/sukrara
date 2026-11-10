// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.designsystem.rows.ValueRow
import app.mauri.sukrara.core.designsystem.R as DsR

/**
 * Which alert a test from Settings › Alert reliability or first-run setup sends: the first alert that is on and rings
 * like an alarm (the one that has to break through Do Not Disturb), else the first that is on, in the alerts' order
 * (very low first); null while alerts are off or none is on (nothing would ring tonight either).
 */
object TestAlert {
    fun kind(settings: AlertSettings): AlertKind? {
        if (!settings.on) return null
        val on = AlertKind.entries.filter { settings.rule(it).on }
        return on.firstOrNull { settings.rule(it).alarm } ?: on.firstOrNull()
    }
}

/**
 * *Send a test alert*: [kind]'s real notification on its channel, marked as a test ([AlertPlayback.sendTest]: no reading,
 * no actions, its own slot, nothing changes). [summary] says what it sends; after a tap it says where to look (a polite
 * live region, so TalkBack hears it). Disabled with [disabledSummary] while there is nothing to test ([kind] null).
 */
@Composable
fun TestAlertRow(
    settings: AlertSettings,
    playback: AlertPlayback,
    kind: AlertKind?,
    summary: String,
    disabledSummary: String,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    var sent by rememberSaveable { mutableStateOf(false) }
    ValueRow(
        stringResource(R.string.st_send_test),
        when {
            kind == null -> disabledSummary
            sent -> stringResource(R.string.st_send_test_sent)
            else -> summary
        },
        {
            if (kind != null) {
                haptics.confirm()
                playback.sendTest(kind, settings)
                sent = true
            }
        },
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("send-test-alert"),
        icon = DsR.drawable.ds_ic_science,
        enabled = kind != null,
    )
}

/**
 * Settings › Alert reliability's *Check it now*: the same real test alert as each alert's page, for [TestAlert.kind],
 * so the person can hear it get through Do Not Disturb without opening an alert's page.
 */
@Composable
internal fun ReliabilityTestRow(
    settings: AlertSettings,
    playback: AlertPlayback,
) {
    val kind = TestAlert.kind(settings)
    TestAlertRow(
        settings,
        playback,
        kind,
        summary = kind?.let { stringResource(R.string.st_reliability_test_summary, kindInSentence(it)) }.orEmpty(),
        disabledSummary = stringResource(R.string.st_reliability_test_none),
    )
}
