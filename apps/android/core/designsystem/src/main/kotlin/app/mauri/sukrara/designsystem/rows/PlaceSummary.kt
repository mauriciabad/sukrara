// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.designsystem.rows

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import app.mauri.sukrara.core.designsystem.R
import app.mauri.sukrara.designsystem.dialog.Choice
import app.mauri.sukrara.designsystem.dialog.ChoiceDialog
import com.android.settingslib.spa.framework.theme.SettingsDimension
import com.android.settingslib.spa.framework.theme.SettingsSize
import com.android.settingslib.spa.framework.theme.SettingsSpace
import com.android.settingslib.spa.framework.theme.isSpaExpressiveEnabled

/*
 * THE pattern for a setting the app sets that some places can change (#209, docs/38): the app's value is the shared
 * one; a place (the notification, the widgets) follows it ("Same as the app") or has its own. The setting's row says
 * the app's value and only the places that differ ([placeSummary]); its page has the app's value first and one row per
 * place. A place's dialog offers what it follows first ([PlaceDialog]), so going back is one tap.
 */

/** One place that can differ from the app: where it is in words ("in the notification") and its own value (null = the app's). */
data class PlaceValue<T : Any>(
    val where: String,
    val own: T?,
)

/**
 * How a summary says things: [app] = where the app's value applies ("in the app"), [join] = a list of places ("in the
 * notification and on widgets", the platform's list formatter), [valueWhere] = a value and where it applies ("3 h in
 * the notification"), [separator] between the app's value and the place that differs on one line.
 */
data class PlaceWords(
    val app: String,
    val join: (List<String>) -> String,
    val valueWhere: (value: String, where: String) -> String,
    val separator: String = ONE_LINE_SEPARATOR,
)

/** Between the app's value and the one place that differs: "12 h · 3 h in the notification". */
const val ONE_LINE_SEPARATOR = " · "

/** What a setting's row says about its places ([placeSummary]). */
sealed interface PlaceSummary {
    /** Nothing differs: the app's value alone ("12 h"); null on a switch row (its switch shows it). */
    data class Plain(
        val text: String?,
    ) : PlaceSummary

    /**
     * Places differ. [oneLine] = the app's value, then the one place that differs ("12 h · 3 h in the notification";
     * a switch row: "Off on widgets"), null when more differ; [lines] = one line per value, each saying where, the
     * app's first ("12 h in the app and on widgets", "3 h in the notification"; a switch row leaves the app's out).
     */
    data class Differs(
        val oneLine: String?,
        val lines: List<String>,
    ) : PlaceSummary
}

/**
 * The summary of a setting whose app's value is [app] and whose [places] (in page order) can have their own. Built from
 * the values: a place whose own value equals the app's isn't listed, places with the same value share a line, and the
 * app's line also names the places that follow it. [switchRow]: the row's switch shows the app's value, so the app's
 * value is never written. [text] writes a value ("12 h", "On").
 */
fun <T : Any> placeSummary(
    app: T,
    places: List<PlaceValue<T>>,
    switchRow: Boolean,
    text: (T) -> String,
    words: PlaceWords,
): PlaceSummary {
    val differs = places.filter { it.own != null && it.own != app }
    if (differs.isEmpty()) return PlaceSummary.Plain(if (switchRow) null else text(app))
    val follow = places.filter { it.own == null || it.own == app }.map { it.where }
    val lines =
        buildList {
            if (!switchRow) add(words.valueWhere(text(app), words.join(listOf(words.app) + follow)))
            for ((value, same) in differs.groupBy { it.own!! }) add(words.valueWhere(text(value), words.join(same.map { it.where })))
        }
    val oneLine =
        differs.singleOrNull()?.let { p ->
            val place = words.valueWhere(text(p.own!!), p.where)
            if (switchRow) place else text(app) + words.separator + place
        }
    return PlaceSummary.Differs(oneLine, lines)
}

/**
 * One paragraph per line (SPA's summary has no line limit), each breaking as a paragraph ([keepLastWords]). A line that
 * wraps (large text) indents what follows its first line, and every line keeps its full height, so the wrapped part
 * reads as part of its own line and never as the start of the next one.
 */
private fun paragraphs(lines: List<String>): AnnotatedString =
    buildAnnotatedString {
        for (line in lines) withStyle(LINE) { append(keepLastWords(line)) }
    }

private val LINE =
    ParagraphStyle(
        lineBreak = LineBreak.Paragraph,
        textIndent = TextIndent(restLine = 16.sp),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )

/**
 * A line that has to wrap (large text) never leaves its last word alone below ("12 h in the app and on" / "widgets"):
 * its last two words are joined by a no-break space, whatever the text engine's line breaker does.
 */
internal fun keepLastWords(line: String): String {
    val last = line.lastIndexOf(' ')
    return if (last <= 0 || line.indexOf(' ') == last) line else line.substring(0, last) + ' ' + line.substring(last + 1)
}

/** The width SPA's row leaves its title and summary: its start padding, the icon and its gap, the end padding. */
private fun textInset(hasIcon: Boolean): Dp =
    if (isSpaExpressiveEnabled) {
        SettingsDimension.itemPaddingStart + (if (hasIcon) SettingsSize.medium3 + SettingsSpace.extraSmall6 else Dp(0f)) +
            SettingsDimension.itemPaddingEnd
    } else {
        (if (hasIcon) SettingsDimension.itemIconContainerSize else SettingsDimension.itemPaddingStart) + SettingsDimension.itemPaddingEnd
    }

/**
 * A setting's row that opens its page, with the places' [summary]: the one-line form while it fits on one line of the
 * row, else one line per value (never a line broken inside an item). [spoken] = the same summary with the values said
 * in full and ", " as separator ("12 hours in the app, 3 hours in the notification"), for TalkBack.
 */
@Composable
fun PlacesRow(
    title: String,
    summary: PlaceSummary,
    spoken: PlaceSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    BoxWithConstraints(modifier) {
        val measurer = rememberTextMeasurer()
        val style = MaterialTheme.typography.bodyMedium
        val width = with(LocalDensity.current) { (maxWidth - textInset(icon != null)).roundToPx().coerceAtLeast(0) }
        val (shown, said) =
            when (summary) {
                is PlaceSummary.Plain -> {
                    summary.text to (spoken as? PlaceSummary.Plain)?.text
                }

                is PlaceSummary.Differs -> {
                    val spokenLines = (spoken as? PlaceSummary.Differs)
                    val one = summary.oneLine
                    if (one != null && measurer.measure(one, style, constraints = Constraints(maxWidth = width)).lineCount == 1) {
                        one to spokenLines?.oneLine
                    } else {
                        paragraphs(summary.lines) to spokenLines?.lines?.joinToString(", ")
                    }
                }
            }
        NavigationRow(title, onClick, summary = shown, summaryDescription = said, icon = icon)
    }
}

/**
 * A switch whose value is the app's (the switch) and that some places can change: SPA's two-target row; the text opens
 * its page, the summary names only the places that differ ("Off on widgets", one line per value).
 */
@Composable
fun PlacesSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: PlaceSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    val text =
        when (summary) {
            is PlaceSummary.Plain -> summary.text
            is PlaceSummary.Differs -> summary.lines.joinToString("\n") { keepLastWords(it) }
        }
    SplitSwitchRow(title, checked, onCheckedChange, onClick, modifier, summary = text, icon = icon)
}

/** "On" / "Off" (a switch's value in words). */
@Composable
fun onOffText(on: Boolean): String = stringResource(if (on) R.string.ds_on else R.string.ds_off)

/**
 * A place's choice: what it follows first ([follow], with that value: "Same as the app (12 h)"; chosen = null), then
 * its own [options]. Choosing saves and closes.
 */
@Composable
fun <T : Any> PlaceDialog(
    title: String,
    follow: String,
    options: List<Choice<T>>,
    own: T?,
    onClose: () -> Unit,
    onChoose: (T?) -> Unit,
) {
    val all: List<Choice<T?>> = listOf(Choice<T?>(follow, null)) + options.map { Choice<T?>(it.label, it.value, it.sub) }
    ChoiceDialog(title, all, own, onClose, onChoose)
}

/** [PlaceDialog] of a switch: what it follows ("Same as the app (On)"), *On*, *Off*. */
@Composable
fun OnOffPlaceDialog(
    title: String,
    follow: String,
    own: Boolean?,
    onClose: () -> Unit,
    onChoose: (Boolean?) -> Unit,
) = PlaceDialog(
    title,
    follow,
    listOf(Choice(onOffText(true), true), Choice(onOffText(false), false)),
    own,
    onClose,
    onChoose,
)
