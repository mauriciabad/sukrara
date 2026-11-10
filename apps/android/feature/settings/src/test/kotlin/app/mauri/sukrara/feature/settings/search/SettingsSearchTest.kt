// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.search

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.mauri.sukrara.alerts.AlertKind
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.feature.settings.SettingsRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings search: matching (accents, case, word starts, ranking) and the index against the pages. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsSearchTest {
    @get:Rule val compose = createComposeRule()

    private val alerts = SearchTarget.Page(SettingsRoute.Alerts)
    private val graph = SearchTarget.Page(SettingsRoute.Graph)

    private fun item(
        title: String,
        summary: String? = null,
        page: String = "Alerts",
        target: SearchTarget = alerts,
    ) = SearchItem(title, summary, page, target)

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
        // inside a word doesn't count: "ute" is not a word start
        assertTrue(SettingsSearch.match("ute", items).isEmpty())
        // every query word must match
        assertTrue(SettingsSearch.match("mute vibr", items).isEmpty())
        assertTrue(SettingsSearch.match("   ", items).isEmpty())
    }

    @Test
    fun titleMatchesComeBeforeSummaryAndPageMatches() {
        val items =
            listOf(
                item("Time shown", page = "Graph", target = graph),
                item("Volume", summary = "Each alert's sound"),
                item("Sound pack"),
            )
        assertEquals(listOf("Sound pack", "Volume"), SettingsSearch.match("sound", items).map { it.title })
        // "graph" is only the page of "Time shown": still found, after title matches
        val withTitle = items + item("Graph", page = "Settings", target = graph)
        assertEquals(listOf("Graph", "Time shown"), SettingsSearch.match("graph", withTitle).map { it.title })
    }

    /** Every settings page with rows of its own is searchable. */
    @Test
    fun everyPageHasEntries() {
        val pages =
            listOf(
                SettingsRoute.Alerts,
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
            ) + AlertKind.entries.map(SettingsRoute::AlertKindPage)
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

    /** #134: a hit on an alert's own row opens that alert's page ("Remind every · In Alerts › Low"), not the list. */
    @Test
    fun alertRowsOpenTheirAlert() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val items =
            SettingsIndex.entries.map { e ->
                val page = listOfNotNull(e.parent, e.page).joinToString(" › ") { context.getString(it) }
                SearchItem(context.getString(e.title), e.summary?.let(context::getString), page, e.target)
            }
        val hits = SettingsSearch.match("low remind", items)
        val low = hits.single { it.target == SearchTarget.Page(SettingsRoute.AlertKindPage(AlertKind.LOW)) }
        assertEquals("Remind every", low.title)
        assertEquals("Alerts › Low", low.page)
        // the list's own rows still lead to the list
        assertEquals(alerts, SettingsSearch.match("sound pack", items).first().target)
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

    /** The field: focused and empty it offers the recent searches; a chip searches again, Clear empties them. */
    @Test
    fun recentSearchesShowOnTheEmptyField() {
        val remembered = mutableListOf<String>()
        var cleared = 0
        val opened = mutableListOf<SettingsRoute>()
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
        compose.onNodeWithText("Search settings").performClick()
        compose.onNodeWithText("Recent searches").assertIsDisplayed()
        compose.onNodeWithText("sound pack").performClick()
        // the chip's query is typed: its results show, the chips go
        compose.onNodeWithText("Recent searches").assertDoesNotExist()
        compose.onNodeWithText("Sound pack").performClick()
        assertEquals(listOf(SettingsRoute.Alerts), opened)
        assertEquals(listOf("sound pack"), remembered)
        // Clear empties them
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNodeWithText("Search settings").performClick()
        compose.onNodeWithText("Clear").performClick()
        assertEquals(1, cleared)
    }

    /** The index's texts read as the pages show them: no format arguments left in (en and es). */
    @Test
    fun entriesHaveNoFormatArguments() = checkTexts()

    @Test
    @Config(qualifiers = "es")
    fun entriesHaveNoFormatArgumentsInSpanish() {
        checkTexts()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val items =
            SettingsIndex.entries.map {
                SearchItem(context.getString(it.title), it.summary?.let(context::getString), context.getString(it.page), it.target)
            }
        // an accented title found without accents
        assertFalse(SettingsSearch.match("vibracion", items).isEmpty())
    }

    private fun checkTexts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (e in SettingsIndex.entries) {
            for (id in listOfNotNull(e.title, e.summary, e.page)) {
                val text = context.getString(id)
                assertTrue("${context.resources.getResourceEntryName(id)} is empty", text.isNotBlank())
                assertFalse("${context.resources.getResourceEntryName(id)} has a format argument: $text", "%1$" in text || "%d" in text)
            }
        }
    }
}
