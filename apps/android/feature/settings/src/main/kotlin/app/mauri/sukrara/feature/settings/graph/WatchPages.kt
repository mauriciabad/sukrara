// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings.graph

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.mauri.sukrara.designsystem.dialog.Choice
import app.mauri.sukrara.designsystem.dialog.NumberDialog
import app.mauri.sukrara.designsystem.dialog.NumberInput
import app.mauri.sukrara.designsystem.rows.CardRow
import app.mauri.sukrara.designsystem.rows.FooterText
import app.mauri.sukrara.designsystem.rows.OnOffPlaceDialog
import app.mauri.sukrara.designsystem.rows.PlaceDialog
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.rows.SettingsIntro
import app.mauri.sukrara.designsystem.rows.SwitchRow
import app.mauri.sukrara.designsystem.rows.ValueRow
import app.mauri.sukrara.designsystem.rows.onOffText
import app.mauri.sukrara.feature.settings.R
import app.mauri.sukrara.feature.settings.SettingsPage
import app.mauri.sukrara.feature.settings.numberLabeler
import app.mauri.sukrara.graph.GraphConfig
import app.mauri.sukrara.graph.MarkShow
import app.mauri.sukrara.graph.WatchDisplay
import app.mauri.sukrara.graph.stats.TirRange
import app.mauri.sukrara.settings.SettingsSpec
import app.mauri.sukrara.core.designsystem.R as DsR

/*
 * Settings › Share with apps › Garmin › a watch's *Sukrara*: Sukrara on that watch as a place (docs/38, the app's SPEC
 * §11). Every value follows the app's (*Same as the app*) until this watch gets its own; a simple value is chosen in
 * the place dialog (*Same as the app (…)* first), a compound one on the app's own page for it, bound to this watch's
 * values, under a *Same as the app* switch (on: the app's values, read-only; off: this watch's, starting from the
 * app's). The unit, the ranges and the alerts are never per watch.
 */

/** A watch value that has a page of its own (the metrics, the colour order, the treatment marks). */
enum class WatchPage {
    Main,
    Second,
    Third,
    NoRecent,
    RingOrder,
    Marks,
    ;

    /** The metric page it opens, for a metric. */
    val metric: MetricSlot?
        get() =
            when (this) {
                Main -> MetricSlot.Main
                Second -> MetricSlot.Second
                Third -> MetricSlot.Third
                NoRecent -> MetricSlot.NoRecent
                else -> null
            }

    companion object {
        val METRICS = listOf(Main, Second, Third)
    }
}

/** The value [page] edits as [own] has it (null = Same as the app). */
private fun WatchDisplay.at(page: WatchPage): Any? =
    when (page) {
        WatchPage.Main, WatchPage.Second, WatchPage.Third -> slots[page.ordinal]
        WatchPage.NoRecent -> lastElse
        WatchPage.RingOrder -> ringOrder
        WatchPage.Marks -> marks
    }

/** [this] with [page] following the app ([follow]) or its own, starting from [app]'s value. */
fun WatchDisplay.following(
    page: WatchPage,
    follow: Boolean,
    app: GraphConfig,
): WatchDisplay =
    when (page) {
        WatchPage.Main, WatchPage.Second, WatchPage.Third -> withSlot(page.ordinal, if (follow) null else app.slots[page.ordinal])
        WatchPage.NoRecent -> copy(lastElse = if (follow) null else app.lastElse)
        WatchPage.RingOrder -> copy(ringOrder = if (follow) null else app.ringOrder)
        WatchPage.Marks -> copy(marks = if (follow) null else app.marks)
    }

/** The page's open dialog (one at a time), saved across rotation. */
private enum class WatchDialog { Last, Range, Rewards, Hours, Side, Report }

/**
 * A watch's *Sukrara* settings: what it shows beside the value (the three metrics, the last treatment, no recent
 * treatment), its time-in-range rings (the number, the rewards, the colour order), its chart (time shown, treatments,
 * the % column) and the period its Numbers page opens on; each row its own value or *Same as the app*. [gone] = Garmin
 * Connect no longer knows this watch: the page says so and its last row lets the values go. [app] = the app's settings,
 * [own] = this watch's, [set] stores them, [open] opens a value's page.
 */
@Composable
fun WatchSettingsContent(
    name: String,
    app: GraphConfig,
    own: WatchDisplay,
    set: (WatchDisplay) -> Unit,
    open: (WatchPage) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    gone: Boolean = false,
) {
    var dialog by rememberSaveable { mutableStateOf<WatchDialog?>(null) }
    val same = stringResource(R.string.st_same_as_app)
    val period = periodLabeler()
    val label = numberLabeler()

    @Composable
    fun follow(value: String) = stringResource(R.string.st_same_as_app_value, value)

    fun days(d: Int) = d * 86_400.0
    SettingsPage(stringResource(R.string.st_watch_title, name), onBack, modifier) {
        SettingsIntro(stringResource(if (gone) R.string.st_watch_gone else R.string.st_watch_intro, name))
        SettingsGroup(title = stringResource(R.string.st_metrics_title)) {
            for (p in WatchPage.METRICS) {
                val slot = p.metric!!
                ValueRow(stringResource(slot.title), own.slots[p.ordinal]?.let { metricSummary(it) } ?: same, { open(p) }, icon = slot.icon)
            }
            ValueRow(stringResource(R.string.st_last), own.lastOn?.let { onOffText(it) } ?: same, {
                dialog = WatchDialog.Last
            }, icon = DsR.drawable.ds_ic_history)
            ValueRow(
                stringResource(R.string.st_last_else),
                own.lastElse?.let { metricSummary(it) } ?: same,
                { open(WatchPage.NoRecent) },
                icon = MetricSlot.NoRecent.icon,
            )
        }
        SettingsGroup(title = stringResource(R.string.st_tir_title)) {
            ValueRow(stringResource(R.string.st_tir_number), own.ringRange?.let { tirName(it) } ?: same, { dialog = WatchDialog.Range })
            ValueRow(stringResource(R.string.st_tir_rewards), own.ringMarks?.let { onOffText(it) } ?: same, {
                dialog = WatchDialog.Rewards
            }, icon = DsR.drawable.ds_ic_crown)
            ValueRow(
                stringResource(R.string.st_tir_order),
                own.ringOrder?.let { stringResource(R.string.st_watch_order_own) } ?: same,
                { open(WatchPage.RingOrder) },
                icon = DsR.drawable.ds_ic_palette,
            )
        }
        SettingsGroup(title = stringResource(R.string.st_watch_chart)) {
            ValueRow(
                stringResource(R.string.st_graph_hours),
                own.hours?.let { label(SettingsSpec.PLACE_HOURS, it.toDouble()) } ?: same,
                { dialog = WatchDialog.Hours },
                icon = DsR.drawable.ds_ic_schedule,
            )
            ValueRow(stringResource(R.string.st_marks_title), own.marks?.let { marksSummary(it) } ?: same, { open(WatchPage.Marks) })
            ValueRow(stringResource(R.string.st_graph_side), own.side?.let { onOffText(it) } ?: same, { dialog = WatchDialog.Side })
        }
        SettingsGroup(title = stringResource(R.string.st_watch_numbers)) {
            ValueRow(stringResource(R.string.st_watch_report), own.reportDays?.let { period(days(it)) } ?: same, {
                dialog = WatchDialog.Report
            })
        }
        // every value back to the app's at once; for a watch Garmin Connect no longer knows, that lets it go
        if (!own.same) {
            SettingsGroup {
                ValueRow(
                    stringResource(if (gone) R.string.st_watch_forget else R.string.st_watch_all_same),
                    "",
                    {
                        set(WatchDisplay())
                        if (gone) onBack()
                    },
                    icon = DsR.drawable.ds_ic_restart_alt,
                )
            }
        }
        FooterText(stringResource(R.string.st_watch_footer))
    }
    val close = { dialog = null }
    when (dialog) {
        null -> {
            Unit
        }

        WatchDialog.Last -> {
            OnOffPlaceDialog(stringResource(R.string.st_last), follow(onOffText(app.lastOn)), own.lastOn, close) {
                set(own.copy(lastOn = it))
                close()
            }
        }

        WatchDialog.Range -> {
            PlaceDialog(
                stringResource(R.string.st_tir_number),
                follow(tirName(app.ringRange)),
                TirRange.entries.map { Choice(tirName(it), it, tirSpan(it, app)) },
                own.ringRange,
                close,
            ) {
                set(own.copy(ringRange = it))
                close()
            }
        }

        WatchDialog.Rewards -> {
            OnOffPlaceDialog(stringResource(R.string.st_tir_rewards), follow(onOffText(app.ringMarks)), own.ringMarks, close) {
                set(own.copy(ringMarks = it))
                close()
            }
        }

        // the one number input (schema placeHours: Same as the app first, 3 / 6 / 12 / 24 h or Custom… 1–48 h; 0 = follow)
        WatchDialog.Hours -> {
            val first = follow(label(SettingsSpec.GRAPH_HOURS, app.appHours.toDouble()))
            NumberDialog(
                stringResource(R.string.st_graph_hours),
                NumberInput(SettingsSpec.PLACE_HOURS),
                (own.hours ?: 0).toDouble(),
                { v -> if (v == 0.0) first else label(SettingsSpec.PLACE_HOURS, v) },
                close,
            ) {
                set(own.copy(hours = it.toInt().takeIf { h -> h > 0 }))
                close()
            }
        }

        WatchDialog.Side -> {
            OnOffPlaceDialog(stringResource(R.string.st_graph_side), follow(onOffText(app.side)), own.side, close) {
                set(own.copy(side = it))
                close()
            }
        }

        WatchDialog.Report -> {
            PlaceDialog(
                stringResource(R.string.st_watch_report),
                follow(period(days(WatchDisplay.REPORT_START))),
                WatchDisplay.REPORT_DAYS.map { Choice(period(days(it)), it) },
                own.reportDays,
                close,
            ) {
                set(own.copy(reportDays = it))
                close()
            }
        }
    }
}

/**
 * A watch value's page: the app's own page for it ([MetricContent], [GraphMarksContent], the colour order of
 * [TirRingsContent]) bound to this watch's values, under a *Same as the app* switch with the app's value: on, the page
 * shows the app's, read-only; off, this watch's own (starting from the app's), editable.
 */
@Composable
fun WatchValueContent(
    page: WatchPage,
    app: GraphConfig,
    own: WatchDisplay,
    set: (WatchDisplay) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val following = own.at(page) == null
    val c = own.applyTo(app)
    val followRow: @Composable () -> Unit = {
        SettingsGroup {
            SwitchRow(
                stringResource(R.string.st_same_as_app),
                following,
                { on -> set(own.following(page, on, app)) },
                summary =
                    when (page) {
                        WatchPage.Main, WatchPage.Second, WatchPage.Third -> metricSummary(app.slots[page.ordinal])
                        WatchPage.NoRecent -> metricSummary(app.lastElse)
                        WatchPage.Marks -> marksSummary(app.marks)
                        WatchPage.RingOrder -> null
                    },
            )
        }
    }
    val actions =
        GraphActions(
            slots = { s -> page.takeIf { it in WatchPage.METRICS }?.let { set(own.withSlot(it.ordinal, s[it.ordinal])) } },
            lastElse = { set(own.copy(lastElse = it)) },
            ringOrder = { set(own.copy(ringOrder = it)) },
            marks = { set(own.copy(marks = it)) },
            reset = { section -> if (section == GraphSection.Marks) set(own.copy(marks = MarkShow.DEFAULT)) },
        )
    val metric = page.metric
    when {
        metric != null -> {
            MetricContent(metric, c, actions, onBack, modifier, readOnly = following, preview = followRow)
        }

        page == WatchPage.Marks -> {
            GraphMarksContent(c, actions, onBack, readOnly = following, preview = followRow)
        }

        else -> {
            val title = stringResource(R.string.st_tir_order)
            SettingsPage(title, onBack, modifier) {
                followRow()
                SettingsGroup(title = title) { CardRow { RingOrderList(c, actions.ringOrder, !following) } }
                if (!following) FooterText(stringResource(R.string.st_tir_order_footer))
            }
        }
    }
}
