// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.search

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.designsystem.rows.NavigationRow
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.rows.SettingsHighlight
import app.mauri.sukrara.designsystem.rows.SettingsScaffoldPage
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.feature.settings.R
import app.mauri.sukrara.feature.settings.SettingsRoute
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings search: matching (accents, case, word starts, stock Settings' ranking), the index and opening a result. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class SettingsSearchTest {
    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alerts = SearchTarget.Page(SettingsRoute.Alerts)
    private val graph = SearchTarget.Page(SettingsRoute.Graph)

    @After fun clear() = SettingsHighlight.request(null)

    private fun item(
        title: String,
        summary: String? = null,
        where: String = "Alerts",
        target: SearchTarget = alerts,
        keywords: String? = null,
        depth: Int = 1,
    ) = SearchItem(title, summary, where, target, highlight = title, keywords = keywords, depth = depth)

    /** The whole index in this language, as the search lists it. */
    private fun index(
        pens: Boolean = true,
        wearer: Boolean = true,
    ) = SettingsIndex.entries.filter { it.shownOn(pens, wearer) }.map { it.item(context.resources) }

    @Test
    fun foldDropsAccentsAndCase() {
        assertEquals("ultimo azucar", SettingsSearch.fold("Último Azúcar"))
        assertEquals("nino", SettingsSearch.fold("NIÑO"))
    }

    @Test
    fun matchesWordStartsIgnoringAccents() {
        val items = listOf(item("Último tratamiento"), item("Vibración"), item("Mute all"))
        assertEquals(listOf("Último tratamiento"), SettingsSearch.match("ultimo", items).map { it.title })
        assertEquals(listOf("Vibración"), SettingsSearch.match("VIBRA", items).map { it.title })
        // inside a title word doesn't count: "ute" is not a word start
        assertTrue(SettingsSearch.match("ute", items).isEmpty())
        // every query word must match
        assertTrue(SettingsSearch.match("mute vibr", items).isEmpty())
        assertTrue(SettingsSearch.match("   ", items).isEmpty())
    }

    /**
     * Stock Settings' ranks (the PoC's): the title's first word 1, another title word 3, a part of the summary or a
     * keyword 7, the breadcrumb (page, group) 9; a result ranks as its worst word; ties: the shallower row first.
     */
    @Test
    fun ranksLikeStockSettings() {
        val items =
            listOf(
                item("Time shown", where = "Graph › Time shown", target = graph),
                item("Each alert's sound", summary = "Plays the sound"),
                item("Volume", summary = "Each alert's sound", depth = 2),
                item("Pack of sounds"),
                item("Sound pack", depth = 2),
                item("Pens", keywords = "NovoPen soundless"),
            )
        assertEquals(
            listOf("Sound pack", "Each alert's sound", "Pack of sounds", "Pens", "Volume"),
            SettingsSearch.match("sound", items).map { it.title },
        )
        // a part of a summary counts ("ound" in "sound"), never inside a title word
        assertEquals(listOf("Each alert's sound", "Volume"), SettingsSearch.match("ound", items).map { it.title })
        // "graph" is only in where "Time shown" is: still found
        assertEquals(listOf("Time shown"), SettingsSearch.match("graph", items).map { it.title })
        // same rank: the shallower row first
        val tied = listOf(item("Low", depth = 2), item("Low", where = "Settings", depth = 0))
        assertEquals(listOf(0, 2), SettingsSearch.match("low", tied).map { it.depth })
    }

    /**
     * #181 (the owner: "search night, click Night alerts"): a page is one result, the row that opens it; it opens the
     * page through the menus' path (Alerts, then Night alerts), at its top. Its rows say where they are.
     */
    @Test
    fun aPageIsOneResult() {
        val night = SettingsSearch.match("night", index()).filter { it.title == context.getString(R.string.st_night_title) }
        assertEquals(1, night.size)
        val result = night.single()
        assertEquals(SearchTarget.Page(SettingsRoute.NightAlerts), result.target)
        assertNull(result.highlight)
        assertEquals("Alerts", result.where)
        assertEquals(Section.Alerts, result.section)
        assertEquals(listOf(SettingsRoute.Alerts, SettingsRoute.NightAlerts), SettingsIndex.path(SettingsRoute.NightAlerts))
        val starts = SettingsSearch.match("night starts", index()).first()
        assertEquals("Starts", starts.title)
        assertEquals("Starts", starts.highlight)
        assertEquals("Alerts › Night alerts", starts.where)
    }

    /** The group's name finds its rows and shows in the breadcrumb; keywords find rows they never show on. */
    @Test
    fun groupsAndKeywordsFindRows() {
        val quiet = SettingsSearch.match("quiet treatments", index())
        assertTrue(quiet.map { it.title }.containsAll(listOf("Quiet highs after insulin", "Quiet lows after carbs")))
        assertEquals("Alerts › Quiet after treatments", quiet.first { it.title == "Quiet lows after carbs" }.where)
        assertEquals(context.getString(R.string.st_pens_title), SettingsSearch.match("novopen", index()).first().title)
        assertTrue(SettingsSearch.match("novopen", index(pens = false)).isEmpty())
        assertEquals(context.getString(R.string.st_lock_time), SettingsSearch.match("timer", index()).first().title)
    }

    /** Every settings page with rows of its own is searchable. */
    @Test
    fun everyPageHasEntries() {
        val pages =
            listOf(
                SettingsRoute.Alerts,
                SettingsRoute.NightAlerts,
                SettingsRoute.Ranges,
                SettingsRoute.Graph,
                SettingsRoute.GraphMarks,
                SettingsRoute.LockScreen,
                SettingsRoute.Pens,
                SettingsRoute.Reliability,
                SettingsRoute.StatusBar,
                SettingsRoute.SensorEnd,
                SettingsRoute.About,
                SettingsRoute.ThresholdReview,
                SettingsRoute.Advanced,
                SettingsRoute.SettingsFrom,
            ) + AlertKind.entries.map(SettingsRoute::AlertKindPage) + AlertKind.entries.map(SettingsRoute::NightKindPage)
        val targets = SettingsIndex.entries.map { it.target }.toSet()
        for (p in pages) assertTrue("$p has no search entries", SearchTarget.Page(p) in targets)
        for (o in OtherPage.entries) assertTrue("$o has no search entry", SearchTarget.Other(o) in targets)
    }

    /** #134: Profile and Backup are the wearer's rows, Settings from a follower's; pens only where pens are read. */
    @Test
    fun rowsAreFilteredByRole() {
        fun targets(
            pens: Boolean,
            wearer: Boolean,
        ) = SettingsIndex.entries
            .filter { it.shownOn(pens, wearer) }
            .map { it.target }
            .toSet()
        val wearer = targets(pens = false, wearer = true)
        val follower = targets(pens = false, wearer = false)
        val profile = SearchTarget.Other(OtherPage.Profile)
        val backup = SearchTarget.Other(OtherPage.Backup)
        val from = SearchTarget.Page(SettingsRoute.SettingsFrom)
        assertTrue(profile in wearer && backup in wearer && from !in wearer)
        assertTrue(profile !in follower && backup !in follower && from in follower)
        assertFalse(SearchTarget.Page(SettingsRoute.Pens) in wearer)
        assertTrue(SearchTarget.Page(SettingsRoute.Pens) in targets(pens = true, wearer = true))
    }

    /**
     * The Google Play build (root features, no update check: #142) finds no *Check again*; a build without root (Play's
     * fallback) no root rows either; every build finds the privacy policy and *About the versions*.
     */
    @Test
    fun rowsOfTheOtherBuildAreNotFound() {
        fun titles(
            build: Set<BuildFeature>,
        ) = SettingsIndex.entries.filter { it.shownOn(pens = true, wearer = true, build) }.map { it.title }
        val noRoot = titles(emptySet())
        val play = titles(setOf(BuildFeature.Root))
        val full = titles(BuildFeature.entries.toSet())
        val root = listOf(R.string.st_root_value, R.string.st_root_colour, R.string.st_root_placement, R.string.st_root_hide_icon)
        for (t in root) assertTrue(t !in noRoot && t in play && t in full)
        assertTrue(R.string.st_update_check_again !in play && R.string.st_update_check_again in full)
        for (t in listOf(R.string.st_privacy, R.string.st_statusbar_chip, R.string.st_about_version, R.string.st_about_versions)) {
            assertTrue(t in noRoot && t in play && t in full)
        }
    }

    /** #134: a hit on an alert's own row opens that alert's page ("Remind every · Alerts › Low › Alert when"). */
    @Test
    fun alertRowsOpenTheirAlert() {
        val hits = SettingsSearch.match("low remind", index())
        val low = hits.first { it.target == SearchTarget.Page(SettingsRoute.AlertKindPage(AlertKind.LOW)) }
        assertEquals("Remind every", low.title)
        assertEquals("Alerts › Low › Alert when", low.where)
        assertEquals(
            listOf(SettingsRoute.Alerts, SettingsRoute.AlertKindPage(AlertKind.LOW)),
            SettingsIndex.path(SettingsRoute.AlertKindPage(AlertKind.LOW)),
        )
        // the list's own rows still lead to the list
        assertEquals(alerts, SettingsSearch.match("sound pack", index()).first().target)
    }

    @Test
    fun recentSearchesKeepTheNewestFiveOnce() {
        var recent = emptyList<String>()
        for (q in listOf("alarma", " Vibración ", "graph", "ALARMA", "lock", "pens", "sound")) recent = RecentSearches.add(recent, q)
        // the same query again (case, accents, spaces aside) moves to the top instead of repeating
        assertEquals(listOf("sound", "pens", "lock", "ALARMA", "graph"), recent)
        assertEquals(
            listOf("vibracion", "sound"),
            RecentSearches.add(listOf("Vibración", "sound"), "  vibracion  ").let {
                it.map(SettingsSearch::fold)
            },
        )
        assertEquals(recent, RecentSearches.decode(RecentSearches.encode(recent)))
        assertEquals(emptyList<String>(), RecentSearches.decode(""))
        assertEquals(recent, RecentSearches.add(recent, "   "))
    }

    /** The pill opens the full-screen search: empty, the recent searches; one searches again, Clear empties them. */
    @Test
    fun recentSearchesShowWhenTheSearchOpens() {
        val remembered = mutableListOf<String>()
        var cleared = 0
        val opened = mutableListOf<List<SettingsRoute>>()
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                SettingsSearchBar(
                    onOpen = { opened += it },
                    onOpenOther = {},
                    recent = RecentSearch(listOf("sound pack"), remember = { remembered += it }, clear = { cleared++ }),
                )
            }
        }
        compose.onNodeWithText("Recent searches").assertDoesNotExist()
        compose.onAllNodesWithText("Search settings").onLast().performClick()
        compose.onNodeWithText("Recent searches").assertIsDisplayed()
        compose.onNodeWithText("Clear").performClick()
        assertEquals(1, cleared)
        compose.onNodeWithText("sound pack").performClick()
        // the recent query is typed: its results show, the recent list goes
        compose.onNodeWithText("Recent searches").assertDoesNotExist()
        compose.onNodeWithText("Sound pack").performClick()
        assertEquals(listOf(listOf(SettingsRoute.Alerts)), opened)
        assertEquals(listOf("sound pack"), remembered)
    }

    /**
     * #181: a result opens its page through the menus' path at its row (SPA's highlight: scrolled into view, flashed),
     * as stock Settings' search does; a result that is a page opens that page at its top.
     */
    @Test
    fun aResultOpensItsPageAtItsRow() {
        var opened by mutableStateOf<List<SettingsRoute>?>(null)
        compose.setContent {
            SukraraTheme(dark = false, dynamic = false) {
                if (opened == null) {
                    SettingsSearchBar(onOpen = { opened = it }, onOpenOther = {}, initialQuery = "")
                } else {
                    SettingsScaffoldPage("Night alerts", onBack = {}) {
                        SettingsGroup {
                            for (i in 1..30) NavigationRow("Row $i", onClick = {})
                            NavigationRow("Starts", onClick = {})
                        }
                    }
                }
            }
        }
        compose.onAllNodes(hasSetTextAction()).onLast().performTextInput("night starts")
        compose.onNodeWithText("Starts").performClick()
        assertEquals(listOf(SettingsRoute.Alerts, SettingsRoute.NightAlerts), opened)
        compose.onNodeWithText("Starts").assertIsDisplayed()
        assertEquals("Starts", SettingsHighlight.shown)
        // the overview's rows and the rows that open a page are pages: no row to flash
        assertTrue(SettingsIndex.entries.filter { it.on == SearchTarget.Page(SettingsRoute.Overview) }.all { it.highlight == null })
        assertTrue(SettingsIndex.entries.filter { it.target != it.on }.all { it.highlight == null })
    }

    /** The index's texts read as the pages show them: no format arguments left in (en and es). */
    @Test
    fun entriesHaveNoFormatArguments() = checkTexts()

    @Test
    @Config(qualifiers = "es")
    fun entriesHaveNoFormatArgumentsInSpanish() {
        checkTexts()
        // an accented title found without accents
        assertFalse(SettingsSearch.match("vibracion", index()).isEmpty())
    }

    private fun checkTexts() {
        for (e in SettingsIndex.entries) {
            for (id in listOfNotNull(e.title, e.summary, e.group, e.keywords, e.highlight) + e.trail) {
                val text = context.getString(id)
                assertTrue("${context.resources.getResourceEntryName(id)} is empty", text.isNotBlank())
                assertFalse("${context.resources.getResourceEntryName(id)} has a format argument: $text", "%1$" in text || "%d" in text)
            }
        }
    }
}
