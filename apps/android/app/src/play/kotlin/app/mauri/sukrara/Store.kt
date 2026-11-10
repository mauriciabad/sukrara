// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import app.mauri.sukrara.feature.settings.ReleasesSource
import app.mauri.sukrara.monitor.RootFeatures

/**
 * The Google Play build (docs/20-publishing.md › Flavors): no root, no LSPosed module in System UI (Device and Network
 * Abuse: no interfering with the system or other apps) and no update check (Play brings the updates; an app on Play
 * may not update itself any other way). Nothing references that code here, so R8 leaves it out of the bundle;
 * tools/release/play-bundle.sh checks it isn't there.
 */
internal object Store {
    val rootFeatures: RootFeatures.Factory? = null

    @Suppress("UNUSED_PARAMETER")
    fun releases(graph: AppGraph): ReleasesSource? = null
}
