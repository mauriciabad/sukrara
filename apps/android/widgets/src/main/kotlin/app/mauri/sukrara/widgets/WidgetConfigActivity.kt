// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import app.mauri.sukrara.designsystem.dialog.Choice
import app.mauri.sukrara.designsystem.dialog.NumberDialog
import app.mauri.sukrara.designsystem.dialog.NumberInput
import app.mauri.sukrara.designsystem.rows.OnOffOverrideDialog
import app.mauri.sukrara.designsystem.rows.OverrideDialog
import app.mauri.sukrara.designsystem.rows.PlaceRow
import app.mauri.sukrara.designsystem.rows.PlaceValue
import app.mauri.sukrara.designsystem.rows.SettingsChrome
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.rows.SettingsScaffoldPage
import app.mauri.sukrara.designsystem.rows.defaultChoice
import app.mauri.sukrara.designsystem.rows.onOffText
import app.mauri.sukrara.designsystem.theme.SukraraTheme
import app.mauri.sukrara.graph.GraphConfig
import app.mauri.sukrara.graph.GraphPlace
import app.mauri.sukrara.graph.stats.TirRange
import app.mauri.sukrara.settings.SettingsSpec
import kotlinx.coroutines.launch

/**
 * One placed widget's own settings: shown when it's placed (optional: back keeps the Widgets place's settings) and
 * from the launcher's Reconfigure. Every row is *Default (…)* (the Widgets place's value, said in brackets) or this
 * widget's own: THE place-override pattern (docs/38). Save stores and redraws, Cancel (or back) leaves the widget as
 * it was.
 */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId =
            intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // OK from the start: the app's settings are valid, so leaving without saving keeps a just-placed widget (a
        // launcher that ignores configuration_optional would remove it on CANCELED)
        setResult(RESULT_OK, result(appWidgetId))
        val kind = kindOf(appWidgetId)
        if (kind == null) {
            finish()
            return
        }
        enableEdgeToEdge()
        val host = widgetsHost
        setContent {
            // a settings page and its dialogs: SPA's SettingsTheme, inside the app's (reduced motion, haptics)
            SukraraTheme {
                SettingsChrome {
                    val initial by produceState<Pair<WidgetOverrides, GraphConfig>?>(null) {
                        host.awaitReady()
                        value = host.widgets.settings.get(appWidgetId) to
                            host.widgets.updater
                                .current()
                                .config
                    }
                    initial?.let { (start, config) ->
                        WidgetSettingsScreen(
                            kind = kind,
                            initial = start,
                            inherited = config,
                            preview = { o -> previewOf(kind, o) },
                            describe = { o -> describeCard(this, kind, host.widgets.updater.current(), o) },
                            onCancel = { finish() },
                            onSave = { o -> save(appWidgetId, kind, o) },
                        )
                    }
                }
            }
        }
    }

    private fun result(appWidgetId: Int) = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    private fun kindOf(appWidgetId: Int): WidgetKind? {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return null
        val provider =
            AppWidgetManager
                .getInstance(this)
                .getAppWidgetInfo(appWidgetId)
                ?.provider
                ?.className ?: return null
        return when (provider) {
            ValueWidgetReceiver::class.java.name -> WidgetKind.VALUE
            GraphWidgetReceiver::class.java.name -> WidgetKind.GRAPH
            CombinedWidgetReceiver::class.java.name -> WidgetKind.COMBINED
            else -> null
        }
    }

    private suspend fun previewOf(
        kind: WidgetKind,
        overrides: WidgetOverrides,
    ) = widgetsHost.widgets.updater
        .current()
        .let { frame -> renderWidget(this, kind, PREVIEW_W_DP, PREVIEW_H_DP, frame, overrides) }

    private fun save(
        appWidgetId: Int,
        kind: WidgetKind,
        overrides: WidgetOverrides,
    ) {
        lifecycleScope.launch {
            val widgets = widgetsHost.widgets
            widgets.saveOverrides(appWidgetId, overrides)
            // the updater redraws on the settings change; this placement now, in case the screen is off
            kind.widget().update(this@WidgetConfigActivity, GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId))
            setResult(RESULT_OK, result(appWidgetId))
            finish()
        }
    }

    companion object {
        const val PREVIEW_W_DP = 320f
        const val PREVIEW_H_DP = 170f
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetSettingsScreen(
    kind: WidgetKind,
    initial: WidgetOverrides,
    inherited: GraphConfig,
    preview: suspend (WidgetOverrides) -> android.graphics.Bitmap,
    describe: suspend (WidgetOverrides) -> String,
    onCancel: () -> Unit,
    onSave: (WidgetOverrides) -> Unit,
) {
    var o by rememberSaveable(stateSaver = OverridesSaver) { mutableStateOf(initial) }
    val render by rememberUpdatedState(preview)
    val words by rememberUpdatedState(describe)
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    // what Default means for this widget: the Widgets place's values
    val hoursDefault = hoursLabel(inherited.hours(GraphPlace.WIDGETS))
    val sideDefault = inherited.side
    val ringsDefault = inherited.ringCount.toString()
    val rangeDefault = rangeLabel(inherited.ringRange)
    val sensorDefault = inherited.sensorTime(GraphPlace.WIDGETS)
    val hasGraph = kind != WidgetKind.VALUE
    val hasSlots = kind != WidgetKind.GRAPH
    // a settings page like the app's (SPA's SettingsScaffold): back = Cancel
    SettingsScaffoldPage(stringResource(R.string.widget_config_title), onCancel) {
        val bitmap by produceState<android.graphics.Bitmap?>(null, o) { value = render(o) }
        val spoken by produceState("", o) { value = words(o) }
        bitmap?.let {
            Image(
                it.asImageBitmap(),
                contentDescription = stringResource(R.string.widget_config_preview, spoken),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            16.dp,
                        ).aspectRatio(WidgetConfigActivity.PREVIEW_W_DP / WidgetConfigActivity.PREVIEW_H_DP),
            )
        }
        SettingsGroup {
            if (hasGraph) {
                PlaceRow(PlaceValue(stringResource(R.string.widget_config_hours), o.hours?.let { hoursLabel(it) }), hoursDefault, {
                    open =
                        "hours"
                })
                PlaceRow(PlaceValue(stringResource(R.string.widget_config_side), o.side?.let { onOffText(it) }), onOffText(sideDefault), {
                    open =
                        "side"
                })
            }
            if (hasSlots) {
                PlaceRow(PlaceValue(stringResource(R.string.widget_config_rings), o.rings?.toString()), ringsDefault, { open = "rings" })
                PlaceRow(PlaceValue(stringResource(R.string.widget_config_ring_range), o.ringRange?.let { rangeLabel(it) }), rangeDefault, {
                    open =
                        "range"
                })
            }
            PlaceRow(
                PlaceValue(stringResource(R.string.widget_config_sensor), o.sensorTime?.let { onOffText(it) }),
                onOffText(sensorDefault),
                { open = "sensor" },
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End),
        ) {
            TextButton(onClick = { o = WidgetOverrides() }) { Text(stringResource(R.string.widget_config_reset)) }
            TextButton(onClick = onCancel) { Text(stringResource(android.R.string.cancel)) }
            Button(onClick = { onSave(o) }) { Text(stringResource(R.string.widget_config_save)) }
        }
    }
    when (open) {
        // the one number input (schema placeHours: Default first, 3 / 6 / 12 / 24 h or Custom… 1–48 h; 0 = Default)
        "hours" -> {
            val res = LocalResources.current
            val default = defaultChoice(hoursDefault)
            NumberDialog(
                stringResource(R.string.widget_config_hours),
                NumberInput(SettingsSpec.PLACE_HOURS),
                (o.hours ?: 0).toDouble(),
                { v -> if (v == 0.0) default else v.toInt().let { h -> res.getQuantityString(R.plurals.widget_config_hours_n, h, h) } },
                { open = null },
            ) {
                o = o.copy(hours = it.toInt().takeIf { h -> h > 0 })
                open = null
            }
        }

        // a closed list (schema ringCount: 0–3, no custom value) is plain choices, as on Settings › Graph
        "rings" -> {
            OverrideDialog(
                stringResource(R.string.widget_config_rings),
                ringsDefault,
                (0..3).map { Choice(it.toString(), it) },
                o.rings,
                { open = null },
            ) {
                o = o.copy(rings = it)
                open = null
            }
        }

        "range" -> {
            OverrideDialog(
                stringResource(R.string.widget_config_ring_range),
                rangeDefault,
                TirRange.entries.map { Choice(rangeLabel(it), it) },
                o.ringRange,
                { open = null },
            ) {
                o = o.copy(ringRange = it)
                open = null
            }
        }

        "side" -> {
            OnOffOverrideDialog(stringResource(R.string.widget_config_side), sideDefault, o.side, { open = null }) {
                o = o.copy(side = it)
                open = null
            }
        }

        "sensor" -> {
            OnOffOverrideDialog(stringResource(R.string.widget_config_sensor), sensorDefault, o.sensorTime, { open = null }) {
                o = o.copy(sensorTime = it)
                open = null
            }
        }
    }
}

@Composable
private fun hoursLabel(h: Int) = pluralStringResource(R.plurals.widget_config_hours_n, h, h)

@Composable
private fun rangeLabel(r: TirRange) =
    when (r) {
        TirRange.IN_RANGE -> stringResource(R.string.widget_config_range_in)
        TirRange.TIGHT -> stringResource(R.string.widget_config_range_tight)
    }

/** Keeps the edited overrides across rotation (a list of the five fields). */
private val OverridesSaver =
    androidx.compose.runtime.saveable.Saver<WidgetOverrides, List<Any?>>(
        save = { listOf(it.hours, it.rings, it.ringRange?.name, it.side, it.sensorTime) },
        restore = { l ->
            WidgetOverrides(l[0] as Int?, l[1] as Int?, (l[2] as String?)?.let(TirRange::valueOf), l[3] as Boolean?, l[4] as Boolean?)
        },
    )
