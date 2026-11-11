// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.rows

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.core.designsystem.R
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import com.android.settingslib.spa.framework.theme.SettingsDimension
import com.android.settingslib.spa.framework.theme.SettingsShape
import com.android.settingslib.spa.framework.theme.SettingsSpace
import com.android.settingslib.spa.widget.button.CategoryButton
import com.android.settingslib.spa.widget.preference.CheckboxPreference
import com.android.settingslib.spa.widget.preference.CheckboxPreferenceModel
import com.android.settingslib.spa.widget.preference.ListPreferenceOption
import com.android.settingslib.spa.widget.preference.MainSwitchPreference
import com.android.settingslib.spa.widget.preference.Preference
import com.android.settingslib.spa.widget.preference.PreferenceModel
import com.android.settingslib.spa.widget.preference.Radio2
import com.android.settingslib.spa.widget.preference.SwitchPreference
import com.android.settingslib.spa.widget.preference.SwitchPreferenceModel
import com.android.settingslib.spa.widget.preference.TwoTargetSwitchPreference
import com.android.settingslib.spa.widget.ui.Category
import com.android.settingslib.spa.widget.ui.CategoryTitle
import com.android.settingslib.spa.widget.ui.Footer
import com.android.settingslib.spa.widget.ui.SettingsBody

/*
 * The settings rows. Everything they draw is AOSP's own Compose settings library, SettingsLib SPA (:settingslib-spa,
 * android17-release), the code the stock Settings app draws its Compose pages with (the owner's rule, #168; the PoC's
 * docs/10-showcase.md "On AOSP's own settings library"):
 *
 *  - a group is SPA's Category (20 dp rounded card, 2 dp between rows, the title in primary above it);
 *  - rows are SPA's Preference / SwitchPreference / TwoTargetSwitchPreference (chevron, 40 dp line, switch with
 *    check / close thumb icons) / CheckboxPreference / RadioPreferences' Radio2, plus MainSwitchPreference, Footer,
 *    SettingsIntro and CategoryButton (SettingsLib's SectionButtonPreference) for the parts of a page around them.
 *
 * This file only adapts them to the app's calls (one function per kind of row) and adds what the app needs on top:
 * group titles that TalkBack reads as headings, a toggle haptic, read-only mirrored rows at full contrast, the error
 * colour for a row whose feature something outside the app blocks. Stock rows have no chevron; a disabled row is
 * greyed out and says why in its summary. Pages are drawn in SPA's SettingsTheme ([SettingsChrome]).
 */

/** The test tag of every row (the settings search index test reads each row's title, its first text, by it). */
const val ROW_TAG = "settings-row"

/**
 * A group of rows: SPA's Category, with an optional [title] (announced as a heading by TalkBack) and [titleAction]
 * beside it (a section's Reset icon). Like SPA, the title shows only while the group has rows to show.
 */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    /** Beside the title (a section's Reset icon), end-aligned. */
    titleAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var hasRows by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        if (title != null && hasRows) GroupTitle(title, titleAction)
        // SPA's Category draws the card and gives its rows their surface; its own title has no heading semantics, so
        // the title is drawn above it in the same place (SPA: 4 dp above the title inside the category's padding,
        // which then pads the rows only: the title's bottom gives that 4 dp back)
        Category(modifier = Modifier.onGloballyPositioned { hasRows = it.size.height > 0 }) { content() }
    }
}

/** SPA's CategoryTitle where Category puts it, as a heading, with an optional end-aligned [action]. */
@Composable
private fun GroupTitle(
    title: String,
    action: (@Composable () -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = SettingsSpace.small1, end = if (action != null) SettingsSpace.extraSmall4 else SettingsSpace.small1)
            .padding(top = SettingsSpace.extraSmall4)
            .trimBottom(SettingsSpace.extraSmall4),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) { CategoryTitle(title) }
        action?.invoke()
    }
}

/** Reports [d] less height than the content takes (the content still draws there): a negative bottom padding. */
private fun Modifier.trimBottom(d: Dp) =
    layout { m, c ->
        val p = m.measure(c)
        layout(p.width, (p.height - d.roundToPx()).coerceAtLeast(0)) { p.place(0, 0) }
    }

/**
 * A section title outside a group (above free content such as the search's recent queries): SPA's CategoryTitle,
 * announced as a heading.
 */
@Composable
fun SettingsHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.semantics(mergeDescendants = true) { heading() }) { CategoryTitle(text) }
}

/** A row's icon as SPA draws it ([com.android.settingslib.spa.widget.ui.SettingsIcon]): 24 dp, onSurfaceVariant. */
@Composable
internal fun RowIcon(
    @DrawableRes icon: Int,
    warning: Boolean = false,
) {
    Icon(
        painterResource(icon),
        contentDescription = null,
        modifier = Modifier.size(SettingsDimension.itemIconSize),
        tint = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun iconOf(
    @DrawableRes icon: Int?,
    leading: (@Composable () -> Unit)?,
    warning: Boolean = false,
): (@Composable () -> Unit)? = leading ?: icon?.let { { RowIcon(it, warning) } }

/** Every row sits in a tagged box (the index test finds rows by it; it adds no layout and no TalkBack stop). */
@Composable
private fun RowBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.testTag(ROW_TAG)) { content() }
}

private fun model(
    title: String,
    summary: CharSequence?,
    icon: (@Composable () -> Unit)?,
    enabled: Boolean,
    onClick: (() -> Unit)?,
) = object : PreferenceModel {
    override val title = title
    override val summary = { summary ?: "" }
    override val icon = icon
    override val enabled = { enabled }
    override val onClick = onClick
}

private fun switchModel(
    title: String,
    summary: CharSequence?,
    icon: (@Composable () -> Unit)?,
    checked: Boolean,
    changeable: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
) = object : SwitchPreferenceModel {
    override val title = title
    override val summary = { summary ?: "" }
    override val icon = icon
    override val checked = { checked }
    override val changeable = { changeable }
    override val onCheckedChange = onCheckedChange
}

/** [text] in the error colour: a summary that says something outside the app stops the feature. */
@Composable
private fun warningText(text: String?): CharSequence? =
    text?.let {
        val error = MaterialTheme.colorScheme.error
        buildAnnotatedString { withStyle(SpanStyle(color = error)) { append(it) } }
    }

/**
 * SPA's SwitchPreference: the whole row toggles (TalkBack: "switch, on"). A disabled row is greyed out and explains
 * why in [summary]. [readOnly]: a value this phone doesn't decide (a follower's mirrored setting): the switch shows it
 * at full contrast but can't be flipped (TalkBack: "switch, on, disabled"; the page says why and how to change it).
 */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
) {
    val haptics = rememberHaptics()
    val i = iconOf(icon, null)
    RowBox(modifier) {
        if (readOnly) {
            SwitchPreference(switchModel(title, summary, i, checked, changeable = true, onCheckedChange = null), readOnlySwitch(checked))
        } else {
            SwitchPreference(
                switchModel(title, summary, i, checked, enabled) {
                    haptics.toggle(it)
                    onCheckedChange(it)
                },
            )
        }
    }
}

/**
 * Two targets in one row, SPA's TwoTargetSwitchPreference (AOSP's PrimarySwitchPreference): left of the chevron and
 * the line opens [onClick]'s page, the switch turns the thing on or off. TalkBack reads two nodes: the row and a
 * switch named [title]. [enabled] greys the left part, [switchEnabled] the switch. [warning]: the thing is on but
 * something outside the app stops it (icon and summary in the error colour; the row opens the fix).
 */
@Composable
fun SplitSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    switchEnabled: Boolean = enabled,
    warning: Boolean = false,
) {
    val haptics = rememberHaptics()
    val shown = if (warning) warningText(summary) else summary
    RowBox(modifier) {
        TwoTargetSwitchPreference(
            switchModel(title, shown, iconOf(icon, null, warning), checked, switchEnabled) {
                haptics.toggle(it)
                onCheckedChange(it)
            },
            primaryEnabled = { enabled },
            primaryOnClick = onClick,
        )
    }
}

/**
 * Opens another screen: SPA's Preference (stock rows show no chevron). [leading] replaces [icon] with other content
 * (the overview's [CategoryIcon], a picture): decorative, the title says what the row is. [warning]: something outside
 * the app blocks what the row is about (icon and summary in the error colour; the row opens the fix).
 */
@Composable
fun NavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    warning: Boolean = false,
) {
    val shown = if (warning) warningText(summary) else summary
    RowBox(modifier) { Preference(model(title, shown, iconOf(icon, leading, warning), enabled, onClick)) }
}

/**
 * Does something right here (copy, show, bring back) instead of opening a screen: SPA's Preference (TalkBack reads it
 * as stock Settings' rows: "double tap to activate"). The caller confirms the result (a haptic, a changed summary).
 */
@Composable
fun ActionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    RowBox(modifier) { Preference(model(title, summary, iconOf(icon, leading), enabled, onClick)) }
}

/**
 * A value that opens a choice or number dialog: the current [value] below the title (SPA ListPreference's summary).
 * [readOnly]: shown at full contrast but not opened (a follower's mirrored setting).
 */
@Composable
fun ValueRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
) {
    RowBox(modifier) {
        if (readOnly) {
            // full contrast, no click; TalkBack: "disabled" (as RestrictedSwitchPreference marks what can't change here)
            Preference(model(title, value, iconOf(icon, null), true, null), rowModifier = Modifier.semantics { disabled() })
        } else {
            Preference(model(title, value, iconOf(icon, null), enabled, onClick))
        }
    }
}

/** Not tappable: a fact (version, an address). */
@Composable
fun InfoRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    @DrawableRes icon: Int? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    RowBox(modifier) { Preference(model(title, summary, iconOf(icon, leading), true, null)) }
}

/**
 * Free content (a slider, chips, a field) as one row of a group: the surface and corners SPA gives its own rows in a
 * category, with SPA's item padding.
 */
@Composable
fun CardRow(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(SettingsShape.CornerExtraSmall2)
            .background(MaterialTheme.colorScheme.surfaceBright)
            .padding(SettingsDimension.itemPadding),
        verticalArrangement = Arrangement.spacedBy(SettingsSpace.extraSmall2),
        content = content,
    )
}

/**
 * Something that can't be undone (forget a sensor, start fresh): a plain row, like stock Settings' "Forget" / "Erase",
 * and a confirmation saying what happens ([confirmText]) before [onConfirm] runs, its action in the error colour.
 * Cancel leaves everything as it was.
 */
@Composable
fun DangerRow(
    title: String,
    confirmTitle: String,
    confirmText: String,
    confirmAction: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    var asking by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberHaptics()
    RowBox(modifier) { Preference(model(title, summary, iconOf(icon, null), enabled) { asking = true }) }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(confirmTitle) },
            text = { Text(confirmText) },
            confirmButton = {
                TextButton(onClick = {
                    asking = false
                    haptics.confirm()
                    onConfirm()
                }) { Text(confirmAction, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(R.string.ds_cancel)) } },
        )
    }
}

/**
 * One option of a choice that stays on the page (two or three options that shouldn't hide behind a dialog): SPA
 * RadioPreferences' row (Radio2); the whole row selects (TalkBack: "radio button, selected"). Put the options of one
 * choice in one [SettingsGroup] with `Modifier.selectableGroup()`.
 */
@Composable
fun RadioRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    RowBox(modifier) {
        Radio2(ListPreferenceOption(id = 1, text = title, summary = summary.orEmpty(), icon = leading), if (selected) 1 else 0, enabled) {
            if (!selected) haptics.tick()
            onSelect()
        }
    }
}

/** SPA's CheckboxPreference: the whole row toggles a check box at its end (an option, not a feature switch). */
@Composable
fun CheckboxRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    val haptics = rememberHaptics()
    val i = iconOf(icon, null)
    RowBox(modifier) {
        CheckboxPreference(
            object : CheckboxPreferenceModel {
                override val title = title
                override val summary = { summary.orEmpty() }
                override val icon = i
                override val checked = { checked }
                override val changeable = { enabled }
                override val onCheckedChange: (Boolean) -> Unit = {
                    haptics.toggle(it)
                    onCheckedChange(it)
                }
            },
        )
    }
}

/**
 * A feature's own switch at the top of its page, SPA's MainSwitchPreference ("Using the main setting to disable the
 * feature disables all other related settings"): a primaryContainer pill, title only. [note]: one line under the pill,
 * aligned with its title, in SPA's body style: what the state means or why the switch can't be used ("Sign in first",
 * "All alerts are off"). [readOnly] as in [SwitchRow].
 */
@Composable
fun MainSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    note: String? = null,
) {
    val haptics = rememberHaptics()
    RowBox(modifier) {
        Column {
            if (readOnly) {
                MainSwitchPreference(
                    switchModel(title, null, null, checked, changeable = true, onCheckedChange = null),
                    readOnlySwitch(checked),
                )
            } else {
                MainSwitchPreference(
                    switchModel(title, null, null, checked, enabled) {
                        haptics.toggle(it)
                        onCheckedChange(it)
                    },
                )
            }
            if (note != null) {
                // the pill's title starts 16 dp (its margin) + 32 dp (its padding) in
                Box(
                    Modifier.padding(
                        start = SettingsSpace.small1 + SettingsSpace.medium1,
                        end = SettingsSpace.medium1,
                        bottom = SettingsSpace.extraSmall4,
                    ),
                ) {
                    SettingsBody(note)
                }
            }
        }
    }
}

/** RestrictedSwitchPreference's way for a switch this phone doesn't decide: no toggle, its state and role on the row. */
private fun readOnlySwitch(checked: Boolean) =
    Modifier.semantics {
        role = Role.Switch
        toggleableState = ToggleableState(checked)
        disabled()
    }

/**
 * A button under a group that adds to it or acts on the whole list ("Add reminder"): SettingsLib's
 * SectionButtonPreference, drawn by SPA's CategoryButton (a medium button in surfaceBright, the icon and text in the
 * text colour), start-aligned under the group.
 */
@Composable
fun SectionButton(
    @DrawableRes icon: Int,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(modifier.padding(vertical = SettingsSpace.extraSmall4).semantics { if (!enabled) disabled() }) {
        CategoryButton(
            ImageVector.vectorResource(icon),
            text,
            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else DISABLED_ALPHA),
            onClick = { if (enabled) onClick() },
        )
    }
}

private const val DISABLED_ALPHA = 0.38f

/**
 * The pill that shows the hidden rest of a list: SettingsLib's NumberButtonPreference (what BannerMessagePreferenceGroup
 * puts under a list to expand its dismissed items): the [count] in a secondaryContainer circle, then [text], on a
 * surfaceBright pill, start-aligned with the list. [count] null: the pill without the number (*See less*).
 */
@Composable
fun NumberButton(
    count: Int?,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.padding(horizontal = SettingsSpace.small1, vertical = SettingsSpace.extraSmall4)) {
        Row(
            Modifier
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surfaceBright)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = SettingsSpace.small1, horizontal = SettingsSpace.small4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (count != null) {
                Box(
                    Modifier
                        .padding(end = SettingsSpace.extraSmall4)
                        .defaultMinSize(SettingsSpace.small4, SettingsSpace.small4)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = SettingsSpace.extraSmall2),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$count", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/**
 * The top of a page, AOSP TopIntroPreference: one or two sentences on what the page is for, in body large, on the
 * page background (SPA's SettingsIntro text with TopIntro's padding).
 */
@Composable
fun SettingsIntro(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.padding(
            start = SettingsSpace.small4,
            end = SettingsSpace.small4,
            top = SettingsSpace.extraSmall4,
            bottom = SettingsSpace.small1,
        ),
    ) {
        com.android.settingslib.spa.widget.ui
            .SettingsIntro(text)
    }
}

/** Explanations at the end of a page: SPA's Footer (an info icon, then the text below it). */
@Composable
fun FooterText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier) { Footer(text) }
}
