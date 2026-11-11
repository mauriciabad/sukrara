// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import app.mauri.sukrara.core.model.AppUpdates
import app.mauri.sukrara.feature.settings.Distribution
import app.mauri.sukrara.monitor.RootFeatures
import app.mauri.sukrara.monitor.systemui.SystemUiRoot

/**
 * The Google Play build (docs/20-publishing.md › Flavors): every feature of the GitHub build, root and the LSPosed
 * module in System UI included (the owner's decision of 2026-11-11, #142), but no updater: Play brings the updates
 * (an app on Play may not update itself any other way), so `:update` isn't even a dependency of this flavor: no
 * update check, notification, download or install, no REQUEST_INSTALL_PACKAGES (ManifestTest; verify-apk.sh --play
 * on the bundle from tools/release/play-bundle.sh). If Play rejects the root features, `rootFeatures = null` is the
 * first step of the fallback in docs/20-publishing.md › Flavors.
 */
internal object Store {
    val distribution = Distribution.GooglePlay

    val rootFeatures: RootFeatures.Factory? = SystemUiRoot

    @Suppress("UNUSED_PARAMETER", "FunctionOnlyReturningConstant")
    fun updates(graph: AppGraph): AppUpdates? = null

    @Suppress("UNUSED_PARAMETER")
    fun start(graph: AppGraph) = Unit
}
