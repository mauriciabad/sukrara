// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * *Update Sukrara* wherever another version of a shared format says this phone is the one to update (docs/48 "When
 * versions differ", decision 3): the GitHub version opens About, whose updater checks at once and offers *Install*;
 * the Google Play version (no updater, [hasUpdater] false) opens Sukrara's Play listing.
 */
internal object AppUpdate {
    fun open(
        context: Context,
        hasUpdater: Boolean,
        openAbout: () -> Unit,
    ) {
        if (hasUpdater) {
            openAbout()
            return
        }
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
