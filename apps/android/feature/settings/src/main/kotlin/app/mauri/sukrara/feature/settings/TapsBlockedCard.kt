// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.mauri.sukrara.designsystem.banner.SettingsBannerCard
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.core.designsystem.R as DsR

/** Which taps a [TapsBlockedCard] is about: its title and text. */
enum class BlockedTapsKind(
    val title: Int,
    val text: Int,
) {
    Pen(R.string.st_pens_blocked, R.string.st_pens_blocked_text),
    Sensor(R.string.st_sensor_taps_blocked, R.string.st_sensor_taps_blocked_text),
}

/**
 * Android blocks Sukrara's NFC taps (a pen tap, a sensor scan): the one banner the pens page and the sensor page show
 * at the top, SPA's SettingsBanner with the warning icon and *Allow* in the error colour (nothing failed, the person has
 * to act); *Allow* opens Android's screen. Home shows the same state as a banner with *Turn off*.
 */
@Composable
fun TapsBlockedCard(
    kind: BlockedTapsKind,
    onAllow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    SettingsBannerCard(
        DsR.drawable.ds_ic_warning,
        stringResource(kind.title),
        stringResource(kind.text),
        modifier,
        action = stringResource(R.string.st_taps_allow),
        onAction = {
            haptics.confirm()
            onAllow()
        },
        tint = MaterialTheme.colorScheme.error,
    )
}
