// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.integrations

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.designsystem.rows.RowIconRes

/**
 * The row icons shown more than once on the screen (as drawable names). Icons are there to tell rows apart and read
 * faster (the owner, 2026-11-15: "it is pointless to have the same icon repeated so many times"), so a page shows each
 * at most once: a list of things of one kind has none.
 */
internal fun ComposeContentTestRule.repeatedRowIcons(): Set<String> {
    val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
    return onAllNodes(SemanticsMatcher.keyIsDefined(RowIconRes), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .map { it.config[RowIconRes] }
        .groupingBy { it }
        .eachCount()
        .filterValues { it > 1 }
        .keys
        .map { resources.getResourceEntryName(it) }
        .toSet()
}
