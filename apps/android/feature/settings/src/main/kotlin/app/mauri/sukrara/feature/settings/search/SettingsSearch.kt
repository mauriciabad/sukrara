// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.search

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExpandedFullScreenContainedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberContainedSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.designsystem.rows.NavigationRow
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.rows.SettingsHighlight
import app.mauri.sukrara.feature.settings.R
import app.mauri.sukrara.feature.settings.SettingsRoute
import com.android.settingslib.spa.widget.preference.Preference
import com.android.settingslib.spa.widget.preference.PreferenceModel
import kotlinx.coroutines.launch
import java.text.Normalizer
import app.mauri.sukrara.core.designsystem.R as DsR

/**
 * A [SearchEntry] with its words in the current language: [where] is its breadcrumb (the pages down to the row, then
 * its group: "Alerts › Quiet after treatments"; "Settings" for the overview's rows), [highlight] the row the opened page
 * flashes (null: the result is a page, opened at its top), [depth] how deep the row is (shallower first), [path] the
 * pages a page result opens through, [section] whose icon the result shows.
 */
data class SearchItem(
    val title: String,
    val summary: String?,
    val where: String,
    val target: SearchTarget,
    val highlight: String? = null,
    val keywords: String? = null,
    val depth: Int = 1,
    val section: Section = Section.of(target),
)

/** This entry in [res]'s language. */
fun SearchEntry.item(res: Resources): SearchItem =
    SearchItem(
        res.getString(title),
        summary?.let(res::getString),
        (trail + listOfNotNull(group)).distinct().joinToString(" › ") { res.getString(it) },
        target,
        highlight?.let(res::getString),
        keywords?.let(res::getString),
        depth,
    )

/**
 * Settings search, pure, ranked as stock Settings' search ranks (SettingsIntelligence `DatabaseResultTask`, base ranks
 * 1 / 3 / 7 / 9): a query word that starts the title's first word ranks 1, another word of the title 3, a part of the
 * summary or the start of a keyword 7, a word of the breadcrumb (page or group) 9. Every query word must match, and a
 * result ranks as its worst word; ties: shallower rows first, then by title. Case and accents don't matter ("alarma"
 * finds "Alarma", "ultimo" finds "Último"). A page is one result (the row that opens it).
 */
object SettingsSearch {
    /** Lower case without accents: "Último Azúcar" → "ultimo azucar". */
    fun fold(text: String): String =
        Normalizer
            .normalize(text, Normalizer.Form.NFD)
            .replace(Marks, "")
            .lowercase()

    private val Marks = Regex("\\p{Mn}+")
    private val Separators = Regex("[^\\p{L}\\p{N}+]+")

    private fun words(text: String?): List<String> = text?.let { fold(it).split(Separators).filter(String::isNotEmpty) }.orEmpty()

    fun match(
        query: String,
        items: List<SearchItem>,
    ): List<SearchItem> {
        val q = words(query)
        if (q.isEmpty()) return emptyList()
        return items
            .mapNotNull { item ->
                val title = words(item.title)
                val summary = fold(item.summary.orEmpty())
                val keywords = words(item.keywords)
                val where = words(item.where)
                val rank =
                    q.maxOf { w ->
                        when {
                            title.firstOrNull()?.startsWith(w) == true -> 1
                            title.any { it.startsWith(w) } -> 3
                            w in summary || keywords.any { it.startsWith(w) } -> 7
                            where.any { it.startsWith(w) } -> 9
                            else -> return@mapNotNull null
                        }
                    }
                item to rank
            }.sortedWith(compareBy<Pair<SearchItem, Int>> { it.second }.thenBy { it.first.depth }.thenBy { it.first.title })
            .map { it.first }
            // a page is one result: the row that opens it (the overview's, or the page above's), never twice
            .distinctBy { if (it.highlight == null) it.target else Triple(it.title, it.where, it.target) }
    }
}

/**
 * The searches the person ran last (the query that led to an opened result), newest first: at most [MAX], one per
 * query (case, accents and spacing aside). Stored as lines in `settings.searchRecent` ([decode] / [encode]).
 */
object RecentSearches {
    const val MAX = 5

    fun decode(text: String): List<String> =
        text
            .lines()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .take(MAX)

    fun encode(queries: List<String>): String = queries.joinToString("\n")

    /** [query] first, the same query from before dropped. */
    fun add(
        queries: List<String>,
        query: String,
    ): List<String> {
        val q = query.trim().replace(Spaces, " ")
        if (q.isEmpty()) return queries
        val key = SettingsSearch.fold(q)
        return (listOf(q) + queries.filterNot { SettingsSearch.fold(it) == key }).take(MAX)
    }

    private val Spaces = Regex("\\s+")
}

/** The settings search's recent queries: [queries] newest first, [remember] one that opened a result, [clear] them all. */
class RecentSearch(
    val queries: List<String> = emptyList(),
    val remember: (String) -> Unit = {},
    val clear: () -> Unit = {},
)

/** The test tag of the search's results list. */
const val SEARCH_RESULTS_TAG = "settings-search-results"

/**
 * Settings search, as stock Settings and the PoC have it: the search pill under the overview's title (Material 3
 * Expressive's contained SearchBar) opens the full-screen search (ExpandedFullScreenContainedSearchBar) with its back
 * arrow. Empty, it lists the [recent] searches (tap = search it again; *Clear* forgets them). Typing lists every row
 * this phone and build show ([pens], [wearer], [build]) that matches, as SPA rows: the icon of its section, the title,
 * the summary and where it is ("Alerts › Quiet after treatments", in the accent colour). A result closes the search and
 * opens its page through the pages above it, as the menus do ([onOpen] gets that path, Back walks up it; another
 * feature's page: [onOpenOther]), scrolled to its row, which flashes ([SettingsHighlight]); a result that is a page
 * opens it at its top. Every search starts empty. Exits: the back arrow, Back, the ×, opening a result. [initialQuery]:
 * open already searching it (the gallery's pictures).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSearchBar(
    onOpen: (List<SettingsRoute>) -> Unit,
    onOpenOther: (OtherPage) -> Unit,
    modifier: Modifier = Modifier,
    pens: Boolean = false,
    wearer: Boolean = true,
    build: Set<BuildFeature> = BuildFeature.entries.toSet(),
    recent: RecentSearch = RecentSearch(),
    initialQuery: String? = null,
) {
    val res = LocalResources.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val state = rememberContainedSearchBarState(if (initialQuery != null) SearchBarValue.Expanded else SearchBarValue.Collapsed)
    val text = rememberTextFieldState(initialQuery.orEmpty())
    val items =
        remember(res, pens, wearer, build) {
            SettingsIndex.entries.filter { it.shownOn(pens, wearer, build) }.map { it.item(res) }
        }
    val expanded = state.currentValue == SearchBarValue.Expanded || state.targetValue == SearchBarValue.Expanded

    fun open(item: SearchItem) {
        haptics.confirm()
        recent.remember(text.text.toString())
        scope.launch { state.animateToCollapsed() }
        // the page it opens scrolls to the row and flashes it (a page itself: nothing to point at)
        SettingsHighlight.request(item.highlight)
        when (val t = item.target) {
            is SearchTarget.Page -> onOpen(SettingsIndex.path(t.route))
            is SearchTarget.Other -> onOpenOther(t.page)
        }
    }

    val input: @Composable () -> Unit = { SearchInput(text, state, expanded, onSearch = { q -> if (q.isNotBlank()) recent.remember(q) }) }
    SearchBar(state, input, modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
    // like stock Settings' search, every search starts empty
    LaunchedEffect(expanded) { if (!expanded) text.clearText() }
    ExpandedFullScreenContainedSearchBar(state, input) {
        val query = text.text.toString()
        val results = remember(query, items) { SettingsSearch.match(query, items) }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                query.isBlank() -> {
                    if (recent.queries.isNotEmpty()) RecentList(recent) { text.setTextAndPlaceCursorAtEnd(it) }
                }

                results.isEmpty() -> {
                    Text(
                        stringResource(R.string.st_search_none, query.trim()),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }

                else -> {
                    SettingsGroup(Modifier.testTag(SEARCH_RESULTS_TAG)) {
                        for (r in results.take(MAX_RESULTS)) ResultRow(r) { open(r) }
                    }
                }
            }
        }
    }
}

/** At most this many results are listed (stock Settings' search shows a page of them; more words narrow it). */
private const val MAX_RESULTS = 60

/** The search field: the search icon (the back arrow while expanded), the hint, the × while there is a query. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchInput(
    text: TextFieldState,
    state: SearchBarState,
    expanded: Boolean,
    onSearch: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    SearchBarDefaults.InputField(
        textFieldState = text,
        searchBarState = state,
        onSearch = onSearch,
        placeholder = { Text(stringResource(R.string.st_search_hint)) },
        leadingIcon = {
            if (expanded) {
                IconButton(onClick = { scope.launch { state.animateToCollapsed() } }) {
                    Icon(painterResource(DsR.drawable.ds_ic_arrow_back), stringResource(R.string.st_search_back))
                }
            } else {
                Icon(painterResource(DsR.drawable.ds_ic_search), contentDescription = null)
            }
        },
        trailingIcon =
            if (expanded && text.text.isNotEmpty()) {
                {
                    IconButton(onClick = {
                        haptics.confirm()
                        text.clearText()
                    }) { Icon(painterResource(DsR.drawable.ds_ic_close), stringResource(R.string.st_search_clear)) }
                }
            } else {
                null
            },
    )
}

/**
 * One result, an SPA row (Settings' search result): the section's icon, the title, then the summary and where the row
 * is in the accent colour; a tap opens it.
 */
@Composable
private fun ResultRow(
    item: SearchItem,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val summary =
        buildAnnotatedString {
            item.summary?.takeIf { it.isNotBlank() }?.let {
                append(it)
                append("\n")
            }
            withStyle(SpanStyle(color = accent)) { append(item.where) }
        }
    Preference(
        object : PreferenceModel {
            override val title = item.title
            override val summary = { summary }
            override val icon: @Composable () -> Unit = { SectionIcon(item.section) }
            override val onClick = onClick
        },
    )
}

/** *Recent searches* with *Clear*, then one row per query with the history icon (tap = search it again). */
@Composable
private fun RecentList(
    recent: RecentSearch,
    onPick: (String) -> Unit,
) {
    val haptics = rememberHaptics()
    SettingsGroup(
        title = stringResource(R.string.st_search_recent),
        titleAction = {
            TextButton(onClick = {
                haptics.confirm()
                recent.clear()
            }) { Text(stringResource(R.string.st_search_recent_clear)) }
        },
    ) {
        for (q in recent.queries) {
            NavigationRow(q, {
                haptics.confirm()
                onPick(q)
            }, icon = DsR.drawable.ds_ic_history)
        }
    }
}
