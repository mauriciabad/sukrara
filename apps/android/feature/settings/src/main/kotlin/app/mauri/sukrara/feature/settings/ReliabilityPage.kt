// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.core.model.AlertReadiness
import app.mauri.sukrara.core.model.AlertRequirement
import app.mauri.sukrara.core.model.UnlockedSound
import app.mauri.sukrara.designsystem.rows.CardRow
import app.mauri.sukrara.designsystem.rows.DndChoice
import app.mauri.sukrara.designsystem.rows.FooterText
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.rows.UnlockedSoundAnswer
import app.mauri.sukrara.designsystem.rows.nameRes
import app.mauri.sukrara.designsystem.rows.rememberCopyAlertReport
import app.mauri.sukrara.designsystem.rows.whyRes
import app.mauri.sukrara.core.designsystem.R as DsR

/**
 * Alert reliability: the same checklist as home's row and the setup step, every item with its state
 * and, while missing, a Fix button that opens the system screen for it (a failed alert step: *Copy report*). Two items
 * carry their own controls: *Do Not Disturb* (never a fault: where Sukrara stands, and one button to change it, #184)
 * and *Sound while the phone is unlocked* (a test alert and what it did, #183). The page refreshes when it comes back.
 * *Check it now* sends the real test alert ([TestAlert]; shown once [alerts] are read).
 */
@Composable
internal fun ReliabilityContent(
    readiness: AlertReadiness,
    onFix: (AlertRequirement) -> Unit,
    onAnswerUnlockedSound: (UnlockedSound) -> Unit = {},
    onTestUnlockedSound: () -> Boolean = { false },
    onLetThroughDnd: (Boolean) -> Unit = {},
    alerts: AlertSettings? = null,
    playback: AlertPlayback = AlertPlayback.None,
    onBack: () -> Unit,
) {
    SettingsPage(stringResource(R.string.st_reliability_title), onBack) {
        if (readiness.ready) {
            StateStrip(stringResource(R.string.st_reliability_ready), emptyList())
        }
        val copyReport = rememberCopyAlertReport(readiness)
        SettingsGroup {
            // a failed alert step is a state of the moment, not a setting: listed only while it lasts
            for (requirement in AlertRequirement.entries.filter { it.inSetup || it in readiness.missing }) {
                val missing = requirement in readiness.missing
                // a choice has no OK / Missing: its statement says where it stands
                val state =
                    when {
                        requirement == AlertRequirement.DoNotDisturb -> null
                        !missing -> R.string.st_req_ok
                        requirement == AlertRequirement.AlertDelivery -> R.string.st_req_failed
                        else -> R.string.st_req_missing
                    }
                val titleText = stringResource(requirement.nameRes())
                CardRow {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(titleText, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        if (state != null) {
                            Text(
                                stringResource(state),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (missing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Text(
                        stringResource(requirement.whyRes()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (requirement == AlertRequirement.DoNotDisturb) {
                        DndChoice(readiness.dnd, onLetThroughDnd)
                    } else if (requirement == AlertRequirement.SoundWhenUnlocked) {
                        // answered or not, the answer stays changeable here (test again, or the other answer)
                        UnlockedSoundAnswer(
                            readiness.unlockedSound,
                            readiness.soundTest,
                            onTestUnlockedSound,
                            { onFix(requirement) },
                            onAnswerUnlockedSound,
                        )
                    } else if (missing && requirement == AlertRequirement.AlertDelivery) {
                        FilledTonalButton(onClick = copyReport) { Text(stringResource(DsR.string.ds_copy_report)) }
                    } else if (missing) {
                        val fixLabel = stringResource(R.string.st_fix_item, titleText)
                        FilledTonalButton(
                            onClick = { onFix(requirement) },
                            modifier = Modifier.semantics { contentDescription = fixLabel },
                        ) {
                            Text(stringResource(R.string.st_fix))
                        }
                    }
                }
            }
        }
        if (alerts != null) {
            SettingsGroup(title = stringResource(R.string.st_reliability_test_title)) { ReliabilityTestRow(alerts, playback) }
        }
        FooterText(stringResource(R.string.st_reliability_footer))
    }
}
