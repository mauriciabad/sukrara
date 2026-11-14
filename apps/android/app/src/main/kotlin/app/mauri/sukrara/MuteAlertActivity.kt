// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.core.model.Durations
import app.mauri.sukrara.core.model.NavTargets
import app.mauri.sukrara.designsystem.alerts.AlertWords
import app.mauri.sukrara.designsystem.alerts.MuteText
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The alert's lengths over the lock screen: *Mute* of the glucose notification while it is a Live Update, where
 * Android drops the direct reply that offers the lengths as chips (docs/36 §8.5). The same lengths as the chips
 * (Settings › Alerts › the mute lengths, shortest first, in the card's compact words: "15m", "1h"; TalkBack hears
 * "Mute for 15 min"). A length mutes the side and closes; *Cancel*, back or a tap outside closes without muting.
 * Opening it is an answer to the alert (#39), like the notification's other buttons. Not exported: only the
 * notification's own PendingIntent opens it.
 */
class MuteAlertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val kind = intent.getStringExtra(NavTargets.EXTRA_ALERT_KIND)?.let(AlertKind::fromWire)
        if (kind == null) {
            finish()
            return
        }
        val words = AlertWords.of(this)
        lifecycleScope.launch { graph.runningAlerts()?.runtime?.acknowledge(kind.wire) }
        setContent {
            SukraraTheme {
                var minutes by remember { mutableStateOf<List<Int>?>(null) }
                LaunchedEffect(Unit) {
                    minutes =
                        runCatching {
                            graph.alerts.store.settings
                                .first()
                                .muteChoices()
                                .distinct()
                                .sorted()
                        }.getOrDefault(emptyList())
                }
                MuteSheet(
                    side = MuteText.sideName(kind.side, words),
                    minutes = minutes.orEmpty(),
                    compact = { Durations.compact(it.toLong(), words.durations) },
                    spoken = { Durations.text(it.toLong(), words.durations) },
                    onMute = { min ->
                        lifecycleScope.launch {
                            graph.runningAlerts()?.runtime?.mute(kind.wire, min)
                            finish()
                        }
                    },
                    onCancel = ::finish,
                )
            }
        }
    }
}

/** The lengths as tonal buttons in a row that wraps, shortest first; each says its whole meaning to TalkBack. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MuteSheet(
    side: String,
    minutes: List<Int>,
    compact: (Int) -> String,
    spoken: (Int) -> String,
    onMute: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    val haptics = rememberHaptics()
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.app_mute_sheet_title, side)) },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val label = stringResource(R.string.app_mute_sheet_for)
                minutes.forEach { min ->
                    val description = "$label ${spoken(min)}"
                    FilledTonalButton(
                        onClick = {
                            haptics.confirm()
                            onMute(min)
                        },
                        modifier = Modifier.semantics { contentDescription = description },
                    ) { Text(compact(min)) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(android.R.string.cancel)) } },
    )
}
