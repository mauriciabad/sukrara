// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mauri.sukrara.core.data.settings.Setting
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.core.model.storage.SettingsRepository
import app.mauri.sukrara.designsystem.dialog.Choice
import app.mauri.sukrara.designsystem.dialog.ChoiceDialog
import app.mauri.sukrara.designsystem.dialog.LocalGlucoseUnit
import app.mauri.sukrara.designsystem.dialog.NumberDialog
import app.mauri.sukrara.designsystem.dialog.NumberInput
import app.mauri.sukrara.designsystem.rows.FooterText
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.designsystem.rows.ValueRow
import app.mauri.sukrara.feature.settings.graph.PreviewCard
import app.mauri.sukrara.graph.GraphSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import app.mauri.sukrara.core.designsystem.R as DsR

/** The six stored ranges the Ranges page shows (mg/dL), lowest first, and the glucose unit they are shown in. */
data class GraphPrefs(
    val bands: List<Double> = SchemaPrefs.Bands.map { it.default },
    val unit: GlucoseUnit = GlucoseUnit.MGDL,
) {
    val isDefault: Boolean get() = bands == SchemaPrefs.Bands.map { it.default }
}

object BandOrder {
    /**
     * Why [value] can't be band [index] (it must stay above the band below and under the band above, so the graph's
     * colours never cross), or null when it fits. Returns the allowed open interval (low, high) on failure.
     */
    fun conflict(
        bands: List<Double>,
        index: Int,
        value: Double,
    ): Pair<Double?, Double?>? {
        val below = bands.getOrNull(index - 1)
        val above = bands.getOrNull(index + 1)
        val ok = (below == null || value > below) && (above == null || value < above)
        return if (ok) null else below to above
    }
}

/** Reads and writes the ranges. */
class GraphPrefsViewModel(
    private val settings: SettingsRepository,
) : ViewModel() {
    /** Null until the stored values have loaded (edits are never checked against defaults). */
    val prefs: StateFlow<GraphPrefs?> =
        combine(
            combine(SchemaPrefs.Bands.map { settings.flow(it) }) { it.toList() },
            settings.flow(GraphSettings.GlucoseUnitSetting),
        ) { bands, unit -> GraphPrefs(bands, GlucoseUnit.of(unit)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun set(
        setting: Setting<Double>,
        value: Double,
    ) {
        viewModelScope.launch { settings.set(setting, value) }
    }

    /** The glucose unit every number is shown and typed in (`general.unit`; never mirrored from the wearer). */
    fun setUnit(unit: GlucoseUnit) {
        viewModelScope.launch { settings.set(GraphSettings.GlucoseUnitSetting, unit.key) }
    }

    /** Every range back to its default (the order of the defaults is valid, so writing them one by one is safe). */
    fun reset() {
        viewModelScope.launch { SchemaPrefs.Bands.forEach { settings.set(it, it.default) } }
    }
}

/**
 * Ranges: the live card, the glucose unit (mg/dL or mmol/L, every number in the app), the six bands the graph colours
 * and time in range use, Reset ranges. [readOnly] = a follower not in Custom: the bands in use, nothing to change
 * ([preview] says why and opens *Settings from*); the unit is this phone's own and stays editable.
 */
@Composable
fun RangesContent(
    prefs: GraphPrefs,
    onSet: (Setting<Double>, Double) -> Unit,
    onBack: () -> Unit,
    onReset: () -> Unit = {},
    readOnly: Boolean = false,
    onUnit: (GlucoseUnit) -> Unit = {},
    preview: @Composable () -> Unit = {},
) {
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
    var choosingUnit by rememberSaveable { mutableStateOf(false) }
    val titles =
        listOf(
            R.string.st_band_very_low,
            R.string.st_band_low,
            R.string.st_band_tight,
            R.string.st_band_high,
            R.string.st_band_very_high,
            R.string.st_band_extremely_high,
        )
    val bandIcons =
        listOf(
            DsR.drawable.ds_ic_dia_very_low,
            DsR.drawable.ds_ic_dia_low,
            DsR.drawable.ds_ic_dia_range,
            DsR.drawable.ds_ic_dia_high,
            DsR.drawable.ds_ic_dia_very_high,
            DsR.drawable.ds_ic_dia_extremely_high,
        )
    SettingsPage(stringResource(R.string.st_ranges_title), onBack) {
        PreviewCard(preview)
        SettingsGroup {
            ValueRow(stringResource(R.string.st_unit), prefs.unit.symbol, { choosingUnit = true }, icon = DsR.drawable.ds_ic_tune)
        }
        FooterText(stringResource(R.string.st_unit_footer))
        val words = spanWords()
        SettingsGroup {
            SchemaPrefs.Bands.forEachIndexed { i, setting ->
                // the number, then exactly which values it makes count ("Low: 56–69 mg/dL")
                ValueRow(
                    stringResource(titles[i]),
                    withSpans(numberLabel(SchemaPrefs.bandSpec(setting), prefs.bands[i]), words.text(RangeSpans.ofBand(prefs.bands, i))),
                    { editing = i },
                    icon = bandIcons[i],
                    readOnly = readOnly,
                )
            }
        }

        if (!prefs.isDefault && !readOnly) {
            SettingsGroup {
                ValueRow(
                    stringResource(R.string.st_ranges_reset),
                    stringResource(R.string.st_ranges_reset_summary),
                    onReset,
                    icon = DsR.drawable.ds_ic_restart_alt,
                )
            }
        }
        FooterText(stringResource(R.string.st_ranges_footer))
    }
    if (choosingUnit) {
        ChoiceDialog(
            stringResource(R.string.st_unit),
            GlucoseUnit.entries.map { Choice(it.symbol, it) },
            prefs.unit,
            { choosingUnit = false },
            onChoose = onUnit,
        )
    }
    editing?.let { i ->
        val setting = SchemaPrefs.Bands[i]
        val spec = SchemaPrefs.bandSpec(setting)
        val unit = LocalGlucoseUnit.current
        val between = stringResource(R.string.st_band_between)
        val input =
            NumberInput(
                spec,
                keep = { BandOrder.conflict(prefs.bands, i, it) == null },
                reject = { v ->
                    BandOrder.conflict(prefs.bands, i, v)?.let { (lo, hi) ->
                        between.format(spec.text(lo ?: spec.min, unit), spec.text(hi ?: spec.max, unit), spec.inUnit(unit).unit)
                    }
                },
                unit = unit,
            )
        val label = numberLabeler()
        val words = spanWords()

        fun spans(v: Double) = RangeSpans.ofBand(prefs.bands.toMutableList().also { it[i] = v }, i)
        NumberDialog(
            stringResource(titles[i]),
            input,
            prefs.bands[i],
            { label(spec, it) },
            { editing = null },
            sub = { words.text(spans(it).take(1)) },
            live = { words.text(spans(it)) },
        ) {
            onSet(setting, it)
        }
    }
}
