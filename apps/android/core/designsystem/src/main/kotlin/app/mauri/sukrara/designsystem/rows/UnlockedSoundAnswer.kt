// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.rows

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.core.designsystem.R
import app.mauri.sukrara.core.model.SoundTest
import app.mauri.sukrara.core.model.UnlockedSound
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.designsystem.motion.LocalReducedMotion
import app.mauri.sukrara.designsystem.motion.Motion

/**
 * The answer to [app.mauri.sukrara.core.model.AlertRequirement.SoundWhenUnlocked], the same on setup's permission step
 * and Settings › Alert reliability (#183): ask from what the person hears, not from reading Android's settings.
 * *Send a test alert* (the real alert, marked as a test, with Sukrara's own unlocked sound off: it does what Android
 * does) while the phone is unlocked; then *I heard it* or *It only vibrated* (Sukrara then plays the alert sound itself
 * while the phone is unlocked). The answers show once a test went out (or an answer exists, to change it); the test is
 * held back while it couldn't show Android's behaviour ([soundTest]: a mode on, the phone on vibrate). *Open Android's
 * setting* stays the way to turn *Vibrate only when unlocked* off, after which the person tests again.
 */
@Composable
fun UnlockedSoundAnswer(
    answer: UnlockedSound,
    soundTest: SoundTest,
    onTest: () -> Boolean,
    onOpen: () -> Unit,
    onAnswer: (UnlockedSound) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val reduced = LocalReducedMotion.current
    var sent by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val hint =
            when (soundTest) {
                SoundTest.Ready -> if (sent) R.string.ds_unlocked_sent else R.string.ds_unlocked_steps
                SoundTest.NoAlert -> R.string.ds_unlocked_no_alert
                SoundTest.ModeOn -> R.string.ds_unlocked_mode_on
                SoundTest.Silent -> R.string.ds_unlocked_silent
            }
        AnimatedContent(hint, transitionSpec = { Motion.fadeThrough(reduced) }, label = "unlocked-hint") {
            Text(
                stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        FilledTonalButton(
            onClick = {
                haptics.confirm()
                if (onTest()) sent = true
            },
            enabled = soundTest == SoundTest.Ready,
            modifier = Modifier.heightIn(min = 48.dp).testTag("unlocked-test"),
        ) { Text(stringResource(R.string.ds_unlocked_send)) }
        AnimatedVisibility(sent || answer != UnlockedSound.Unchecked) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.ds_unlocked_question), style = MaterialTheme.typography.bodyMedium)
                val choices =
                    listOf(
                        UnlockedSound.Heard to R.string.ds_unlocked_heard,
                        UnlockedSound.OnlyVibrated to R.string.ds_unlocked_vibrated,
                    )
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
                AnimatedContent(answer, transitionSpec = { Motion.fadeThrough(reduced) }, label = "unlocked-answer") { a ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (a) {
                            UnlockedSound.Heard -> {
                                Note(stringResource(R.string.ds_unlocked_heard_note))
                            }

                            UnlockedSound.OnlyVibrated -> {
                                Note(stringResource(R.string.ds_unlocked_vibrated_note))
                                OutlinedButton(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) {
                                    Text(stringResource(R.string.ds_unlocked_open))
                                }
                            }

                            UnlockedSound.Unchecked -> {
                                Unit
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
