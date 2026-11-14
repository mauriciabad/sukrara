// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.rows

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import app.mauri.sukrara.core.designsystem.R
import app.mauri.sukrara.designsystem.dialog.Choice
import app.mauri.sukrara.designsystem.dialog.ChoiceDialog

/*
 * THE pattern for a setting with a default and per-place exceptions (#209, docs/38-place-overrides.md): the default is
 * the setting's own row (the main action, on the page where the setting lives); one *Exceptions* row under it says only
 * the places that differ ("Notification: 3 h", or "None") and opens a page with one row per place. A place's value is
 * *Default (12 h)* or its own; *Default* is the first choice of every place's dialog. As AOSP's App languages ("System
 * default" first in each app's list), macOS Notifications' per-app Show previews ("Default") and Chrome's site settings
 * (a default behaviour, then the customised sites).
 */

/** One place of a setting with a default: its [name] and its own value in words, or null = it follows the default. */
data class PlaceValue(
    val name: String,
    val own: String?,
    @param:DrawableRes val icon: Int? = null,
)

/** "Default (12 h)": a place, or a dialog's first choice, that follows the default, with the default's value. */
@Composable
fun defaultChoice(value: String): String = stringResource(R.string.ds_default_value, value)

/** "On" / "Off" (a switch's value in words). */
@Composable
fun onOffText(on: Boolean): String = stringResource(if (on) R.string.ds_on else R.string.ds_off)

/** What the *Exceptions* row says: the places with their own value ("Notification: 3 h, Widgets: off"), or "None". */
@Composable
fun exceptionsSummary(places: List<PlaceValue>): String {
    val res = LocalResources.current
    val own = places.filter { it.own != null }
    return if (own.isEmpty()) {
        stringResource(R.string.ds_exceptions_none)
    } else {
        own.joinToString(res.getString(R.string.ds_list_separator)) { res.getString(R.string.ds_exception, it.name, it.own) }
    }
}

/** The row under a setting's default that opens its places ([exceptionsSummary]). Each place is this phone's own. */
@Composable
fun ExceptionsRow(
    places: List<PlaceValue>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    NavigationRow(stringResource(R.string.ds_exceptions), onClick, modifier, summary = exceptionsSummary(places), icon = icon)
}

/** One place on the exceptions page: its own value, or *Default (…)* with the default's value ([defaultValue]). */
@Composable
fun PlaceRow(
    place: PlaceValue,
    defaultValue: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ValueRow(place.name, place.own ?: defaultChoice(defaultValue), onClick, modifier, icon = place.icon)
}

/**
 * A place's choice: *Default (…)* first ([defaultValue] = the default's value in words; chosen = null), then the
 * place's own [options]. Choosing saves and closes.
 */
@Composable
fun <T : Any> OverrideDialog(
    title: String,
    defaultValue: String,
    options: List<Choice<T>>,
    own: T?,
    onClose: () -> Unit,
    onChoose: (T?) -> Unit,
) {
    val all: List<Choice<T?>> =
        listOf(Choice<T?>(defaultChoice(defaultValue), null)) + options.map { Choice<T?>(it.label, it.value, it.sub) }
    ChoiceDialog(title, all, own, onClose, onChoose)
}

/** [OverrideDialog] of a switch: *Default (on)*, *On*, *Off*. */
@Composable
fun OnOffOverrideDialog(
    title: String,
    defaultOn: Boolean,
    own: Boolean?,
    onClose: () -> Unit,
    onChoose: (Boolean?) -> Unit,
) = OverrideDialog(
    title,
    onOffText(defaultOn),
    listOf(Choice(onOffText(true), true), Choice(onOffText(false), false)),
    own,
    onClose,
    onChoose,
)
