// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import app.mauri.sukrara.feature.settings.Distribution
import app.mauri.sukrara.feature.settings.GitHubReleases
import app.mauri.sukrara.feature.settings.ReleasesSource
import app.mauri.sukrara.monitor.RootFeatures
import app.mauri.sukrara.monitor.systemui.SystemUiRoot

/**
 * The GitHub build (GitHub releases, Obtainium; docs/20-publishing.md › Flavors): root and the LSPosed module in
 * System UI, and About's update check against the GitHub releases. The play flavor's `Store` has no update check.
 */
internal object Store {
    val distribution = Distribution.GitHub

    val rootFeatures: RootFeatures.Factory? = SystemUiRoot

    fun releases(graph: AppGraph): ReleasesSource? = GitHubReleases(graph.data.http, graph.dispatchers.io)
}
