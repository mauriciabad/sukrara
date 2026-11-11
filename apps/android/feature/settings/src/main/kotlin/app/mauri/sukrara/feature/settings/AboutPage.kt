// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mauri.sukrara.designsystem.rows.FooterText
import app.mauri.sukrara.designsystem.rows.InfoRow
import app.mauri.sukrara.designsystem.rows.NavigationRow
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import app.mauri.sukrara.core.designsystem.R as DsR

/** The website (docs/, published by .github/workflows/website.yml). */
const val SITE_URL = "https://sukrara.mauri.app"

/** The privacy policy on the site (docs/privacy.md): what Sukrara stores and sends, and where. */
const val PRIVACY_URL = "$SITE_URL/privacy"

/** The Android page's *Versions* section (docs/android/index.md): Google Play and GitHub, what differs, where to get each. */
const val VERSIONS_URL = "$SITE_URL/android/#versions"

/**
 * Which version of the app this is (the app module's flavor, docs/20-publishing.md › Flavors): the same app and key,
 * updated differently. About names it next to the version number and links to [VERSIONS_URL].
 */
enum class Distribution(
    /** About's version summary, with the version number as its argument. */
    @StringRes val version: Int,
) {
    /** The `full` flavor: GitHub releases or Obtainium; About checks GitHub for a newer release. */
    GitHub(R.string.st_version_github),

    /** The `play` flavor: Google Play updates it; no update check of its own. */
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

/**
 * Checks for an update once when the About page opens (and on *Check again*); never polls. Without [releases] (the
 * Google Play build: Play brings the updates) there is no check and [state] stays null.
 */
class AboutViewModel(
    private val releases: ReleasesSource?,
    val installed: String,
    private val prerelease: Boolean,
) : ViewModel() {
    private val _state = MutableStateFlow<UpdateState?>(null)
    val state: StateFlow<UpdateState?> = _state.asStateFlow()

    init {
        check()
    }

    fun check() {
        val source = releases ?: return
        _state.value = UpdateState.Checking
        viewModelScope.launch { _state.value = UpdateCheck.state(source.fetch(), installed, prerelease) }
    }
}

/**
 * About: the version and which one it is ([distribution]: Google Play or GitHub), the update check (none when
 * [update] is null: the Google Play build), *About the versions* (the website's explanation), Sukrara on other devices
 * (the website's page per app), report a problem, source code, privacy policy, and the reminder that Sukrara isn't a
 * medical device (the same sentence as setup's welcome; Play's health-app policy, the Mac's About too).
 */
@Composable
fun AboutContent(
    installed: String,
    distribution: Distribution,
    update: UpdateState?,
    onCheckAgain: () -> Unit,
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
            when (update) {
                null -> {}

                UpdateState.Checking -> {
                    InfoRow(stringResource(R.string.st_update_checking), icon = DsR.drawable.ds_ic_update)
                }

                UpdateState.UpToDate -> {
                    NavigationRow(
                        stringResource(R.string.st_update_none),
                        onCheckAgain,
                        summary = stringResource(R.string.st_update_check_again),
                        icon = DsR.drawable.ds_ic_check_circle,
                    )
                }

                is UpdateState.Available -> {
                    NavigationRow(
                        stringResource(R.string.st_update_available, update.release.tag.removePrefix("v")),
                        { context.openUrl(update.release.url) },
                        summary = stringResource(if (update.release.prerelease) R.string.st_update_prerelease else R.string.st_update_open),
                        icon = DsR.drawable.ds_ic_download,
                    )
                }

                UpdateState.Offline -> {
                    NavigationRow(
                        stringResource(R.string.st_update_offline),
                        { context.openUrl(UpdateCheck.RELEASES_URL) },
                        summary = stringResource(R.string.st_update_offline_summary),
                        icon = DsR.drawable.ds_ic_download,
                    )
                }
            }
            NavigationRow(
                stringResource(R.string.st_about_versions),
                { context.openUrl(VERSIONS_URL) },
                summary = stringResource(R.string.st_about_versions_summary),
                icon = DsR.drawable.ds_ic_open_in_new,
            )
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
                context.openUrl(UpdateCheck.ISSUES_URL)
            }, summary = stringResource(R.string.st_report_problem_summary), icon = DsR.drawable.ds_ic_bug_report)
            NavigationRow(stringResource(R.string.st_source_code), {
                context.openUrl(UpdateCheck.REPO_URL)
            }, summary = stringResource(R.string.st_license), icon = DsR.drawable.ds_ic_code)
            NavigationRow(stringResource(R.string.st_privacy), {
                context.openUrl(PRIVACY_URL)
            }, summary = stringResource(R.string.st_privacy_summary), icon = DsR.drawable.ds_ic_privacy)
        }
        FooterText(stringResource(R.string.st_not_medical))
    }
}
