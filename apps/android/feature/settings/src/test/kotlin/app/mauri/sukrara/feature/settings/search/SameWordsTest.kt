// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.search

import app.mauri.sukrara.feature.settings.R
import app.mauri.sukrara.feature.settings.SettingsRoute
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A row shown on more than one kind of page says what it does on each (the owner, 2026-11-15: Graph's *Sensor time
 * left* row reused in Sensor end, under *After the end*, without words for that page). THE index lists every row with
 * its page and group, and `SettingsIndexTest` checks it against the rendered pages, so a row reused anywhere in
 * Settings is here: when the same title is on pages of two kinds, the two must differ in their group (a group title
 * that says where or what it is on that page) or the row gets its own title there. Kinds: one alert's page and
 * another's are one kind (the page's name says which alert); Alerts and Night alerts are two.
 *
 * The same words on two kinds of page are fine only for different settings the page itself names ([differentSettings],
 * each with why). See also links (they open the other page) and the overview's rows (the pages) aren't rows of a setting.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SameWordsTest {
    /** Titles that are different settings on each kind of page, with why the page's own words are enough. */
    private val differentSettings: Map<Int, String> =
        buildMap {
            for (kind in listOf(
                R.string.st_kind_very_low,
                R.string.st_kind_low,
                R.string.st_kind_high,
                R.string.st_kind_very_high,
                R.string.st_kind_extremely_high,
                R.string.st_kind_falling_fast,
                R.string.st_kind_rising_fast,
                R.string.st_kind_no_data,
            )) {
                put(kind, "Alerts lists each alert, Night alerts each alert's night rule: the page says which")
            }
            put(R.string.st_threshold, "an alert's threshold, and its night one on a page titled ‹alert› at night")
            put(R.string.st_repeat, "an alert's reminder, and its night one on a page titled ‹alert› at night")
        }

    /** The page's kind: the route without its argument (one alert's page and another's are one kind). */
    private fun kind(on: SearchTarget): Any =
        when (on) {
            is SearchTarget.Other -> on.page
            is SearchTarget.Page -> on.route::class
        }

    private fun string(id: Int?) = id?.let { RuntimeEnvironment.getApplication().getString(it) }

    @Test
    fun aRowOnTwoKindsOfPageHasWordsForEach() {
        val rows =
            SettingsIndex.entries.filter {
                it.group != R.string.st_see_also && it.on != SearchTarget.Page(SettingsRoute.Overview)
            }
        val problems = mutableListOf<String>()
        for ((title, places) in rows.groupBy { it.title }) {
            if (title in differentSettings) continue
            for ((i, a) in places.withIndex()) {
                for (b in places.drop(i + 1)) {
                    if (kind(a.on) == kind(b.on) || a.group != b.group) continue
                    problems +=
                        "\"${string(title)}\" (group \"${string(a.group)}\") on ${a.on} and on ${b.on}: give it a group " +
                        "that says what it is there, or its own title there"
                }
            }
        }
        assertTrue(problems.distinct().joinToString("\n"), problems.isEmpty())
        val stale =
            differentSettings.keys.filter { t ->
                rows
                    .filter { it.title == t }
                    .map { kind(it.on) }
                    .distinct()
                    .size < 2
            }
        assertTrue("differentSettings lists titles no longer on two kinds of page: ${stale.map(::string)}", stale.isEmpty())
    }
}
