// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import app.mauri.sukrara.designsystem.rows.RowIconRes

/**
 * The row icons shown more than once on the screen (as drawable names). Icons are there to tell rows apart and read
 * faster (the owner, 2026-11-15: "it is pointless to have the same icon repeated so many times"), so a page shows each
 * at most once: a list of things of one kind (reminders, pens) has none.
 */
internal fun ComposeContentTestRule.repeatedRowIcons(): Set<String> {
    val resources =
        androidx.test.core.app.ApplicationProvider
            .getApplicationContext<android.content.Context>()
            .resources
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

/** Settings › Rings is being redesigned from scratch (its own round checks it). */
internal fun ringsPage(name: String) = name.startsWith("rings")
