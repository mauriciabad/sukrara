// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.mauri.sukrara.designsystem.rows.NavigationRow
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.feature.settings.search.Section
import app.mauri.sukrara.feature.settings.search.SectionIcon

/** One *See also* link: another page's [title] (its overview row's), what it holds, its section's icon, how to open it. */
class SeeAlsoLink(
    val title: String,
    val summary: String?,
    val section: Section,
    val open: () -> Unit,
)

/**
 * The foot of a page: the pages people look for next to this one (docs/35 §7, "pages can link to each other"), each a
 * row with that page's own title and overview icon, so it reads as the same page wherever it is reached from. AOSP
 * Settings links pages the same way (a Preference that opens another screen; SettingsLib SPA's
 * `SettingsEntryBuilder.createLinkFrom`). The search index lists each link under its page (`SettingsIndex.link`).
 */
@Composable
fun SeeAlso(links: List<SeeAlsoLink>) {
    if (links.isEmpty()) return
    SettingsGroup(title = stringResource(R.string.st_see_also)) {
        for (l in links) NavigationRow(l.title, l.open, summary = l.summary, leading = { SectionIcon(l.section) })
    }
}

/** The four data pages: each one's foot links the other three (docs/35 §7). */
enum class DataPage { Sharing, Apps, Backup, Files }

/**
 * The data pages' *See also*: every one of Sharing, Share with apps, Backup (the wearer's phone) and Import and export
 * but [here]. Live to people, live to apps, an automatic copy, files you make yourself.
 */
@Composable
fun DataLinks(
    here: DataPage,
    wearer: Boolean,
    open: (DataPage) -> Unit,
) {
    val all =
        listOf(
            DataPage.Sharing to
                SeeAlsoLink(stringResource(R.string.st_sharing), stringResource(R.string.st_sharing_summary), Section.Sharing) {
                    open(DataPage.Sharing)
                },
            DataPage.Apps to
                SeeAlsoLink(stringResource(R.string.st_apps), stringResource(R.string.st_apps_summary), Section.Apps) {
                    open(DataPage.Apps)
                },
            DataPage.Backup to SeeAlsoLink(stringResource(R.string.st_backup_title), null, Section.Backup) { open(DataPage.Backup) },
            DataPage.Files to
                SeeAlsoLink(stringResource(R.string.st_files_title), stringResource(R.string.st_overview_files), Section.ImportExport) {
                    open(DataPage.Files)
                },
        )
    SeeAlso(all.filter { (page, _) -> page != here && (wearer || page != DataPage.Backup) }.map { it.second })
}
