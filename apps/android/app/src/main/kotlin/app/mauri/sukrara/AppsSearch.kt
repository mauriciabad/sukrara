// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import androidx.annotation.StringRes
import app.mauri.sukrara.feature.settings.search.OtherPage
import app.mauri.sukrara.feature.settings.search.SearchEntry
import app.mauri.sukrara.feature.settings.search.SearchTarget
import app.mauri.sukrara.feature.settings.search.Seen
import app.mauri.sukrara.feature.settings.R as SettingsR
import app.mauri.sukrara.integrations.R as IntegrationsR

/**
 * Settings › Share with apps' rows for the settings search (#181: the PoC's search found them): their strings are
 * `:integrations`', which the settings feature can't depend on, so the app lists them (`SettingsDeps.moreSearch`).
 * A result opens Share with apps at its row, flashed. `AppsSearchTest` renders the page (wearer and follower) and checks
 * every row is here with its group, every entry is on the page and flashes there.
 */
object AppsSearch {
    private val apps = SearchTarget.Other(OtherPage.Apps)
    private val trail = listOf(SettingsR.string.st_apps)

    private fun row(
        @StringRes title: Int,
        @StringRes group: Int,
        seen: Seen = Seen.Everyone,
        @StringRes keywords: Int? = null,
    ) = SearchEntry(title, apps, trail, group = group, keywords = keywords, seen = seen)

    val entries: List<SearchEntry> =
        listOf(
            row(IntegrationsR.string.in_lv_title, IntegrationsR.string.in_apps_clinic_group, Seen.Wearer),
            row(IntegrationsR.string.in_tp_title, IntegrationsR.string.in_apps_clinic_group),
            row(IntegrationsR.string.in_ns_title, IntegrationsR.string.in_apps_nightscout_group, Seen.Wearer),
            row(
                IntegrationsR.string.in_apps_xdrip,
                IntegrationsR.string.in_apps_watch_group,
                keywords = IntegrationsR.string.in_apps_xdrip_keywords,
            ),
            row(IntegrationsR.string.in_bc_title, IntegrationsR.string.in_apps_phone_group, Seen.Wearer),
            row(IntegrationsR.string.in_hc_title, IntegrationsR.string.in_apps_phone_group, Seen.Wearer),
            row(IntegrationsR.string.in_nsapi_title, IntegrationsR.string.in_apps_phone_group),
            row(IntegrationsR.string.in_import_title, IntegrationsR.string.in_apps_files_group, Seen.Wearer),
            // Export data opens its own page, the overview's row's too: one result, that one
            SearchEntry(
                IntegrationsR.string.in_export_title,
                apps,
                trail,
                SearchTarget.Other(OtherPage.Export),
                IntegrationsR.string.in_apps_export_summary,
                IntegrationsR.string.in_apps_files_group,
                highlight = null,
            ),
        )
}
