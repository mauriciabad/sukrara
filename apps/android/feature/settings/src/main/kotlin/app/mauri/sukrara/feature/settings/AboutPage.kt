// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.mauri.sukrara.core.model.UpdateFailure
import app.mauri.sukrara.core.model.UpdateRelease
import app.mauri.sukrara.core.model.UpdateState
import app.mauri.sukrara.designsystem.rows.ActionRow
import app.mauri.sukrara.designsystem.rows.CardRow
import app.mauri.sukrara.designsystem.rows.FooterText
import app.mauri.sukrara.designsystem.rows.InfoRow
import app.mauri.sukrara.designsystem.rows.NavigationRow
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.rows.SwitchRow
import app.mauri.sukrara.core.designsystem.R as DsR

/** The website (docs/, published by .github/workflows/website.yml). */
const val SITE_URL = "https://sukrara.mauri.app"

/** The privacy policy on the site (docs/privacy.md): what Sukrara stores and sends, and where. */
const val PRIVACY_URL = "$SITE_URL/privacy"

/** The Android page's *Versions* section (docs/android/index.md): Google Play and GitHub, what differs, where to get each. */
const val VERSIONS_URL = "$SITE_URL/android/#versions"

/** The project on GitHub: About's *Source code*, *Report a problem* and the releases page. */
object ProjectLinks {
    const val REPO_URL = "https://github.com/mauriciabad/sukrara"
    const val RELEASES_URL = "$REPO_URL/releases"
    const val ISSUES_URL = "$REPO_URL/issues"
}

/**
 * Which version of the app this is (the app module's flavor, docs/20-publishing.md › Flavors): the same app and key,
 * updated differently. About names it next to the version number and links to [VERSIONS_URL].
 */
enum class Distribution(
    /** About's version summary, with the version number as its argument. */
    @StringRes val version: Int,
) {
    /** The `full` flavor: GitHub releases or Obtainium; it updates itself (`:update`, #188). */
    GitHub(R.string.st_version_github),

    /** The `play` flavor: Google Play updates it; no updater of its own. */
    GooglePlay(R.string.st_version_play),
}

/** One app of Sukrara on another device: its row under *Sukrara on other devices* and its page on the website. */
data class OtherDevice(
    @StringRes val name: Int,
    @StringRes val summary: Int,
    @DrawableRes val icon: Int,
    val url: String,
)

/**
 * *Sukrara on other devices* (#179): each opens that app's page on the website (what it does, how to get it), never a
 * download. The Mac page is docs/mac/index.md.
 */
val OTHER_DEVICES =
    listOf(
        OtherDevice(R.string.st_device_mac, R.string.st_device_mac_summary, DsR.drawable.ds_ic_open_in_new, "$SITE_URL/mac/"),
    )

/** About's updater (the GitHub version, #188): its actions and its two switches (schema `update.*`). */
class AboutUpdates(
    val check: () -> Unit,
    val update: () -> Unit,
    val cancel: () -> Unit,
    val confirm: () -> Unit,
    val automaticChecks: Boolean,
    val automaticInstall: Boolean,
    val setAutomaticChecks: (Boolean) -> Unit,
    val setAutomaticInstall: (Boolean) -> Unit,
)

/**
 * About: the version and which one it is ([distribution]: Google Play or GitHub), the GitHub version's update ([update]
 * as the updater says, its actions and switches in [updates]; none of it in the Google Play build), *About the
 * versions* (the website's explanation), Sukrara on other devices (the website's page per app), report a problem,
 * source code, privacy policy, and the reminder that Sukrara isn't a medical device (the same sentence as setup's
 * welcome; Play's health-app policy, the Mac's About too).
 */
@Composable
fun AboutContent(
    installed: String,
    distribution: Distribution,
    update: UpdateState?,
    updates: AboutUpdates?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    SettingsPage(stringResource(R.string.st_about_title), onBack) {
        SettingsGroup {
            InfoRow(
                stringResource(R.string.st_about_version),
                summary = stringResource(distribution.version, installed),
                icon = DsR.drawable.ds_ic_info,
            )
            if (updates != null) UpdateRows(installed, update, updates)
            NavigationRow(
                stringResource(R.string.st_about_versions),
                { context.openUrl(VERSIONS_URL) },
                summary = stringResource(R.string.st_about_versions_summary),
                icon = DsR.drawable.ds_ic_open_in_new,
            )
        }
        if (updates != null) {
            SettingsGroup(title = stringResource(R.string.st_update_group)) {
                SwitchRow(
                    stringResource(R.string.st_update_auto_checks),
                    updates.automaticChecks,
                    updates.setAutomaticChecks,
                    summary = stringResource(R.string.st_update_auto_checks_summary),
                    icon = DsR.drawable.ds_ic_update,
                )
                // like the Mac: installing by itself needs the daily check, so it is dimmed while that is off
                SwitchRow(
                    stringResource(R.string.st_update_auto_install),
                    updates.automaticInstall && updates.automaticChecks,
                    updates.setAutomaticInstall,
                    summary =
                        stringResource(
                            if (updates.automaticChecks) R.string.st_update_auto_install_summary else R.string.st_update_auto_install_off,
                        ),
                    icon = DsR.drawable.ds_ic_download,
                    enabled = updates.automaticChecks,
                )
            }
        }
        SettingsGroup(title = stringResource(R.string.st_other_devices)) {
            for (device in OTHER_DEVICES) {
                NavigationRow(stringResource(device.name), {
                    context.openUrl(device.url)
                }, summary = stringResource(device.summary), icon = device.icon)
            }
        }
        SettingsGroup {
            NavigationRow(stringResource(R.string.st_report_problem), {
                context.openUrl(ProjectLinks.ISSUES_URL)
            }, summary = stringResource(R.string.st_report_problem_summary), icon = DsR.drawable.ds_ic_bug_report)
            NavigationRow(stringResource(R.string.st_source_code), {
                context.openUrl(ProjectLinks.REPO_URL)
            }, summary = stringResource(R.string.st_license), icon = DsR.drawable.ds_ic_code)
            NavigationRow(stringResource(R.string.st_privacy), {
                context.openUrl(PRIVACY_URL)
            }, summary = stringResource(R.string.st_privacy_summary), icon = DsR.drawable.ds_ic_privacy)
        }
        FooterText(stringResource(R.string.st_not_medical))
    }
}

/**
 * The update's rows, one per state (docs/FEATURES.md › Updates (Android, GitHub version), States and exits): each
 * says where the update is and its row does the next step or the way out; *Release notes* while a release is known.
 */
@Composable
private fun UpdateRows(
    installed: String,
    update: UpdateState?,
    actions: AboutUpdates,
) {
    val context = LocalContext.current
    when (update) {
        null -> {}

        UpdateState.Checking -> {
            InfoRow(stringResource(R.string.st_update_checking), icon = DsR.drawable.ds_ic_update)
        }

        UpdateState.UpToDate -> {
            ActionRow(
                stringResource(R.string.st_update_none),
                actions.check,
                summary = stringResource(R.string.st_update_check_again),
                icon = DsR.drawable.ds_ic_check_circle,
            )
        }

        UpdateState.Offline -> {
            ActionRow(
                stringResource(R.string.st_update_offline),
                actions.check,
                summary = stringResource(R.string.st_update_check_again),
                icon = DsR.drawable.ds_ic_update,
            )
            NavigationRow(
                stringResource(R.string.st_update_offline_summary),
                { context.openUrl(ProjectLinks.RELEASES_URL) },
                icon = DsR.drawable.ds_ic_open_in_new,
            )
        }

        is UpdateState.Available -> {
            val r = update.release
            val summary =
                if (r.installable) R.string.st_update_install else R.string.st_update_open
            ActionRow(
                stringResource(R.string.st_update_available, r.version),
                actions.update,
                summary = stringResource(summary),
                icon = DsR.drawable.ds_ic_download,
            )
        }

        is UpdateState.Downloading -> {
            val progress by animateFloatAsState(update.progress ?: 0f, label = "download")
            ActionRow(
                stringResource(R.string.st_update_downloading, update.release.version),
                actions.cancel,
                summary = stringResource(R.string.st_update_cancel_summary),
                icon = DsR.drawable.ds_ic_download,
            )
            CardRow {
                if (update.progress == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator({ progress }, Modifier.fillMaxWidth())
                }
            }
        }

        is UpdateState.Waiting -> {
            ActionRow(
                stringResource(R.string.st_update_ready, update.release.version),
                actions.cancel,
                summary = stringResource(R.string.st_update_waiting_summary),
                icon = DsR.drawable.ds_ic_update,
            )
        }

        is UpdateState.Confirm -> {
            ActionRow(
                stringResource(R.string.st_update_ready, update.release.version),
                actions.confirm,
                summary = stringResource(R.string.st_update_confirm_summary),
                icon = DsR.drawable.ds_ic_download,
            )
            ActionRow(stringResource(R.string.st_update_cancel), actions.cancel, icon = DsR.drawable.ds_ic_close)
        }

        is UpdateState.Installing -> {
            InfoRow(
                stringResource(R.string.st_update_installing, update.release.version),
                summary = stringResource(R.string.st_update_installing_summary),
                icon = DsR.drawable.ds_ic_update,
            )
            CardRow { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }

        is UpdateState.Failed -> {
            ActionRow(
                stringResource(R.string.st_update_failed, update.release.version),
                actions.update,
                summary = stringResource(failure(update.reason), installed),
                icon = DsR.drawable.ds_ic_update,
            )
        }
    }
    release(update)?.let { r ->
        NavigationRow(
            stringResource(R.string.st_update_notes),
            { context.openUrl(r.notesUrl) },
            summary = stringResource(R.string.st_update_notes_summary),
            icon = DsR.drawable.ds_ic_open_in_new,
        )
    }
}

private fun release(state: UpdateState?): UpdateRelease? =
    when (state) {
        is UpdateState.Available -> state.release
        is UpdateState.Downloading -> state.release
        is UpdateState.Waiting -> state.release
        is UpdateState.Confirm -> state.release
        is UpdateState.Installing -> state.release
        is UpdateState.Failed -> state.release
        else -> null
    }

@StringRes
private fun failure(reason: UpdateFailure): Int =
    when (reason) {
        UpdateFailure.Download -> R.string.st_update_failed_download
        UpdateFailure.Hash -> R.string.st_update_failed_hash
        UpdateFailure.Signature -> R.string.st_update_failed_signature
        UpdateFailure.Install -> R.string.st_update_failed_install
    }
