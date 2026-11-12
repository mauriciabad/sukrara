// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.mauri.sukrara.core.designsystem.R
import app.mauri.sukrara.designsystem.haptics.rememberHaptics
import app.mauri.sukrara.settings.NumberSpec
import com.android.settingslib.spa.framework.theme.SettingsDimension
import com.android.settingslib.spa.framework.theme.SettingsOpacity.alphaForEnabled
import com.android.settingslib.spa.framework.theme.SettingsSpace
import com.android.settingslib.spa.widget.dialog.SettingsDialog
import com.android.settingslib.spa.widget.ui.SettingsBody
import com.android.settingslib.spa.widget.ui.SettingsDialogItem

/*
 * The settings dialogs are SPA's (the stock Settings app's): a choice is SPA ListPreference's dialog (SettingsDialog with
 * its radio rows; choosing saves and closes), a number adds a *Custom* radio that reveals a number field, like AOSP
 * Settings' one custom option (the keyboard key-delay dialog), with the expressive buttons of SPA's
 * SettingsAlertDialog (outlined dismiss, filled confirm). Every dialog has a visible Cancel (complete flows).
 */

/** One option of a [ChoiceDialog]; [trailing] sits at the row's end (a ▶ preview), outside the row's selection. */
data class Choice<T>(
    val label: String,
    val value: T,
    val sub: String? = null,
    val enabled: Boolean = true,
    val trailing: (@Composable () -> Unit)? = null,
)

/** A single choice: SPA ListPreference's dialog; choosing saves and closes. Cancel (or back) leaves the value as it was. */
@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<Choice<T>>,
    selected: T,
    onClose: () -> Unit,
    onChoose: (T) -> Unit,
) = ChoiceDialog(title, options, selected, onClose, null, onChoose)

/** [ChoiceDialog] with a [message] above the rows, about all of them (null = none). A dialog window: no modifier. */
@Suppress("ktlint:compose:modifier-missing-check")
@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<Choice<T>>,
    selected: T,
    onClose: () -> Unit,
    message: String?,
    onChoose: (T) -> Unit,
) {
    val haptics = rememberHaptics()
    SettingsDialog(title = title, onDismissRequest = onClose) {
        Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
            if (message != null) {
                Box(Modifier.padding(SettingsDimension.dialogItemPadding)) { SettingsBody(message) }
            }
            for (o in options) {
                DialogRadio(o.label, o.sub, o.value == selected, o.enabled, trailing = o.trailing) {
                    haptics.confirm()
                    onChoose(o.value)
                    onClose()
                }
            }
            DialogButtons(dismiss = stringResource(R.string.ds_cancel), onDismiss = onClose)
        }
    }
}

/**
 * SPA ListPreference's radio row (its padding, 16 dp gap, SettingsDialogItem and SettingsBody): the whole row selects
 * (TalkBack: "radio button, selected"); [trailing] sits at the row's end, outside the selection (a ▶ preview). For a
 * choice inside a dialog of its own (SPA's SettingsDialog): put a choice's rows in a `Modifier.selectableGroup()`.
 */
@Suppress("ktlint:compose:lambda-param-event-trailing")
@Composable
fun DialogRadio(
    text: String,
    sub: String?,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                // a trailing control (48 dp) takes the place of the row's vertical padding, so rows with and without
                // one are as tall
                .padding(
                    start = SettingsDimension.dialogItemPaddingHorizontal,
                    end = if (trailing != null) 0.dp else SettingsDimension.dialogItemPaddingHorizontal,
                    top = if (trailing != null) 0.dp else SettingsDimension.buttonPaddingVertical,
                    bottom = if (trailing != null) 0.dp else SettingsDimension.buttonPaddingVertical,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null, enabled = enabled)
            Spacer(Modifier.width(SettingsDimension.itemPaddingEnd))
            Column(Modifier.weight(1f).alphaForEnabled(enabled)) {
                SettingsDialogItem(text = text, enabled = enabled)
                if (!sub.isNullOrEmpty()) SettingsBody(body = sub)
            }
        }
        trailing?.let { Box(Modifier.padding(end = SettingsDimension.paddingSmall)) { it() } }
    }
}

/**
 * SPA SettingsAlertDialog's expressive buttons at the dialog's end: the outlined dismiss, then the filled confirm.
 * [other]: a third action before them as a text button (*Test*, *Remove*), in the error colour when [otherDanger]; the
 * buttons wrap at a large font.
 */
@Composable
fun DialogButtons(
    dismiss: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirm: String? = null,
    confirmEnabled: Boolean = true,
    onConfirm: () -> Unit = {},
    other: String? = null,
    otherEnabled: Boolean = true,
    otherDanger: Boolean = false,
    onOther: () -> Unit = {},
) {
    FlowRow(
        modifier.fillMaxWidth().padding(SettingsDimension.dialogItemPadding),
        horizontalArrangement = Arrangement.spacedBy(SettingsSpace.extraSmall4, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(SettingsSpace.extraSmall4),
    ) {
        if (other != null) {
            TextButton(onClick = onOther, enabled = otherEnabled) {
                Text(other, color = if (otherDanger) MaterialTheme.colorScheme.error else Color.Unspecified)
            }
        }
        OutlinedButton(onClick = onDismiss) { Text(dismiss) }
        if (confirm != null) Button(onClick = onConfirm, enabled = confirmEnabled) { Text(confirm) }
    }
}

/** A number spec's unit as the field's suffix and the range text say it ("h", "días", "mg/dL"). */
@Composable
fun unitText(spec: NumberSpec): String =
    when (spec.unit) {
        "h" -> stringResource(R.string.ds_unit_h)
        "d" -> stringResource(R.string.ds_unit_d)
        "min" -> stringResource(R.string.ds_unit_min)
        "s" -> stringResource(R.string.ds_unit_s)
        else -> spec.unit
    }

/**
 * THE number input (the Mac's NumberSettingRow is the same pattern), driven by the setting's generated spec: special
 * values ("Off") and presets as radio rows (choosing one saves and closes), then — when the spec takes custom values —
 * "Custom", already selected when the current value isn't an option, revealing a number field with the unit as suffix,
 * the allowed range as supporting text and an error in its place while the text isn't valid; OK saves. Exact values
 * are typed, never dragged: glucose limits and minutes are precise. [label] names an option ("1 h", "Off"), [sub]
 * says more under it; [live] says what a valid typed value means, under the field as it changes ("Low: 56–69 mg/dL").
 * [focusField] false keeps the keyboard closed (screenshot tests).
 */
@Suppress("ktlint:compose:modifier-missing-check")
@Composable
fun NumberDialog(
    title: String,
    input: NumberInput,
    selected: Double,
    label: (Double) -> String,
    onClose: () -> Unit,
    focusField: Boolean = true,
    sub: (Double) -> String? = { null },
    live: (Double) -> String? = { null },
    onChoose: (Double) -> Unit,
) {
    // a glucose format follows the screen's unit (mmol/L: its schema variant), values stay stored ones
    val shown = input.inUnit(LocalGlucoseUnit.current)
    if (!shown.hasCustom) {
        ChoiceDialog(title, shown.options.map { Choice(label(it), it, sub(it)) }, selected, onClose, onChoose)
        return
    }
    val haptics = rememberHaptics()
    val isOption = shown.isOption(selected)
    var custom by rememberSaveable { mutableStateOf(!isOption || shown.options.isEmpty()) }
    var text by rememberSaveable { mutableStateOf(shown.initialText(selected)) }
    val unit = unitText(shown.spec)
    val rangeText = stringResource(R.string.ds_custom_range, shown.shownMin, shown.shownMax, unit)
    val check = shown.check(text)
    SettingsDialog(title = title, onDismissRequest = onClose) {
        Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
            for (p in shown.options) {
                DialogRadio(label(p), sub(p), !custom && shown.same(p, selected), true) {
                    haptics.confirm()
                    onChoose(p)
                    onClose()
                }
            }
            if (shown.options.isNotEmpty()) {
                DialogRadio(stringResource(R.string.ds_custom), null, custom, true) { custom = true }
            }
            if (custom) {
                val focus = remember { FocusRequester() }
                if (focusField) LaunchedEffect(Unit) { focus.requestFocus() }
                val error =
                    when (check) {
                        is NumberInput.Check.Rejected -> check.message
                        NumberInput.Check.OutOfRange -> rangeText
                        else -> null
                    }
                // SPA's SettingsOutlinedTextField (label, the error as supporting text), with a number keyboard and the unit
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = shown.filter(it) },
                    label = { Text(stringResource(R.string.ds_custom_label)) },
                    suffix = { Text(unit) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = {
                        Text(error ?: listOfNotNull((check as? NumberInput.Check.Ok)?.let { live(it.value) }, rangeText).joinToString("\n"))
                    },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = if (shown.spec.decimals > 0) KeyboardType.Decimal else KeyboardType.Number,
                        ),
                    modifier = Modifier.fillMaxWidth().padding(SettingsDimension.textFieldPadding).focusRequester(focus),
                )
            }
            DialogButtons(
                dismiss = stringResource(R.string.ds_cancel),
                onDismiss = onClose,
                confirm = if (custom) stringResource(R.string.ds_ok) else null,
                confirmEnabled = check is NumberInput.Check.Ok,
            ) {
                (check as? NumberInput.Check.Ok)?.let {
                    haptics.confirm()
                    onChoose(it.value)
                    onClose()
                }
            }
        }
    }
}
