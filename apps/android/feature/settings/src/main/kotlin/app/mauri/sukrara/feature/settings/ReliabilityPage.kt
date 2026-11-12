// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.semantics
import app.mauri.sukrara.alerts.AlertSettings
import app.mauri.sukrara.core.model.AlertReadiness
import app.mauri.sukrara.core.model.AlertRequirement
import app.mauri.sukrara.core.model.UnlockedSound
import app.mauri.sukrara.designsystem.rows.ActionRow
import app.mauri.sukrara.designsystem.rows.CardRow
import app.mauri.sukrara.designsystem.rows.DndChoice
import app.mauri.sukrara.designsystem.rows.FooterText
import app.mauri.sukrara.designsystem.rows.InfoRow
import app.mauri.sukrara.designsystem.rows.NavigationRow
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.rows.UnlockedSoundAnswer
import app.mauri.sukrara.designsystem.rows.nameRes
import app.mauri.sukrara.designsystem.rows.rememberCopyAlertReport
import app.mauri.sukrara.designsystem.rows.whyRes
import com.android.settingslib.spa.widget.preference.StatusBannerLevel
import com.android.settingslib.spa.widget.preference.StatusBannerPreference
import com.android.settingslib.spa.widget.preference.StatusBannerPreferenceModel
import com.android.settingslib.spa.widget.ui.SettingsBody
import com.android.settingslib.spa.widget.ui.SettingsTitle
import app.mauri.sukrara.core.designsystem.R as DsR

/**
 * Alert reliability: the same checklist as home's row and the setup step, every item an SPA row with its state (a
 * check, or the error icon and colour while missing) and, while missing, the row opens the system screen for it
 * (TalkBack: "double tap to fix"; a failed alert step: copies the report). SPA's status banner says when all is on.
 * Two items carry their own controls: *Do Not Disturb* (never a fault: where Sukrara stands, and one button to change
 * it, #184) and *Sound while the phone is unlocked* (a test alert and what it did, #183). The page refreshes when it comes back.
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
        if (readiness.ready) ReadyBanner()
        val copyReport = rememberCopyAlertReport(readiness)
        SettingsGroup {
            // a failed alert step is a state of the moment, not a setting: listed only while it lasts
            for (requirement in AlertRequirement.entries.filter { it.inSetup || it in readiness.missing }) {
                val missing = requirement in readiness.missing
                val title = stringResource(requirement.nameRes())
                val why = stringResource(requirement.whyRes())
                when {
                    requirement == AlertRequirement.DoNotDisturb -> {
                        // a choice, never a fault: its statement says where it stands, its button changes it
                        ControlRow(title, why) { DndChoice(readiness.dnd, onLetThroughDnd) }
                    }

                    requirement == AlertRequirement.SoundWhenUnlocked -> {
                        // answered or not, the answer stays changeable here (test again, or the other answer)
                        val state = stringResource(if (missing) R.string.st_req_missing else R.string.st_req_ok)
                        ControlRow(title, "$state · $why", warning = missing) {
                            UnlockedSoundAnswer(
                                readiness.unlockedSound,
                                readiness.soundTest,
                                onTestUnlockedSound,
                                { onFix(requirement) },
                                onAnswerUnlockedSound,
                            )
                        }
                    }

                    !missing -> {
                        InfoRow(title, summary = stringResource(R.string.st_req_ok) + " · " + why, icon = DsR.drawable.ds_ic_check_circle)
                    }

                    // a failed alert step (an alert that couldn't be shown, or another step): its way out is the report
                    requirement == AlertRequirement.AlertShown || requirement == AlertRequirement.AlertDelivery -> {
                        ActionRow(
                            title,
                            copyReport,
                            summary = stringResource(R.string.st_req_failed) + " · " + why,
                            icon = DsR.drawable.ds_ic_error,
                            warning = true,
                            onClickLabel = stringResource(DsR.string.ds_copy_report),
                        )
                    }

                    else -> {
                        NavigationRow(
                            title,
                            { onFix(requirement) },
                            summary = stringResource(R.string.st_req_missing) + " · " + why,
                            icon = DsR.drawable.ds_ic_error,
                            warning = true,
                            onClickLabel = stringResource(R.string.st_fix),
                        )
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

/** SPA's StatusBannerPreference at the top while everything alerts need is on (Safety Center's "all good"). */
@Composable
private fun ReadyBanner() {
    val title = stringResource(R.string.st_reliability_ready)
    val check = ImageVector.vectorResource(DsR.drawable.ds_ic_check_circle)
    StatusBannerPreference(
        object : StatusBannerPreferenceModel {
            override val title = title
            override val icon: @Composable () -> ImageVector = { check }
            override val iconLevel = StatusBannerLevel.LOW
        },
    )
}

/**
 * An item with its own controls (Do Not Disturb's choice, the unlocked-sound test) as one row of the group: SPA's
 * title and body, then [controls]. [warning]: the body in the error colour (the item is missing).
 */
@Composable
private fun ControlRow(
    title: String,
    summary: String,
    warning: Boolean = false,
    controls: @Composable () -> Unit,
) {
    CardRow {
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            SettingsTitle(title)
            if (warning) {
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            } else {
                SettingsBody(summary)
            }
        }
        controls()
    }
}
