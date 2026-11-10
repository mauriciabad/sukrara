// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.rows

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.core.designsystem.R
import app.mauri.sukrara.core.model.UnlockedSound
import app.mauri.sukrara.designsystem.haptics.rememberHaptics

/**
 * The answer to [app.mauri.sukrara.core.model.AlertRequirement.SoundWhenUnlocked], the same on setup's permission step
 * and Settings › Alert reliability: *Open the setting* (Android's cooldown page), then *It's off* or *Keep it on*
 * (Sukrara then plays the alert sound itself while the phone is unlocked). Either answer can be changed at any time;
 * nothing is chosen until the person answers.
 */
@Composable
fun UnlockedSoundAnswer(
    answer: UnlockedSound,
    onOpen: () -> Unit,
    onAnswer: (UnlockedSound) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onOpen) { Text(stringResource(R.string.ds_unlocked_open)) }
        val choices = listOf(UnlockedSound.Off to R.string.ds_unlocked_off, UnlockedSound.AppPlays to R.string.ds_unlocked_app)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            choices.forEachIndexed { i, (value, label) ->
                SegmentedButton(
                    selected = answer == value,
                    onClick = {
                        haptics.confirm()
                        onAnswer(value)
                    },
                    shape = SegmentedButtonDefaults.itemShape(i, choices.size),
                ) { Text(stringResource(label)) }
            }
        }
        if (answer == UnlockedSound.AppPlays) {
            Text(
                stringResource(R.string.ds_unlocked_app_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
