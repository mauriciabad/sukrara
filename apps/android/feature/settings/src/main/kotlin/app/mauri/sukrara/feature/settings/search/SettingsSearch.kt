// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.designsystem.motion.PageBackHandler
import app.mauri.sukrara.designsystem.rows.InfoRow
import app.mauri.sukrara.designsystem.rows.NavigationRow
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.rows.SettingsHeader
import app.mauri.sukrara.feature.settings.R
import app.mauri.sukrara.feature.settings.SettingsRoute
import java.text.Normalizer
import app.mauri.sukrara.core.designsystem.R as DsR

/** A [SearchEntry] with its words in the current language. */
data class SearchItem(
    val title: String,
    val summary: String?,
    val page: String,
    val target: SearchTarget,
)

/**
 * Settings search, pure: case and accents don't matter ("alarma" finds "Alarma", "ultimo" finds "Último"), every
 * word of the query must start a word of the row (title, summary or page). Rows whose title matches come first, then
 * those matched by their summary, then by their page; in index order within each.
 */
object SettingsSearch {
    /** Lower case without accents: "Último Azúcar" → "ultimo azucar". */
    fun fold(text: String): String =
        Normalizer
            .normalize(text, Normalizer.Form.NFD)
            .replace(Marks, "")
            .lowercase()

    private val Marks = Regex("\\p{Mn}+")
    private val Separators = Regex("[^\\p{L}\\p{N}]+")

    private fun words(text: String?): List<String> = text?.let { fold(it).split(Separators).filter(String::isNotEmpty) }.orEmpty()

    fun match(
        query: String,
        items: List<SearchItem>,
    ): List<SearchItem> {
        val q = words(query)
        if (q.isEmpty()) return emptyList()

        fun covers(ws: List<String>) = q.all { part -> ws.any { it.startsWith(part) } }
        return items
            .mapNotNull { item ->
                val title = words(item.title)
                val summary = title + words(item.summary)
                val all = summary + words(item.page)
                when {
                    covers(title) -> 0 to item
                    covers(summary) -> 1 to item
                    covers(all) -> 2 to item
                    else -> null
                }
            }.sortedBy { it.first } // stable: index order within a rank
            .map { it.second }
            .distinctBy { Triple(it.title, it.page, it.target) }
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

/**
 * The search field at the top of the settings overview, with its results below it: "Page › row" rows that open the
 * page ([onOpen]) or another feature's page ([onOpenOther]); only rows this phone and build show ([pens], [wearer],
 * [build]). [onActiveChange] tells the overview to hide its rows while a query is typed. While the field is focused and
 * empty, the [recent] searches show as chips (tap = search it again) with *Clear*. Exits: the clear button, Back
 * (clears the query first), opening a result.
 */
@Composable
fun SettingsSearchBar(
    onOpen: (SettingsRoute) -> Unit,
    onOpenOther: (OtherPage) -> Unit,
    modifier: Modifier = Modifier,
    pens: Boolean = false,
    wearer: Boolean = true,
    build: Set<BuildFeature> = BuildFeature.entries.toSet(),
    recent: RecentSearch = RecentSearch(),
    initialQuery: String = "",
    onActiveChange: (Boolean) -> Unit = {},
) {
    val res = LocalResources.current
    val focus = LocalFocusManager.current
    val haptics = rememberHaptics()
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var focused by remember { mutableStateOf(false) }
    val items =
        remember(res, pens, wearer, build) {
            SettingsIndex.entries.filter { it.shownOn(pens, wearer, build) }.map { e ->
                val page = listOfNotNull(e.parent, e.page).joinToString(" › ") { res.getString(it) }
                SearchItem(res.getString(e.title), e.summary?.let(res::getString), page, e.target)
            }
        }
    val results = remember(query, items) { SettingsSearch.match(query, items) }
    val active = query.isNotBlank()
    val activeChanged by rememberUpdatedState(onActiveChange)
    LaunchedEffect(active) { activeChanged(active) }
    PageBackHandler(enabled = active || focused) {
        query = ""
        focus.clearFocus()
    }
    val clear = stringResource(R.string.st_search_clear)
    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .onFocusChanged { focused = it.isFocused },
            placeholder = { Text(stringResource(R.string.st_search_hint)) },
            leadingIcon = { Icon(painterResource(DsR.drawable.ds_ic_search), contentDescription = null) },
            trailingIcon = {
                if (active) {
                    IconButton(onClick = {
                        haptics.confirm()
                        query = ""
                    }) { Icon(painterResource(DsR.drawable.ds_ic_close), contentDescription = clear) }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors =
                OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceBright,
                ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions =
                KeyboardActions(onSearch = {
                    if (results.isNotEmpty()) recent.remember(query)
                    focus.clearFocus()
                }),
        )
        AnimatedVisibility(focused && !active && recent.queries.isNotEmpty()) {
            RecentChips(recent) { q ->
                haptics.confirm()
                query = q
            }
        }
        AnimatedVisibility(active) {
            SettingsGroup(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                if (results.isEmpty()) {
                    InfoRow(stringResource(R.string.st_search_none, query.trim()))
                }
                for (r in results) {
                    NavigationRow(
                        r.title,
                        {
                            haptics.confirm()
                            recent.remember(query)
                            focus.clearFocus()
                            when (val t = r.target) {
                                is SearchTarget.Page -> onOpen(t.route)
                                is SearchTarget.Other -> onOpenOther(t.page)
                            }
                        },
                        // the breadcrumb first ("Alerts › Mute all" reads as page, then row), then what the row does
                        summary = listOfNotNull(stringResource(R.string.st_search_in, r.page), r.summary).joinToString(" · "),
                    )
                }
            }
        }
    }
}

/** *Recent searches* with *Clear*, then one chip per query (tap = search it again). */
@Composable
private fun RecentChips(
    recent: RecentSearch,
    onPick: (String) -> Unit,
) {
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsHeader(stringResource(R.string.st_search_recent), Modifier.weight(1f))
            TextButton(onClick = {
                haptics.confirm()
                recent.clear()
            }) { Text(stringResource(R.string.st_search_recent_clear)) }
        }
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (q in recent.queries) {
                AssistChip(
                    onClick = { onPick(q) },
                    label = { Text(q) },
                    leadingIcon = { Icon(painterResource(DsR.drawable.ds_ic_history), contentDescription = null) },
                )
            }
        }
    }
}
