// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.designsystem.haptics.rememberHaptics

/** Which taps a [TapsBlockedCard] is about: its title and text. */
enum class BlockedTapsKind(
    val title: Int,
    val text: Int,
) {
    Pen(R.string.st_pens_blocked, R.string.st_pens_blocked_text),
    Sensor(R.string.st_sensor_taps_blocked, R.string.st_sensor_taps_blocked_text),
}

/**
 * Android blocks Sukrara's NFC taps (a pen tap, a sensor scan): the one card the pens page and the sensor page show at
 * the top. Tonal, with the warning in the error colour (nothing failed, the person has to act); *Allow* opens Android's
 * screen. Home shows the same state as a banner with *Turn off*. TalkBack reads the title and text as one node.
 */
@Composable
fun TapsBlockedCard(
    kind: BlockedTapsKind,
    onAllow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Card(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(kind.title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    stringResource(kind.text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = {
                haptics.confirm()
                onAllow()
            }, Modifier.align(Alignment.End)) { Text(stringResource(R.string.st_taps_allow)) }
        }
    }
}
