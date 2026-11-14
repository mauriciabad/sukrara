// Sukrara — https://github.com/mauriciabad/sukrara
// Copyright (C) 2026 Maurici Abad
// SPDX-License-Identifier: GPL-3.0-or-later
package app.mauri.sukrara.feature.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mauri.sukrara.core.data.readings.TrendReadings
import app.mauri.sukrara.core.model.Clock
import app.mauri.sukrara.core.model.GlucoseUnit
import app.mauri.sukrara.core.model.Reading
import app.mauri.sukrara.core.model.TrendFrom
import app.mauri.sukrara.core.model.TrendRate
import app.mauri.sukrara.core.model.TrendSetting
import app.mauri.sukrara.core.model.storage.SettingsRepository
import app.mauri.sukrara.designsystem.motion.LocalReducedMotion
import app.mauri.sukrara.designsystem.motion.Motion
import app.mauri.sukrara.designsystem.rows.FooterText
import app.mauri.sukrara.designsystem.rows.RadioRow
import app.mauri.sukrara.designsystem.rows.SettingsGroup
import app.mauri.sukrara.graph.CardSurface
import app.mauri.sukrara.graph.CardWords
import app.mauri.sukrara.graph.GlucoseCard
import app.mauri.sukrara.graph.GlucoseLook
import app.mauri.sukrara.graph.GraphSettings
import app.mauri.sukrara.graph.TrendAngle
import app.mauri.sukrara.graph.TrendMark
import app.mauri.sukrara.graph.ValueGlyphs
import app.mauri.sukrara.graph.stats.toPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale
import kotlin.math.abs

/**
 * What one choice of the *Trend arrow* says now (glyph surface `trend-preview`): the newest reading's [look] under that
 * choice ([GlucoseCard.face]: value, struck, arrow or ring, the trend's rate and band) and where its trend came from.
 */
data class TrendPreview(
    val look: GlucoseLook?,
    val from: TrendFrom?,
)

/** The Trend arrow page: the choice in use and what each choice would show for the readings now. */
data class TrendArrowUi(
    val choice: TrendSetting,
    val previews: Map<TrendSetting, TrendPreview>,
    val unit: GlucoseUnit,
) {
    val shown: TrendPreview get() = previews.getValue(choice)
}

/**
 * The *Trend arrow* setting (`glucose.trend`) and its live preview: the hub's recent readings worked out under every
 * choice with THE trend ([TrendRate.annotate], the same function the app's one place runs), so the page shows what
 * each choice would say for the person's own readings before they pick it.
 */
class TrendArrowViewModel(
    private val settings: SettingsRepository,
    recent: Flow<List<Reading>>,
    private val clock: Clock,
) : ViewModel() {
    val ui: StateFlow<TrendArrowUi?> =
        combine(
            TrendReadings.setting(settings),
            recent,
            settings.flow(GraphSettings.GlucoseUnitSetting),
        ) { choice, readings, unit ->
            GlucoseUnit.of(unit).let { u -> TrendArrowUi(choice, TrendArrowPreview.of(readings, clock.now(), u), u) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun choose(s: TrendSetting) {
        viewModelScope.launch { settings.set(TrendReadings.TrendArrow, s.id) }
    }
}

/** The page's preview, a pure function of the readings (glyph surface `trend-preview`). */
object TrendArrowPreview {
    /**
     * What each choice shows for [readings] (oldest first) at [now]: the newest reading's face under that choice (THE
     * trend worked out as the app's one place does, here for every choice), in [unit].
     */
    fun of(
        readings: List<Reading>,
        now: Instant,
        unit: GlucoseUnit,
    ): Map<TrendSetting, TrendPreview> {
        val window = readings.filter { !it.t.isBefore(now - TrendReadings.LOOKBACK) }
        return TrendSetting.entries.associateWith { s ->
            val newest = TrendRate.annotate(window, s).lastOrNull()
            TrendPreview(
                newest?.let { GlucoseCard.face(it.toPoint(), now.epochSecond, null, CardSurface.TREND_PREVIEW, unit) },
                newest?.trend?.from,
            )
        }
    }
}

/**
 * Glucose (Ranges) › Trend arrow: the live preview (the arrow turns with a spring as the choice changes; none with
 * reduced motion), the four choices each with the arrow it would show now, and what the setting reaches. [readOnly] = a
 * follower mirroring the wearer: the choice in use, not changeable here ([note] says why and opens *Settings from*).
 */
@Composable
fun TrendArrowContent(
    ui: TrendArrowUi,
    onChoose: (TrendSetting) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    note: @Composable () -> Unit = {},
) {
    SettingsPage(stringResource(R.string.st_trend_title), onBack, modifier) {
        note()
        TrendPreviewCard(ui)
        SettingsGroup(Modifier.selectableGroup(), title = stringResource(R.string.st_trend_from)) {
            for (s in TrendSetting.entries) {
                RadioRow(
                    stringResource(s.title),
                    selected = ui.choice == s,
                    onSelect = { onChoose(s) },
                    summary = stringResource(s.summary),
                    enabled = !readOnly,
                    leading = { ArrowMark(ui.previews.getValue(s).look, 24.dp) },
                )
            }
        }
        FooterText(stringResource(R.string.st_trend_footer))
    }
}

private val TrendSetting.title: Int
    get() =
        when (this) {
            TrendSetting.Quick -> R.string.st_trend_quick
            TrendSetting.Balanced -> R.string.st_trend_balanced
            TrendSetting.Smooth -> R.string.st_trend_smooth
            TrendSetting.Sensor -> R.string.st_trend_sensor
        }

private val TrendSetting.summary: Int
    get() =
        when (this) {
            TrendSetting.Quick -> R.string.st_trend_quick_summary
            TrendSetting.Balanced -> R.string.st_trend_balanced_summary
            TrendSetting.Smooth -> R.string.st_trend_smooth_summary
            TrendSetting.Sensor -> R.string.st_trend_sensor_summary
        }

/** The name of [s] as the Ranges row shows it. */
@Composable
fun trendChoiceName(s: TrendSetting): String = stringResource(s.title)

/**
 * The preview on top: the newest value with the arrow of the choice in use, turning with a short spring when the
 * choice (or a new reading) changes it, and in words where that arrow came from (and what the sensor itself says).
 */
@Composable
private fun TrendPreviewCard(ui: TrendArrowUi) {
    val look = ui.shown.look
    val locale = Locale.getDefault()
    val words = CardWords.of(LocalContext.current.resources)
    val sensor = ui.previews.getValue(TrendSetting.Sensor).look
    val lines =
        buildList {
            val rate = look?.rate?.takeIf { look.trend == TrendMark.ARROW }
            when {
                look == null || !look.shown -> {
                    add(stringResource(R.string.st_trend_preview_no_readings))
                }

                rate == null -> {
                    val none = if (ui.choice == TrendSetting.Sensor) R.string.st_trend_preview_no_rate else R.string.st_trend_preview_none
                    add(stringResource(none))
                }

                ui.choice == TrendSetting.Sensor -> {
                    add(stringResource(R.string.st_trend_preview_sensor, signed(rate, ui.unit, locale)))
                }

                ui.shown.from == TrendFrom.Sensor -> {
                    add(stringResource(R.string.st_trend_preview_fallback, signed(rate, ui.unit, locale)))
                }

                else -> {
                    add(stringResource(R.string.st_trend_preview_readings, signed(rate, ui.unit, locale)))
                }
            }
            val sensorSays = sensor?.rate?.takeIf { sensor.trend == TrendMark.ARROW }
            if (ui.choice != TrendSetting.Sensor && sensorSays != null && ui.shown.from == TrendFrom.Readings) {
                add(stringResource(R.string.st_trend_preview_compare, TrendAngle.text(sensor.band), signed(sensorSays, ui.unit, locale)))
            }
        }
    val said =
        listOfNotNull(look?.text, look?.band?.takeIf { look.trend == TrendMark.ARROW }?.let(words::trend)) + lines
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier
                .padding(20.dp)
                .clearAndSetSemantics {
                    contentDescription = said.joinToString(", ")
                    liveRegion = LiveRegionMode.Polite
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    look?.text ?: "–",
                    style = MaterialTheme.typography.displaySmall,
                    textDecoration = if (look?.strike == true) TextDecoration.LineThrough else null,
                )
                ArrowMark(look, 40.dp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                lines.forEachIndexed { i, l ->
                    Text(
                        l,
                        style = if (i == 0) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                        color = if (i == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * The face's trend mark at [size]: the card's arrow (doubled at band ±3) turned to the trend's angle with a short
 * spring, or the "no trend" ring. Decorative: the rows and the preview say it in words.
 */
@Composable
private fun ArrowMark(
    look: GlucoseLook?,
    size: Dp,
) {
    val arrow = look?.takeIf { it.trend == TrendMark.ARROW && it.rate != null && it.band != null }
    val doubled = arrow?.band?.let(TrendAngle::isDouble) == true
    val bitmap = remember(arrow == null, doubled) { ValueGlyphs.trendIcon(arrow?.let { 0.0 }, arrow?.let { if (doubled) 3 else 0 }) }
    val reduced = LocalReducedMotion.current
    val deg by animateFloatAsState(arrow?.rate?.let(TrendAngle::deg) ?: 0f, Motion.arrowTurn(reduced), label = "trend-preview")
    Image(
        bitmap.asImageBitmap(),
        contentDescription = null,
        modifier = Modifier.size(size).graphicsLayer { rotationZ = deg },
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
    )
}

/** A rate as the preview writes it: signed, in the person's unit ("+1.1 mg/dL/min", "−0.02 mmol/L/min"), the unit never broken at a slash. */
private fun signed(
    rate: Double,
    unit: GlucoseUnit,
    locale: Locale,
): String = (if (rate < 0) "−" else "+") + unit.rateText(abs(rate), locale) + " " + unit.rateSymbol.replace("/", "/\u2060")
