// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.alerts.MuteLine
import app.mauri.sukrara.core.model.NavTargets
import app.mauri.sukrara.designsystem.alerts.AlertWords
import app.mauri.sukrara.designsystem.alerts.MuteText
import app.mauri.sukrara.designsystem.dialog.NumberDialog
import app.mauri.sukrara.designsystem.dialog.NumberInput
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.settings.SettingsSpec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The alert's lengths over the lock screen: *Mute* of the glucose notification while it is a Live Update, where
 * Android drops the direct reply that offers the lengths as chips (docs/36 §8.5). The app's own length dialog, as
 * *Mute alerts* on home and Android's Do Not Disturb duration dialog: a Material 3 dialog with one radio row per
 * length ("For 15 min" … "For 2 h", the schema's presets `actionMute`) and *Custom* for a typed one (THE number input,
 * `NumberDialog`); the person's own first length is the one selected. A length mutes the side and closes; *Cancel*,
 * back or a tap outside closes without muting. Opening it is an answer to the alert (#39), like the notification's
 * other buttons. Not exported: only the notification's own PendingIntent opens it.
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
                var first by remember { mutableStateOf<Int?>(null) }
                LaunchedEffect(Unit) {
                    first =
                        runCatching {
                            graph.alerts.store.settings
                                .first()
                                .actions.muteMin
                        }.getOrDefault(SettingsSpec.ACTION_MUTE.default.roundToInt())
                }
                first?.let { min ->
                    MuteSheet(
                        // "Mute lows" / "Silenciar bajas": the side as the mute lines name it, in the title's lower case
                        side = MuteText.sideName(kind.side, words).lowercase(java.util.Locale.getDefault()),
                        first = min,
                        label = { MuteText.line(MuteLine.Choice(it), words) },
                        onMute = { m ->
                            lifecycleScope.launch {
                                graph.runningAlerts()?.runtime?.mute(kind.wire, m)
                                finish()
                            }
                        },
                        onCancel = ::finish,
                    )
                }
            }
        }
    }
}

/** The lengths dialog: [first] selected, each row "For 30 min" ([label]), *Custom* typed; TalkBack reads the radio rows. */
@Composable
internal fun MuteSheet(
    side: String,
    first: Int,
    label: (Int) -> String,
    onMute: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    NumberDialog(
        stringResource(R.string.app_mute_sheet_title, side),
        NumberInput(SettingsSpec.ACTION_MUTE),
        first.toDouble(),
        { label(it.roundToInt()) },
        onCancel,
    ) { onMute(it.roundToInt()) }
}
